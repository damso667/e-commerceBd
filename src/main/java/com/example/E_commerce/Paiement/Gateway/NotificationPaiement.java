package com.example.E_commerce.Paiement.Gateway;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutPaiement;

import java.math.BigDecimal;

/**
 * Notification entrante, qu'elle vienne d'un vrai webhook operateur ou de
 * la passerelle simulee. Les deux chemins aboutissent a la meme methode
 * de service : le code de traitement est donc deja valide le jour ou on
 * branche les vraies API.
 *
 * @param idEvenement identifiant unique de la notification cote operateur.
 *                    Sert a ignorer les doublons.
 */
public record NotificationPaiement(
        FournisseurPaiement fournisseur,
        String idEvenement,
        String referenceInterne,
        String referenceOperateur,
        StatutPaiement statut,
        BigDecimal montant,
        String codeFournisseur,
        String message,
        String chargeBrute
) {}
