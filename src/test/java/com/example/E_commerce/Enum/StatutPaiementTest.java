package com.example.E_commerce.Enum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La machine a etats du paiement est la garantie centrale du module :
 * c'est elle qui empeche un callback en retard de defaire un paiement
 * reussi. Elle merite donc d'etre testee sans base ni serveur.
 */
class StatutPaiementTest {

    @Test
    @DisplayName("un paiement reussi ne peut plus echouer, meme si un callback arrive en retard")
    void unPaiementReussiNeRedevientPasEnEchec() {
        assertFalse(StatutPaiement.PAYE.peutAllerVers(StatutPaiement.ECHOUE));
        assertFalse(StatutPaiement.PAYE.peutAllerVers(StatutPaiement.EXPIRE));
        assertFalse(StatutPaiement.PAYE.peutAllerVers(StatutPaiement.EN_COURS));
        assertFalse(StatutPaiement.PAYE.peutAllerVers(StatutPaiement.ANNULE));
    }

    @Test
    @DisplayName("seul le remboursement fait suite a un paiement reussi")
    void seulLeRemboursementSuitUnSucces() {
        assertTrue(StatutPaiement.PAYE.peutAllerVers(StatutPaiement.REMBOURSE));
    }

    @Test
    @DisplayName("un echec est definitif : pas de resurrection en PAYE")
    void unEchecEstDefinitif() {
        for (StatutPaiement terminal : new StatutPaiement[]{
                StatutPaiement.ECHOUE, StatutPaiement.EXPIRE,
                StatutPaiement.ANNULE, StatutPaiement.REMBOURSE}) {
            for (StatutPaiement cible : StatutPaiement.values()) {
                assertFalse(terminal.peutAllerVers(cible),
                        terminal + " ne doit pas pouvoir aller vers " + cible);
            }
        }
    }

    @Test
    @DisplayName("le parcours nominal est autorise")
    void parcoursNominal() {
        assertTrue(StatutPaiement.EN_ATTENTE.peutAllerVers(StatutPaiement.EN_COURS));
        assertTrue(StatutPaiement.EN_COURS.peutAllerVers(StatutPaiement.PAYE));
        assertTrue(StatutPaiement.EN_COURS.peutAllerVers(StatutPaiement.ECHOUE));
        assertTrue(StatutPaiement.EN_COURS.peutAllerVers(StatutPaiement.EXPIRE));
    }

    @Test
    @DisplayName("une transition vers soi-meme est refusee : un callback rejoue ne reecrit rien")
    void transitionVersSoiMemeRefusee() {
        for (StatutPaiement statut : StatutPaiement.values()) {
            assertFalse(statut.peutAllerVers(statut), statut + " -> " + statut);
        }
        assertFalse(StatutPaiement.EN_COURS.peutAllerVers(null));
    }

    @Test
    @DisplayName("les etats terminaux sont bien identifies")
    void etatsTerminaux() {
        assertFalse(StatutPaiement.EN_ATTENTE.estTerminal());
        assertFalse(StatutPaiement.EN_COURS.estTerminal());
        assertTrue(StatutPaiement.PAYE.estTerminal());
        assertTrue(StatutPaiement.ECHOUE.estTerminal());
        assertTrue(StatutPaiement.EXPIRE.estTerminal());
        assertTrue(StatutPaiement.ANNULE.estTerminal());
        assertTrue(StatutPaiement.REMBOURSE.estTerminal());

        assertTrue(StatutPaiement.PAYE.estSucces());
        assertFalse(StatutPaiement.REMBOURSE.estSucces());
    }
}
