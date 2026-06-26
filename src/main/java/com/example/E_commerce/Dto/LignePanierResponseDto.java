package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.LignePagnier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LignePanierResponseDto {
    private Long idArticle;
    private String libelle;
    private BigDecimal prix;
    private Integer quantite;
    private BigDecimal sousTotal;

    public static LignePanierResponseDto of(LignePagnier ligne) {
        LignePanierResponseDto dto = new LignePanierResponseDto();
        dto.setIdArticle(ligne.getArticle().getId());
        dto.setLibelle(ligne.getArticle().getLibelle());
        dto.setPrix(ligne.getArticle().getPrix());
        dto.setQuantite(ligne.getQuantite());
        dto.setSousTotal(ligne.getArticle().getPrix()
                .multiply(BigDecimal.valueOf(ligne.getQuantite())));
        return dto;
    }
}
