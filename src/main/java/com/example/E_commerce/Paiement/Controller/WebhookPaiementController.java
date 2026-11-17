package com.example.E_commerce.Paiement.Controller;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Paiement.Dto.NotificationWebhookDto;
import com.example.E_commerce.Paiement.Gateway.NotificationPaiement;
import com.example.E_commerce.Paiement.Service.PaiementService;
import com.example.E_commerce.Paiement.Service.VerificateurSignature;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Point d'entree des callbacks operateurs.
 *
 * <p>Cette route est publique (voir SecurityConfig) : c'est l'operateur qui
 * l'appelle, pas un utilisateur connecte. Sa protection repose donc
 * entierement sur la signature.</p>
 *
 * <p><b>Pourquoi cet endpoint existe des maintenant, en simulation :</b>
 * la passerelle simulee appelle exactement la meme methode de service que
 * ce controleur. Le chemin "notification asynchrone" est donc deja ecrit,
 * teste et fonctionnel. Le jour du passage en reel, il n'y a qu'a declarer
 * cette URL chez MTN et Orange, et a traduire leur format d'appel dans le
 * DTO pivot ci-dessous.</p>
 *
 * <p>Toujours repondre 200, meme sur un doublon ou une reference inconnue :
 * un operateur qui recoit une erreur rejoue son callback en boucle.</p>
 */
@RestController
@RequestMapping("/api/paiements/webhook")
@RequiredArgsConstructor
public class WebhookPaiementController {

    private static final Logger log = LoggerFactory.getLogger(WebhookPaiementController.class);

    private final PaiementService paiementService;
    private final VerificateurSignature verificateur;
    private final ObjectMapper mapper;

    @PostMapping("/{fournisseur}")
    public ResponseEntity<Map<String, String>> recevoir(
            @PathVariable FournisseurPaiement fournisseur,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String corpsBrut) {

        if (!verificateur.estValide(corpsBrut, signature)) {
            log.warn("Callback {} rejete : signature invalide", fournisseur);
            return ResponseEntity.status(401).body(Map.of("statut", "signature_invalide"));
        }

        try {
            NotificationWebhookDto dto = mapper.readValue(corpsBrut, NotificationWebhookDto.class);

            paiementService.traiterNotification(new NotificationPaiement(
                    fournisseur,
                    dto.getIdEvenement(),
                    dto.getReferenceInterne(),
                    dto.getReferenceOperateur(),
                    traduireStatut(dto.getStatut()),
                    dto.getMontant(),
                    dto.getCode(),
                    dto.getMessage(),
                    corpsBrut));

            return ResponseEntity.ok(Map.of("statut", "recu"));

        } catch (Exception e) {
            // On accuse reception malgre tout : l'operateur ne doit pas boucler.
            // Le job de reconciliation rattrapera le paiement concerne.
            log.error("Callback {} illisible, il sera rattrape par la reconciliation", fournisseur, e);
            return ResponseEntity.ok(Map.of("statut", "recu_avec_erreur"));
        }
    }

    /**
     * Traduction "vocabulaire operateur" vers "vocabulaire maison".
     * C'est le seul endroit a enrichir par operateur lors du passage en reel.
     */
    private StatutPaiement traduireStatut(String brut) {
        if (brut == null) {
            return StatutPaiement.EN_COURS;
        }
        return switch (brut.toUpperCase()) {
            case "SUCCESSFUL", "SUCCESS", "PAYE", "PAID" -> StatutPaiement.PAYE;
            case "FAILED", "REJECTED", "CANCELLED", "ECHOUE" -> StatutPaiement.ECHOUE;
            case "TIMEOUT", "EXPIRED", "EXPIRE" -> StatutPaiement.EXPIRE;
            default -> StatutPaiement.EN_COURS;
        };
    }
}
