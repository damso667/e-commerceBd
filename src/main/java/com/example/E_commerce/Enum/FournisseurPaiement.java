package com.example.E_commerce.Enum;

/**
 * Operateurs de mobile money supportes.
 * Ajouter un operateur = ajouter une valeur ici + une implementation de
 * {@code PasserellePaiement}. Aucun autre fichier n'a besoin de changer.
 */
public enum FournisseurPaiement {
    MTN_MOMO("MTN Mobile Money"),
    ORANGE_MONEY("Orange Money");

    private final String libelle;

    FournisseurPaiement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
