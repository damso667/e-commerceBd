package com.example.E_commerce.Enum;

public enum StatutCommande {
    /** Commande creee, stock reserve, en attente du reglement. */
    EN_ATTENTE,
    /** Paiement confirme. */
    PAYEE,
    /** Preparation / expedition en cours. */
    EN_COURS,
    LIVREE,
    /** Paiement echoue, expire, ou annulation : le stock a ete rendu. */
    ANNULEE
}
