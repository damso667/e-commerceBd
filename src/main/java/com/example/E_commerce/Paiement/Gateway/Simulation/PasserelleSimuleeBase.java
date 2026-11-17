package com.example.E_commerce.Paiement.Gateway.Simulation;

import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Paiement.Config.PaiementProperties;
import com.example.E_commerce.Paiement.Gateway.DemandePaiement;
import com.example.E_commerce.Paiement.Gateway.NotificationPaiement;
import com.example.E_commerce.Paiement.Gateway.PasserellePaiement;
import com.example.E_commerce.Paiement.Gateway.RecepteurNotification;
import com.example.E_commerce.Paiement.Gateway.ResultatPasserelle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Socle commun des passerelles simulees.
 *
 * <p>Elle imite le comportement d'un vrai operateur mobile money :</p>
 * <ol>
 *   <li>{@code initier} repond immediatement EN_COURS avec une reference
 *       operateur, comme le fait un "request to pay" reel ;</li>
 *   <li>quelques secondes plus tard une notification asynchrone arrive sur
 *       {@link RecepteurNotification}, exactement comme un vrai webhook ;</li>
 *   <li>{@code consulter} permet le polling et la reconciliation.</li>
 * </ol>
 *
 * <p>Le resultat est <b>deterministe</b>, pilote par le dernier chiffre du
 * numero, pour que les demos et les tests soient reproductibles :</p>
 *
 * <pre>
 *   ...0  -&gt; ECHOUE  (FONDS_INSUFFISANTS)
 *   ...1  -&gt; ECHOUE  (REFUSE_PAR_UTILISATEUR)
 *   ...2  -&gt; EXPIRE  (le client ne valide jamais)
 *   ...3  -&gt; refus immediat a l'initiation (COMPTE_INTROUVABLE)
 *   autre -&gt; PAYE
 * </pre>
 *
 * <p>Aucun appel reseau n'est effectue.</p>
 */
public abstract class PasserelleSimuleeBase implements PasserellePaiement {

    private static final Logger log = LoggerFactory.getLogger(PasserelleSimuleeBase.class);

    protected final PaiementProperties properties;
    private final TaskScheduler ordonnanceur;

    /** ObjectProvider : resolution paresseuse, evite le cycle passerelle / service. */
    private final ObjectProvider<RecepteurNotification> recepteur;

    /** Memoire de la simulation : reference operateur vers issue decidee. */
    private final Map<String, IssueSimulee> transactions = new ConcurrentHashMap<>();

    protected PasserelleSimuleeBase(PaiementProperties properties,
                                    TaskScheduler ordonnanceur,
                                    ObjectProvider<RecepteurNotification> recepteur) {
        this.properties = properties;
        this.ordonnanceur = ordonnanceur;
        this.recepteur = recepteur;
    }

    /** Prefixes de numeros servis par l'operateur (format camerounais, 9 chiffres). */
    protected abstract String[] prefixesSupportes();

