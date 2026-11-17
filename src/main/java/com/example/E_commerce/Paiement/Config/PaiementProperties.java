package com.example.E_commerce.Paiement.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Toute la configuration paiement, typee, au meme endroit.
 * Les valeurs viennent de application.properties, qui lui-meme lit le .env.
 */
@ConfigurationProperties(prefix = "app.paiement")
@Getter
@Setter
public class PaiementProperties {

    /** "simulation" ou "reel". */
    private String mode = "simulation";

    private String devise = "XAF";

    /** Au-dela de ce delai, un paiement non confirme bascule en EXPIRE. */
    private int delaiExpirationMinutes = 15;

    /** Secret HMAC-SHA256 utilise pour signer / verifier les callbacks. */
    private String webhookSecret = "dev-secret-a-changer";

    private Simulation simulation = new Simulation();
    private Mtn mtn = new Mtn();
    private Orange orange = new Orange();

    public boolean estEnSimulation() {
        return !"reel".equalsIgnoreCase(mode);
    }

    @Getter
    @Setter
    public static class Simulation {
        /** Delai avant la notification simulee, pour imiter la saisie du code USSD. */
        private long delaiReponseMs = 4000;
        /** Part de paiements qui echouent au hasard, en plus des regles deterministes. */
        private double tauxEchecAleatoire = 0.0;
    }

    @Getter
    @Setter
    public static class Mtn {
        private String baseUrl;
        private String subscriptionKey;
        private String apiUser;
        private String apiKey;
        private String targetEnvironment = "sandbox";
        private String callbackUrl;
    }

    @Getter
    @Setter
    public static class Orange {
        private String baseUrl;
        private String clientId;
        private String clientSecret;
        private String merchantKey;
        private String callbackUrl;
    }
}
