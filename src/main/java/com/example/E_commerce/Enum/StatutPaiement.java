package com.example.E_commerce.Enum;

import java.util.Set;

/**
 * Cycle de vie d'un paiement.
 *
 * <p>Les transitions autorisees sont declarees ici une fois pour toutes :
 * aucun service ne doit changer un statut sans passer par
 * {@link #peutAllerVers(StatutPaiement)}. C'est ce qui garantit qu'un
 * paiement deja PAYE ne peut jamais redevenir ECHOUE parce qu'un webhook
 * est arrive en retard ou en double.</p>
 */
public enum StatutPaiement {

    /** Paiement enregistre chez nous, pas encore transmis a l'operateur. */
    EN_ATTENTE,

    /** Demande transmise a l'operateur, on attend la validation du client (code USSD). */
    EN_COURS,

    /** Succes. Etat terminal. */
    PAYE,

    /** Refus de l'operateur ou du client (solde insuffisant, annulation...). Etat terminal. */
    ECHOUE,

    /** Le client n'a pas valide dans le delai imparti. Etat terminal. */
    EXPIRE,

    /** Annule par le marchand avant validation. Etat terminal. */
    ANNULE,

    /** Rembourse apres un paiement reussi. Etat terminal. */
    REMBOURSE;

    private static final Set<StatutPaiement> TERMINAUX =
            Set.of(PAYE, ECHOUE, EXPIRE, ANNULE, REMBOURSE);

    /** Un statut terminal ne change plus jamais (sauf PAYE -> REMBOURSE). */
    public boolean estTerminal() {
        return TERMINAUX.contains(this);
    }

    public boolean estSucces() {
        return this == PAYE;
    }

    /**
     * @return true si la transition this -> cible est legitime.
     */
    public boolean peutAllerVers(StatutPaiement cible) {
        if (cible == null || cible == this) {
            return false;
        }
        return switch (this) {
            case EN_ATTENTE -> cible == EN_COURS || cible == PAYE || cible == ECHOUE
                            || cible == EXPIRE || cible == ANNULE;
            case EN_COURS   -> cible == PAYE || cible == ECHOUE || cible == EXPIRE || cible == ANNULE;
            case PAYE       -> cible == REMBOURSE;
            default         -> false;
        };
    }
}
