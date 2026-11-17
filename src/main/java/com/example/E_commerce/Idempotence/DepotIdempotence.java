package com.example.E_commerce.Idempotence;

import com.example.E_commerce.Models.CleIdempotence;
import com.example.E_commerce.Repository.CleIdempotenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Acces base pour l'idempotence, isole dans son propre bean.
 *
 * <p>Chaque methode est en {@code REQUIRES_NEW} : la reservation de la cle
 * doit etre <b>committee immediatement</b>, avant meme que le traitement
 * metier commence. C'est ce qui permet a une requete concurrente portant la
 * meme cle de voir la reservation et de s'arreter, au lieu de lancer un
 * second paiement.</p>
 *
 * <p>Ces methodes sont dans une classe separee et non dans le service :
 * un appel interne (this.methode()) ne passe pas par le proxy Spring et
 * {@code REQUIRES_NEW} serait silencieusement ignore.</p>
 */
@Component
@RequiredArgsConstructor
public class DepotIdempotence {

    private static final Duration DUREE_DE_VIE = Duration.ofHours(24);

    private final CleIdempotenceRepository repository;

    /**
     * Tente de reserver la cle.
     *
     * @return {@code Optional.empty()} si la reservation a reussi (donc
     *         premiere execution), ou l'enregistrement existant sinon.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CleIdempotence> reserver(String cle, String endpoint,
                                             Long idUtilisateur, String empreinte) {
        Optional<CleIdempotence> existante = repository.findByCleAndEndpoint(cle, endpoint);
        if (existante.isPresent()) {
            return existante;
        }
        CleIdempotence nouvelle = new CleIdempotence();
        nouvelle.setCle(cle);
        nouvelle.setEndpoint(endpoint);
        nouvelle.setIdUtilisateur(idUtilisateur);
        nouvelle.setEmpreinteRequete(empreinte);
        nouvelle.setDateCreation(Instant.now());
        nouvelle.setDateExpiration(Instant.now().plus(DUREE_DE_VIE));
        try {
            repository.saveAndFlush(nouvelle);
            return Optional.empty();
        } catch (DataIntegrityViolationException course) {
            // Deux requetes identiques exactement en meme temps : la contrainte
            // unique a tranche, l'autre a gagne. On relit sa reservation.
            return repository.findByCleAndEndpoint(cle, endpoint);
        }
    }

    /** Memorise la reponse pour pouvoir la rejouer telle quelle. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerReponse(String cle, String endpoint, int statutHttp, String corps) {
        repository.findByCleAndEndpoint(cle, endpoint).ifPresent(enregistrement -> {
            enregistrement.setStatutHttp(statutHttp);
            enregistrement.setCorpsReponse(corps);
            repository.save(enregistrement);
        });
    }

    /**
     * Libere la cle quand le traitement a echoue, pour que le client puisse
     * rejouer sa requete avec la meme cle.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void liberer(String cle, String endpoint) {
        repository.findByCleAndEndpoint(cle, endpoint)
                .filter(e -> !e.estTerminee())
                .ifPresent(repository::delete);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purger() {
        repository.deleteByDateExpirationBefore(Instant.now());
    }
}
