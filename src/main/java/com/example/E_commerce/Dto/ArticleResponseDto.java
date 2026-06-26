package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Article;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponseDto {
    private Long idArticle;
    private String libelle;
    private String description;
    private BigDecimal prix;
    private Integer stock;
    private String imageUrl;
    private String nomCategorie;

    public static ArticleResponseDto of(Article article) {
        ArticleResponseDto dto = new ArticleResponseDto();
        dto.setIdArticle(article.getId());
        dto.setLibelle(article.getLibelle());
        dto.setDescription(article.getDescription());
        dto.setPrix(article.getPrix());
        dto.setStock(article.getStock());
        dto.setImageUrl(article.getImageUrl());
        dto.setNomCategorie(article.getCategorie().getNomCategorie());
        return dto;
    }
}