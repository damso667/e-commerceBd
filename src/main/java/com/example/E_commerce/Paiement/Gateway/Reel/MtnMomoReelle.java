package com.example.E_commerce.Paiement.Gateway.Reel;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Paiement.Config.PaiementProperties;
import com.example.E_commerce.Paiement.Gateway.DemandePaiement;
import com.example.E_commerce.Paiement.Gateway.PasserellePaiement;
import com.example.E_commerce.Paiement.Gateway.ResultatPasserelle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * MTN Mobile Money - API Collection (produit "Request to Pay").
 *
 * <p><b>ETAT : squelette non teste.</b> La structure des appels suit la
 * documentation MTN MoMo Developer, mais elle n'a pas pu etre validee contre
 * le sandbox faute de cles. Avant la mise en production :</p>
 * <ol>
 *   <li>creer un compte sur momodeveloper.mtn.com, souscrire au produit
 *       Collection et recuperer la <i>Ocp-Apim-Subscription-Key</i> ;</li>
 *   <li>creer un API User + API Key (endpoints {@code /v1_0/apiuser}) ;</li>
 *   <li>renseigner MTN_MOMO_* dans le fichier .env ;</li>
 *   <li>passer PAIEMENT_MODE=reel ;</li>
 *   <li>rejouer le scenario de bout en bout sur le sandbox et ajuster le
 *       mapping des statuts ci-dessous si MTN a fait evoluer son API.</li>
 * </ol>
 *
 * <p>Point cle d'idempotence : MTN utilise l'en-tete {@code X-Reference-Id}
 * comme identifiant de transaction. On le derive de facon <b>deterministe</b>
 * de notre propre reference, donc rejouer l'initiation ne cree jamais un
 * second debit : MTN repond 409 Conflict sur un identifiant deja vu.</p>
 */
@Component
@ConditionalOnProperty(name = "app.paiement.mode", havingValue = "reel")
public class MtnMomoReelle implements PasserellePaiement {

    private static final Logger log = LoggerFactory.getLogger(MtnMomoReelle.class);

    private static final String[] PREFIXES = {
            "67", "680", "681", "682", "683", "684", "650", "651", "652", "653", "654"
    };

    private final PaiementProperties.Mtn config;
    private final RestClient client;

    public MtnMomoReelle(PaiementProperties properties, RestClient.Builder builder) {
        this.config = properties.getMtn();
        this.client = builder.baseUrl(config.getBaseUrl()).build();
    }

    @Override
    public FournisseurPaiement fournisseur() {
        return FournisseurPaiement.MTN_MOMO;
    }

    @Override
    public boolean accepteNumero(String numero) {
        String n = normaliser(numero);
        if (n.length() != 9) {
            return false;
        }
        for (String p : PREFIXES) {
            if (n.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ResultatPasserelle initier(DemandePaiement demande) {
        String referenceMtn = referenceMtnPour(demande.referenceInterne());
        try {
            String jeton = obtenirJeton();

            client.post()
                    .uri("/collection/v1_0/requesttopay")
                    .header("X-Reference-Id", referenceMtn)          // idempotence MTN
                    .header("X-Target-Environment", config.getTargetEnvironment())
                    .header("X-Callback-Url", demande.urlCallback())
                    .header("Ocp-Apim-Subscription-Key", config.getSubscriptionKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "amount", demande.montant().toPlainString(),
                            "currency", demande.devise(),
                            "externalId", demande.referenceInterne(),
                            "payer", Map.of(
                                    "partyIdType", "MSISDN",
                                    "partyId", "237" + normaliser(demande.numeroPayeur())),
                            "payerMessage", demande.description(),
                            "payeeNote", demande.description()))
                    .retrieve()
                    .toBodilessEntity();

            return ResultatPasserelle.enCours(referenceMtn,
                    "Demande envoyee. Validez la transaction sur votre telephone.");

        } catch (Exception e) {
            log.error("MTN MoMo : echec de l'initiation de {}", demande.referenceInterne(), e);
            return ResultatPasserelle.echec(referenceMtn, "ERREUR_PASSERELLE",
                    "Operateur injoignable, reessayez dans un instant");
        }
    }

    @Override
    public ResultatPasserelle consulter(String referenceInterne, String referenceOperateur) {
        try {
            String jeton = obtenirJeton();

            Map<?, ?> reponse = client.get()
                    .uri("/collection/v1_0/requesttopay/{id}", referenceOperateur)
                    .header("X-Target-Environment", config.getTargetEnvironment())
                    .header("Ocp-Apim-Subscription-Key", config.getSubscriptionKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton)
                    .retrieve()
                    .body(Map.class);

            String statutMtn = reponse == null ? null : String.valueOf(reponse.get("status"));
            String raison = reponse == null ? null : String.valueOf(reponse.get("reason"));
            return traduire(statutMtn, raison, referenceOperateur);

        } catch (Exception e) {
            log.error("MTN MoMo : echec de la consultation de {}", referenceOperateur, e);
            return ResultatPasserelle.enCours(referenceOperateur, "Statut indisponible, nouvelle tentative plus tard");
        }
    }

    /**
     * Traduit le vocabulaire MTN vers le notre. C'est le SEUL endroit a
     * ajuster si MTN modifie ses libelles de statut.
     */
    private ResultatPasserelle traduire(String statutMtn, String raison, String referenceOperateur) {
        if (statutMtn == null) {
            return ResultatPasserelle.enCours(referenceOperateur, "Statut inconnu");
        }
        return switch (statutMtn.toUpperCase()) {
            case "SUCCESSFUL" -> ResultatPasserelle.succes(referenceOperateur, "Paiement confirme");
            case "FAILED", "REJECTED" -> ResultatPasserelle.echec(referenceOperateur,
                    raison == null ? "FAILED" : raison, "Paiement refuse par l'operateur");
            case "TIMEOUT", "EXPIRED" -> ResultatPasserelle.expire(referenceOperateur,
                    "Le client n'a pas valide dans le delai imparti");
            default -> ResultatPasserelle.enCours(referenceOperateur, "En attente de validation du client");
        };
    }

    /**
     * Jeton OAuth2 MTN (valide 1 heure).
     * TODO mise en production : mettre ce jeton en cache jusqu'a son expiration
     * plutot que d'en redemander un a chaque appel.
     */
    private String obtenirJeton() {
        String basique = Base64.getEncoder().encodeToString(
                (config.getApiUser() + ":" + config.getApiKey()).getBytes(StandardCharsets.UTF_8));

        Map<?, ?> reponse = client.post()
                .uri("/collection/token/")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basique)
                .header("Ocp-Apim-Subscription-Key", config.getSubscriptionKey())
                .retrieve()
                .body(Map.class);

        if (reponse == null || reponse.get("access_token") == null) {
            throw new IllegalStateException("MTN MoMo : jeton d'acces non obtenu");
        }
        return String.valueOf(reponse.get("access_token"));
    }

    /**
     * MTN exige un UUID. On le derive de notre reference pour rester
     * idempotent : meme reference interne = meme X-Reference-Id = pas de
     * double debit meme si l'initiation est rejouee.
     */
    private static String referenceMtnPour(String referenceInterne) {
        return UUID.nameUUIDFromBytes(referenceInterne.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String normaliser(String numero) {
        if (numero == null) {
            return "";
        }
        String n = numero.replaceAll("[^0-9]", "");
        return n.startsWith("237") && n.length() > 9 ? n.substring(3) : n;
    }
}
