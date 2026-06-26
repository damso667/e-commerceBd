package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Article;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ArticleDto {
    private Long id;
    private String nom;
    private String description;
    private BigDecimal prix;
     private String imageUrl;
     private Long categorieId;

    public static ArticleDto of(Article a) {
        return ArticleDto.builder()
                .id(a.getId())
                .nom(a.getLibelle())
                .description(a.getDescription())
                .prix(a.getPrix())
                .imageUrl(a.getImageUrl())
                .categorieId(a.getCategorie().getIdCategorie())
                .build();
    }




}
