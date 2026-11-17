package com.example.E_commerce.Paiement.Service;

import com.example.E_commerce.Paiement.Config.PaiementProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Signature HMAC-SHA256 des callbacks.
 *
 * <p>Un webhook est une URL publique : sans signature, n'importe qui peut
 * poster "paiement reussi" et se faire livrer gratuitement. On verifie donc
 * que le corps recu a bien ete signe avec le secret partage.</p>
 *
 * <p>MTN et Orange ont chacun leur mecanisme (MTN rappelle une URL de
 * callback que l'on re-verifie par un GET sur le statut, Orange fournit un
 * notif_token). Le jour du passage en reel, on remplace le contenu de
 * {@link #estValide} par le controle propre a l'operateur : le reste du code
 * ne bouge pas.</p>
 */
@Component
@RequiredArgsConstructor
public class VerificateurSignature {

    private static final Logger log = LoggerFactory.getLogger(VerificateurSignature.class);
    private static final String ALGO = "HmacSHA256";

    private final PaiementProperties properties;

    public String signer(String corps) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(
                    properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), ALGO));
            return HexFormat.of().formatHex(mac.doFinal(corps.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Signature HMAC impossible", e);
        }
    }

    /**
     * Comparaison a temps constant : une comparaison classique fuit, octet
     * par octet, la position de la premiere difference.
     */
    public boolean estValide(String corps, String signatureRecue) {
        if (signatureRecue == null || signatureRecue.isBlank()) {
            return false;
        }
        String attendue = signer(corps);
        boolean ok = MessageDigest.isEqual(
                attendue.getBytes(StandardCharsets.UTF_8),
                signatureRecue.trim().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            log.warn("Signature de webhook invalide");
        }
        return ok;
    }
}
