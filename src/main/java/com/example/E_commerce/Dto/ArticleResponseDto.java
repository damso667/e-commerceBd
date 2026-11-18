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

    /**
     * Vrai quand la photo est televersee, faux quand elle vient d'une URL
     * externe. Le back-office propose « Remplacer » dans un cas et
     * « Televerser » dans l'autre, et ne propose « Retirer » que pour une
     * photo qu'il sait effacer.
     */
    private boolean imageTeleversee;

    public static ArticleResponseDto of(Article article) {
        ArticleResponseDto dto = new ArticleResponseDto();
        dto.setIdArticle(article.getId());
        dto.setLibelle(article.getLibelle());
        dto.setDescription(article.getDescription());
        dto.setPrix(article.getPrix());
        dto.setStock(article.getStock());
        dto.setImageUrl(ArticleDto.adresseImage(article));
        dto.setImageTeleversee(article.aUneImage());
        dto.setNomCategorie(article.getCategorie().getNomCategorie());
        return dto;
    }
}