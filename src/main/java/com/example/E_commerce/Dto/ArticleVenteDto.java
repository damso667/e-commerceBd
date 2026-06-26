package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Article;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor
@AllArgsConstructor
public class ArticleVenteDto {
    private Long idArticle;
    private String libelle;
    private Integer quantiteTotaleVendue;
    private BigDecimal chiffreAffaires;

    public static ArticleVenteDto of(Article article, Integer quantite) {
        ArticleVenteDto dto = new ArticleVenteDto();
        dto.setIdArticle(article.getId());
        dto.setLibelle(article.getLibelle());
        dto.setQuantiteTotaleVendue(quantite);
        dto.setChiffreAffaires(article.getPrix()
                .multiply(BigDecimal.valueOf(quantite)));
        return dto;
    }
}
