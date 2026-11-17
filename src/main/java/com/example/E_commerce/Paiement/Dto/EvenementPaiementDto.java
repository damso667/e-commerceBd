package com.example.E_commerce.Paiement.Dto;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Models.EvenementPaiement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Un callback recu de l'operateur, tel qu'il est arrive. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvenementPaiementDto {
    private Long id;
    private FournisseurPaiement fournisseur;
    private String idEvenement;
    private String statutRecu;
    private String charge;
    private Instant dateReception;

    public static EvenementPaiementDto of(EvenementPaiement e) {
        return new EvenementPaiementDto(e.getId(), e.getFournisseur(), e.getIdEvenement(),
                e.getStatutRecu(), e.getCharge(), e.getDateReception());
    }
}