    @Override
    public boolean accepteNumero(String numero) {
        String normalise = normaliser(numero);
        if (normalise.length() != 9) {
            return false;
        }
        for (String prefixe : prefixesSupportes()) {
            if (normalise.startsWith(prefixe)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ResultatPasserelle initier(DemandePaiement demande) {

        // Idempotence cote operateur : meme reference interne = meme transaction.
        IssueSimulee dejaConnue = transactions.values().stream()
                .filter(t -> t.referenceInterne().equals(demande.referenceInterne()))
                .findFirst()
                .orElse(null);
        if (dejaConnue != null) {
            log.info("[SIMULATION {}] initiation rejouee pour {} : on renvoie la transaction {}",
                    fournisseur(), demande.referenceInterne(), dejaConnue.referenceOperateur());
            return ResultatPasserelle.enCours(dejaConnue.referenceOperateur(),
                    "Demande deja en cours chez l'operateur");
        }

        String referenceOperateur = fournisseur().name().toLowerCase() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        StatutPaiement issue = deciderIssue(demande.numeroPayeur());

        // Refus synchrone : l'operateur refuse avant meme d'appeler le client.
        if (issue == null) {
            log.info("[SIMULATION {}] numero {} inconnu, refus immediat",
                    fournisseur(), masquer(demande.numeroPayeur()));
            return ResultatPasserelle.echec(referenceOperateur, "COMPTE_INTROUVABLE",
                    "Aucun compte mobile money actif pour ce numero");
        }

        IssueSimulee transaction = new IssueSimulee(
                demande.referenceInterne(), referenceOperateur, issue,
                demande.montant(), Instant.now());
        transactions.put(referenceOperateur, transaction);

        log.info("[SIMULATION {}] demande {} acceptee (operateur {}), issue prevue {} dans {} ms",
                fournisseur(), demande.referenceInterne(), referenceOperateur, issue,
                properties.getSimulation().getDelaiReponseMs());

        planifierNotification(transaction);

        return ResultatPasserelle.enCours(referenceOperateur,
                "Demande envoyee. Validez la transaction sur votre telephone.");
    }

    @Override
    public ResultatPasserelle consulter(String referenceInterne, String referenceOperateur) {
        IssueSimulee transaction = transactions.get(referenceOperateur);
        if (transaction == null) {
            return ResultatPasserelle.echec(referenceOperateur, "TRANSACTION_INCONNUE",
                    "Transaction inconnue de l'operateur");
        }
        boolean delaiEcoule = Instant.now().isAfter(
                transaction.initiee().plusMillis(properties.getSimulation().getDelaiReponseMs()));
        if (!delaiEcoule) {
            return ResultatPasserelle.enCours(referenceOperateur, "En attente de validation du client");
        }
        return versResultat(transaction);
    }

    // ------------------------------------------------------------------
    // Interne
    // ------------------------------------------------------------------

    /**
     * @return le statut final prevu, ou {@code null} pour un refus synchrone.
     */
    private StatutPaiement deciderIssue(String numero) {
        String normalise = normaliser(numero);
        char dernier = normalise.charAt(normalise.length() - 1);

        StatutPaiement deterministe;
        switch (dernier) {
            case '0':
            case '1':
                deterministe = StatutPaiement.ECHOUE;
                break;
            case '2':
                deterministe = StatutPaiement.EXPIRE;
                break;
            case '3':
                return null;
            default:
                deterministe = StatutPaiement.PAYE;
        }
        if (deterministe != StatutPaiement.PAYE) {
            return deterministe;
        }
        double taux = properties.getSimulation().getTauxEchecAleatoire();
        if (taux > 0 && ThreadLocalRandom.current().nextDouble() < taux) {
            return StatutPaiement.ECHOUE;
        }
        return StatutPaiement.PAYE;
    }

    private void planifierNotification(IssueSimulee transaction) {
        // Un operateur ne notifie pas un timeout : le paiement expire tout seul
        // via le job de reconciliation. On reproduit ce silence.
        if (transaction.issue() == StatutPaiement.EXPIRE) {
            return;
        }
        Instant quand = Instant.now().plusMillis(properties.getSimulation().getDelaiReponseMs());
        ordonnanceur.schedule(() -> {
            try {
                ResultatPasserelle resultat = versResultat(transaction);
                NotificationPaiement notification = new NotificationPaiement(
                        fournisseur(),
                        // Id d'evenement stable : rejouer ce callback est sans effet.
                        "evt-" + transaction.referenceOperateur(),
                        transaction.referenceInterne(),
                        transaction.referenceOperateur(),
                        resultat.statut(),
                        transaction.montant(),
                        resultat.codeFournisseur(),
                        resultat.message(),
                        "{\"simulation\":true,\"reference\":\"" + transaction.referenceInterne() + "\"}"
                );
                log.info("[SIMULATION {}] notification differee : {} -> {}",
                        fournisseur(), transaction.referenceInterne(), resultat.statut());
                recepteur.getObject().traiterNotification(notification);
            } catch (Exception e) {
                log.error("[SIMULATION {}] echec de la notification simulee", fournisseur(), e);
            }
        }, quand);
    }

    private ResultatPasserelle versResultat(IssueSimulee t) {
        if (t.issue() == StatutPaiement.PAYE) {
            return ResultatPasserelle.succes(t.referenceOperateur(), "Paiement confirme par le client");
        }
        if (t.issue() == StatutPaiement.EXPIRE) {
            return ResultatPasserelle.expire(t.referenceOperateur(),
                    "Le client n'a pas valide dans le delai imparti");
        }
        String code = codeEchecPour(t.referenceInterne());
        return ResultatPasserelle.echec(t.referenceOperateur(), code, messagePour(code));
    }

    private String codeEchecPour(String referenceInterne) {
        return Math.abs(referenceInterne.hashCode()) % 2 == 0
                ? "FONDS_INSUFFISANTS" : "REFUSE_PAR_UTILISATEUR";
    }

    private String messagePour(String code) {
        return "FONDS_INSUFFISANTS".equals(code)
                ? "Solde insuffisant sur le compte mobile money"
                : "Le client a refuse la transaction";
    }

    /** Ramene un numero a 9 chiffres (retire l'indicatif 237 et la ponctuation). */
    protected static String normaliser(String numero) {
        if (numero == null) {
            return "";
        }
        String nettoye = numero.replaceAll("[^0-9]", "");
        if (nettoye.startsWith("237") && nettoye.length() > 9) {
            nettoye = nettoye.substring(3);
        }
        return nettoye;
    }

    /** Ne jamais journaliser un numero complet. */
    protected static String masquer(String numero) {
        String n = normaliser(numero);
        return n.length() < 4 ? "***" : "*****" + n.substring(n.length() - 4);
    }

    /** Etat interne d'une transaction simulee. */
    private record IssueSimulee(String referenceInterne,
                                String referenceOperateur,
                                StatutPaiement issue,
                                BigDecimal montant,
                                Instant initiee) {
    }
}
