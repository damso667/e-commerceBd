package com.example.E_commerce.Paiement.Gateway.Reel;

import com.example.E_commerce.Enum.FournisseurPaiement;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Orange Money - API Web Payment.
 *
 * <p><b>ETAT : squelette non teste.</b> La structure suit la documentation
 * Orange Developer (produit Orange Money Web Payment), mais elle n'a pas pu
 * etre validee faute de cles marchand. Avant la mise en production :</p>
 * <ol>
 *   <li>creer une application sur developer.orange.com, souscrire a
 *       Orange Money Web Payment, recuperer client_id / client_secret ;</li>
 *   <li>obtenir la <i>merchant key</i> aupres d'Orange Cameroun ;</li>
 *   <li>renseigner ORANGE_MONEY_* dans le .env ;</li>
 *   <li>passer PAIEMENT_MODE=reel ;</li>
 *   <li>valider le scenario complet en environnement "dev" d'Orange puis
 *       remplacer le segment {@code /dev/} de l'URL par {@code /prod/}.</li>
 * </ol>
 *
 * <p>Particularite Orange : le paiement se fait sur une page web hebergee par
 * Orange. {@code initier} renvoie donc une URL de redirection, transportee
 * ici dans le message du resultat. Le front doit y rediriger le client.</p>
 */
@Component
@ConditionalOnProperty(name = "app.paiement.mode", havingValue = "reel")
public class OrangeMoneyReelle implements PasserellePaiement {

    private static final Logger log = LoggerFactory.getLogger(OrangeMoneyReelle.class);

    private static final String[] PREFIXES = {
            "69", "655", "656", "657", "658", "659", "685", "686", "687", "688", "689"
    };

    /** TODO production : remplacer "dev" par "prod". */
    private static final String ESPACE = "dev";

    private final PaiementProperties.Orange config;
    private final RestClient client;

    /** pay_token renvoye par Orange, indispensable pour consulter le statut. */
    private final Map<String, String> jetonsParReference = new HashMap<>();

    public OrangeMoneyReelle(PaiementProperties properties, RestClient.Builder builder) {
        this.config = properties.getOrange();
        this.client = builder.baseUrl(config.getBaseUrl()).build();
    }

    @Override
    public FournisseurPaiement fournisseur() {
        return FournisseurPaiement.ORANGE_MONEY;
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
        try {
            String jeton = obtenirJeton();

            Map<?, ?> reponse = client.post()
                    .uri("/orange-money-webpay/{espace}/v1/webpayment", ESPACE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "merchant_key", config.getMerchantKey(),
                            "currency", demande.devise(),
                            // order_id = notre reference : c'est elle qui assure
                            // l'idempotence, Orange refuse un order_id deja utilise.
                            "order_id", demande.referenceInterne(),
                            "amount", demande.montant().intValue(),
                            "return_url", demande.urlCallback(),
                            "cancel_url", demande.urlCallback(),
                            "notif_url", demande.urlCallback(),
                            "lang", "fr",
                            "reference", demande.description()))
                    .retrieve()
                    .body(Map.class);

            if (reponse == null || reponse.get("pay_token") == null) {
                return ResultatPasserelle.echec(null, "REPONSE_INVALIDE",
                        "Orange Money n'a pas renvoye de jeton de paiement");
            }

            String payToken = String.valueOf(reponse.get("pay_token"));
            String urlPaiement = String.valueOf(reponse.get("payment_url"));
            jetonsParReference.put(demande.referenceInterne(), payToken);

            return ResultatPasserelle.enCours(payToken,
                    "Redirigez le client vers : " + urlPaiement);

        } catch (Exception e) {
            log.error("Orange Money : echec de l'initiation de {}", demande.referenceInterne(), e);
            return ResultatPasserelle.echec(null, "ERREUR_PASSERELLE",
                    "Operateur injoignable, reessayez dans un instant");
        }
    }

    @Override
    public ResultatPasserelle consulter(String referenceInterne, String referenceOperateur) {
        try {
            String jeton = obtenirJeton();
            String payToken = referenceOperateur != null
                    ? referenceOperateur
                    : jetonsParReference.get(referenceInterne);

            Map<?, ?> reponse = client.post()
                    .uri("/orange-money-webpay/{espace}/v1/transactionstatus", ESPACE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "order_id", referenceInterne,
                            "pay_token", payToken == null ? "" : payToken))
                    .retrieve()
                    .body(Map.class);

            String statut = reponse == null ? null : String.valueOf(reponse.get("status"));
            return traduire(statut, payToken);

        } catch (Exception e) {
            log.error("Orange Money : echec de la consultation de {}", referenceInterne, e);
            return ResultatPasserelle.enCours(referenceOperateur,
                    "Statut indisponible, nouvelle tentative plus tard");
        }
    }

    /**
     * Traduit le vocabulaire Orange vers le notre. Seul endroit a ajuster si
     * Orange modifie ses libelles.
     */
    private ResultatPasserelle traduire(String statutOrange, String payToken) {
        if (statutOrange == null) {
            return ResultatPasserelle.enCours(payToken, "Statut inconnu");
        }
        return switch (statutOrange.toUpperCase()) {
            case "SUCCESS", "SUCCESSFULL", "SUCCESSFUL" ->
                    ResultatPasserelle.succes(payToken, "Paiement confirme");
            case "FAILED", "CANCELLED", "REJECTED" ->
                    ResultatPasserelle.echec(payToken, statutOrange, "Paiement refuse ou annule");
            case "EXPIRED" ->
                    ResultatPasserelle.expire(payToken, "Session de paiement expiree");
            default -> ResultatPasserelle.enCours(payToken, "Paiement en cours cote Orange");
        };
    }

    /**
     * Jeton OAuth2 Orange.
     * TODO mise en production : mettre en cache jusqu'a expiration.
     */
    private String obtenirJeton() {
        String basique = Base64.getEncoder().encodeToString(
                (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        Map<?, ?> reponse = client.post()
                .uri("/oauth/v3/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basique)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(Map.class);

        if (reponse == null || reponse.get("access_token") == null) {
            throw new IllegalStateException("Orange Money : jeton d'acces non obtenu");
        }
        return String.valueOf(reponse.get("access_token"));
    }

    private static String normaliser(String numero) {
        if (numero == null) {
            return "";
        }
        String n = numero.replaceAll("[^0-9]", "");
        return n.startsWith("237") && n.length() > 9 ? n.substring(3) : n;
    }
}
