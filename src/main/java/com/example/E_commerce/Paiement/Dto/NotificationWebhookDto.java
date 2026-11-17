package com.example.E_commerce.Paiement.Dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Format du callback attendu par notre webhook.
 *
 * <p>Les vrais operateurs n'envoient pas exactement ce format. Le jour du
 * passage en reel, on garde ce DTO comme format pivot et on ajoute une
 * petite traduction par operateur dans {@code WebhookPaiementController} :
 * le service metier, lui, ne change pas.</p>
 */
@Data
public class NotificationWebhookDto {
    /** Identifiant unique de l'evenement cote operateur : cle de deduplication. */
    private String idEvenement;
    /** Notre reference PAY-... */
    private String referenceInterne;
    /** Identifiant de transaction cote operateur. */
    private String referenceOperateur;
    /** SUCCESSFUL / FAILED / TIMEOUT / PENDING */
    private String statut;
    private BigDecimal montant;
    private String code;
    private String message;
}
