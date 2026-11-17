package com.example.E_commerce.Paiement.Dto;

import com.example.E_commerce.Dto.CommandeResponseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Fiche complete d'un paiement pour le support.
 *
 * <p>Tout ce qu'il faut pour instruire une reclamation en un seul appel :
 * le paiement, la commande concernee, les tentatives precedentes et les
 * callbacks recus de l'operateur.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaiementDetailAdminDto {
    private PaiementAdminDto paiement;
    private CommandeResponseDto commande;
    /** Toutes les tentatives de la commande, de la plus ancienne a la plus recente. */
    private List<PaiementAdminDto> tentatives;
    /** Callbacks recus pour cette tentative. */
    private List<EvenementPaiementDto> evenements;
}
