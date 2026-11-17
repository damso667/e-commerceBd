package com.example.E_commerce.Paiement.Service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Taches de fond du paiement.
 *
 * <p>Aucun operateur de mobile money ne garantit la livraison de ses
 * webhooks : un callback peut se perdre, arriver en double ou en retard.
 * Ces trois taches sont le filet de securite qui rend le systeme fiable
 * malgre cela, et elles fonctionnent a l'identique en simulation et en
 * production.</p>
 *
 * <p>Les boucles sont ici, et pas dans le service : chaque element est
 * traite dans sa propre transaction ({@code REQUIRES_NEW}), ce qui exige de
 * passer par le proxy Spring, donc par un appel depuis un autre bean.</p>
 *
 * <p><b>Multi-instance :</b> ces jobs tournent sur chaque instance. Les
 * verrous de ligne evitent la corruption, mais pour eviter le travail en
 * double il faudrait un verrou distribue (ShedLock).</p>
 */
@Component
@RequiredArgsConstructor
public class OrdonnanceurPaiement {

    private static final Logger log = LoggerFactory.getLogger(OrdonnanceurPaiement.class);

    private final PaiementService paiementService;

    /** Va chercher activement le statut des paiements restes sans reponse. */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    public void reconcilier() {
        for (String reference : securise(paiementService::referencesEnVol)) {
            try {
                paiementService.reconcilierUn(reference);
            } catch (Exception e) {
                log.error("Reconciliation impossible pour {}", reference, e);
            }
        }
    }

    /** Ferme les tentatives jamais confirmees et rend le stock reserve. */
    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT60S")
    public void expirer() {
        for (String reference : securise(paiementService::referencesExpirees)) {
            try {
                paiementService.expirerUn(reference);
            } catch (Exception e) {
                log.error("Expiration impossible pour {}", reference, e);
            }
        }
    }

    /** Ramasse les commandes creees puis abandonnees sans paiement. */
    @Scheduled(fixedDelayString = "PT300S", initialDelayString = "PT120S")
    public void annulerCommandesAbandonnees() {
        for (Long idCommande : securise(paiementService::commandesOrphelines)) {
            try {
                paiementService.annulerOrpheline(idCommande);
            } catch (Exception e) {
                log.error("Annulation impossible pour la commande {}", idCommande, e);
            }
        }
    }

    /** Une panne de lecture ne doit pas tuer le job : il repassera. */
    private <T> java.util.List<T> securise(java.util.function.Supplier<java.util.List<T>> lecture) {
        try {
            return lecture.get();
        } catch (Exception e) {
            log.error("Lecture impossible pour une tache de fond", e);
            return java.util.List.of();
        }
    }
}
