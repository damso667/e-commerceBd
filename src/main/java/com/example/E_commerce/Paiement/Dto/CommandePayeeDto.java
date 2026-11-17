package com.example.E_commerce.Paiement.Dto;

import com.example.E_commerce.Dto.CommandeResponseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reponse de "passer commande" : la commande creee et le paiement lance.
 * Le front affiche "validez sur votre telephone" puis interroge
 * GET /api/user/paiements/{reference} jusqu'a un statut terminal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandePayeeDto {
    private CommandeResponseDto commande;
    private PaiementResponseDto paiement;
    /** Indication pour le front : intervalle de polling conseille. */
    private int intervallePollingSecondes = 3;

    public CommandePayeeDto(CommandeResponseDto commande, PaiementResponseDto paiement) {
        this.commande = commande;
        this.paiement = paiement;
        this.intervallePollingSecondes = 3;
    }
}
