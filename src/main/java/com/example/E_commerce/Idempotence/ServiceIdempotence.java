package com.example.E_commerce.Idempotence;

import com.example.E_commerce.Exception.ConflitIdempotenceException;
import com.example.E_commerce.Models.CleIdempotence;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Rend une operation rejouable sans effet de bord, a la maniere de Stripe.
 *
 * <p>Le client envoie l'en-tete {@code Idempotency-Key: <uuid>}. Trois cas :</p>
 * <ol>
 *   <li><b>Cle inconnue</b> : on la reserve, on execute, on memorise la reponse.</li>
 *   <li><b>Cle connue, meme corps de requete</b> : on renvoie la reponse memorisee
 *       sans rien re-executer. Le client peut donc rejouer sa requete autant de
 *       fois qu'il veut (reseau coupe, bouton clique deux fois, retry automatique)
 *       sans declencher un second paiement.</li>
 *   <li><b>Cle connue, corps different</b> : 409. La cle a deja servi pour une
 *       autre operation, la reutiliser masquerait un bug cote client.</li>
 * </ol>
 *
 * <p>La cle est cloisonnee par utilisateur et par endpoint : deux clients ne
 * peuvent pas se voler une reponse en devinant une cle.</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceIdempotence {

    private static final Logger log = LoggerFactory.getLogger(ServiceIdempotence.class);

    private final DepotIdempotence depot;
    private final ObjectMapper mapper;

    /**
     * @param cle          valeur de l'en-tete Idempotency-Key (peut etre null)
     * @param endpoint     "POST /api/user/commandes"
     * @param idUtilisateur proprietaire de la cle
     * @param corpsRequete corps de la requete, sert a detecter une reutilisation abusive
     * @param type         type de la reponse, pour la deserialiser au rejeu
     * @param action       le traitement metier a executer une seule fois
     */
    public <T> ResultatIdempotent<T> executer(String cle,
                                              String endpoint,
                                              Long idUtilisateur,
                                              Object corpsRequete,
                                              Class<T> type,
                                              Supplier<T> action) {

        // Sans cle fournie, on execute normalement. L'idempotence reste assuree
        // au niveau metier (un seul paiement actif par commande).
        if (cle == null || cle.isBlank()) {
            return new ResultatIdempotent<>(action.get(), false);
        }

        String empreinte = empreinteDe(corpsRequete);
        Optional<CleIdempotence> existante =
                depot.reserver(cle, endpoint, idUtilisateur, empreinte);

        if (existante.isPresent()) {
            CleIdempotence enregistrement = existante.get();

            if (!enregistrement.getIdUtilisateur().equals(idUtilisateur)) {
                throw new ConflitIdempotenceException(
                        "Cle d'idempotence deja utilisee par un autre compte");
            }
            if (!enregistrement.getEmpreinteRequete().equals(empreinte)) {
                throw new ConflitIdempotenceException(
                        "Cette cle d'idempotence a deja ete utilisee avec un contenu different. "
                        + "Utilisez une nouvelle cle.");
            }
            if (!enregistrement.estTerminee()) {
                throw new ConflitIdempotenceException(
                        "Une requete identique est en cours de traitement. Reessayez dans un instant.");
            }
            log.info("Rejeu idempotent de {} (cle {})", endpoint, cle);
            return new ResultatIdempotent<>(deserialiser(enregistrement.getCorpsReponse(), type), true);
        }

        try {
            T reponse = action.get();
            depot.enregistrerReponse(cle, endpoint, 200, serialiser(reponse));
            return new ResultatIdempotent<>(reponse, false);
        } catch (RuntimeException echec) {
            // Un echec ne doit pas bruler la cle : le client doit pouvoir rejouer.
            depot.liberer(cle, endpoint);
            throw echec;
        }
    }

    /** Nettoyage quotidien des cles expirees. */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgerCles() {
        depot.purger();
    }

    // ------------------------------------------------------------------

    private String empreinteDe(Object corps) {
        try {
            byte[] octets = mapper.writeValueAsBytes(corps == null ? "" : corps);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer l'empreinte de la requete", e);
        }
    }

    private String serialiser(Object valeur) {
        try {
            return mapper.writeValueAsString(valeur);
        } catch (Exception e) {
            throw new IllegalStateException("Reponse non serialisable", e);
        }
    }

    private <T> T deserialiser(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Reponse memorisee illisible", e);
        }
    }

    /**
     * @param valeur reponse
     * @param rejeu  true si elle vient du cache d'idempotence (le controleur
     *               ajoute alors l'en-tete {@code Idempotent-Replay: true})
     */
    public record ResultatIdempotent<T>(T valeur, boolean rejeu) {
    }
}
