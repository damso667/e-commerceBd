package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.LigneCommande;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LigneCommandeResponseDto {
    private Long idArticle;
    private String libelle;
    private Integer quantite;
    private BigDecimal prixUnitaire;
    private BigDecimal sousTotal;

    public static LigneCommandeResponseDto of(LigneCommande ligne) {
        LigneCommandeResponseDto dto = new LigneCommandeResponseDto();
        dto.setIdArticle(ligne.getArticle().getId());
        dto.setLibelle(ligne.getArticle().getLibelle());
        dto.setQuantite(ligne.getQuantite());
        dto.setPrixUnitaire(ligne.getPrixUnitaire());
        dto.setSousTotal(ligne.getPrixUnitaire()
                .multiply(BigDecimal.valueOf(ligne.getQuantite())));
        return dto;
    }
}
