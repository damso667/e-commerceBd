package com.example.E_commerce.Paiement.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultat d'un remboursement.
 *
 * <p>{@code automatique} est l'information decisive pour l'administrateur :
 * si elle est fausse, le paiement est bien marque REMBOURSE dans le systeme
 * mais <b>l'argent n'a pas bouge</b> et il reste un transfert a effectuer a
 * la main. Ne pas l'afficher serait trompeur.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultatRemboursementDto {
    private PaiementAdminDto paiement;
    private boolean automatique;
    private String consigne;
}
