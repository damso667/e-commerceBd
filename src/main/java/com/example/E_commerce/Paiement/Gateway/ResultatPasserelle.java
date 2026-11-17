package com.example.E_commerce.Paiement.Gateway;

import com.example.E_commerce.Enum.StatutPaiement;

/**
 * Reponse normalisee d'une passerelle, quel que soit l'operateur.
 * C'est cette traduction "format operateur -> format maison" qui permet
 * de brancher une vraie API sans toucher au service metier.
 *
 * @param statut             statut traduit dans notre vocabulaire
 * @param referenceOperateur identifiant de la transaction chez l'operateur
 * @param codeFournisseur    code brut de l'operateur (pour les logs / le support)
 * @param message            message lisible
 */
public record ResultatPasserelle(
        StatutPaiement statut,
        String referenceOperateur,
        String codeFournisseur,
        String message
) {
    public static ResultatPasserelle enCours(String referenceOperateur, String message) {
        return new ResultatPasserelle(StatutPaiement.EN_COURS, referenceOperateur, "PENDING", message);
    }

    public static ResultatPasserelle succes(String referenceOperateur, String message) {
        return new ResultatPasserelle(StatutPaiement.PAYE, referenceOperateur, "SUCCESSFUL", message);
    }

    public static ResultatPasserelle echec(String referenceOperateur, String code, String message) {
        return new ResultatPasserelle(StatutPaiement.ECHOUE, referenceOperateur, code, message);
    }

    public static ResultatPasserelle expire(String referenceOperateur, String message) {
        return new ResultatPasserelle(StatutPaiement.EXPIRE, referenceOperateur, "TIMEOUT", message);
    }
}
