package com.example.E_commerce.Paiement.Gateway;

/**
 * Point d'entree unique des notifications de paiement.
 * Implemente par le service metier ; appele aussi bien par le controleur
 * de webhook que par la passerelle simulee.
 */
public interface RecepteurNotification {
    void traiterNotification(NotificationPaiement notification);
}
