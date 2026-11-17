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

    /**
     * Demande le remboursement d'un paiement reussi.
     *
     * <p>Par defaut : <b>non disponible</b>. Chez MTN comme chez Orange, un
     * remboursement passe par un produit distinct de l'encaissement
     * (Disbursement chez MTN, transfert marchand chez Orange) qui demande
     * une habilitation separee. Beaucoup de marchands camerounais le font
     * donc a la main.</p>
     *
     * <p>Le marchand ne doit pas etre bloque pour autant : le service
     * enregistre le remboursement dans tous les cas, et cette valeur de
     * retour sert uniquement a dire a l'administrateur s'il doit encore
     * effectuer le transfert lui-meme.</p>
     *
     * @return true si l'operateur a execute le remboursement automatiquement
     */
    default boolean rembourser(String referenceInterne, String referenceOperateur,
                               java.math.BigDecimal montant) {
        return false;
    }
}
