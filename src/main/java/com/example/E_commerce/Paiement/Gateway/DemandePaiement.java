package com.example.E_commerce.Paiement.Gateway;

import java.math.BigDecimal;

/**
 * Ce que notre application demande a un operateur.
 * Volontairement neutre : aucune notion propre a MTN ou a Orange.
 *
 * @param referenceInterne notre reference de paiement (PAY-...), que l'operateur
 *                         doit nous retourner dans son callback. C'est la cle
 *                         d'idempotence vis-a-vis de l'operateur.
 * @param montant          montant calcule par le serveur, jamais par le client
 * @param devise           XAF
 * @param numeroPayeur     numero mobile money a debiter
 * @param description      libelle affiche au client sur son telephone
 * @param urlCallback      ou l'operateur doit notifier le resultat
 */
public record DemandePaiement(
        String referenceInterne,
        BigDecimal montant,
        String devise,
        String numeroPayeur,
        String description,
        String urlCallback
) {}
