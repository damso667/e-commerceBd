package com.example.E_commerce.Paiement.Gateway;

import com.example.E_commerce.Enum.FournisseurPaiement;

/**
 * Contrat unique entre le service metier et un operateur de mobile money.
 *
 * <p>Le service de paiement ne connait QUE cette interface. Passer de la
 * simulation au reel revient donc a fournir une autre implementation :
 * aucune ligne du service metier, des controleurs ou des entites ne bouge.</p>
 */
public interface PasserellePaiement {

    /** Operateur couvert par cette implementation. */
    FournisseurPaiement fournisseur();

    /**
     * Demande le debit. Le resultat est presque toujours {@code EN_COURS} :
     * en mobile money le client doit valider sur son telephone, la reponse
     * definitive arrive ensuite par callback ou par consultation.
     *
     * <p>Doit etre idempotente du point de vue de l'operateur : appeler deux
     * fois avec la meme {@code referenceInterne} ne doit pas debiter deux fois.</p>
     */
    ResultatPasserelle initier(DemandePaiement demande);

    /**
     * Consultation active du statut (polling / reconciliation), utilisee quand
     * le callback n'est pas arrive.
     */
    ResultatPasserelle consulter(String referenceInterne, String referenceOperateur);

    /**
     * Verifie qu'un numero est bien servi par cet operateur.
     * Evite d'envoyer un numero Orange a MTN.
     */
    boolean accepteNumero(String numero);
}
