package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Article;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Article vu par un visiteur, connecte ou non.
 *
 * <p>Le stock est expose volontairement : une boutique qui n'affiche pas
 * « plus que 3 exemplaires » laisse le client decouvrir la rupture au
 * moment du paiement, ce qui est la pire place pour l'apprendre. C'est une
 * information commerciale, pas une donnee sensible.</p>
 */
@Data
@Builder
public class ArticleDto {

    private Long id;
    private String nom;
    private String description;
    private BigDecimal prix;
    private String imageUrl;
    private Long categorieId;
    private String nomCategorie;

    /** Quantite restante. 0 = rupture. */
    private Integer stock;

    public static ArticleDto of(Article a) {
        return ArticleDto.builder()
                .id(a.getId())
                .nom(a.getLibelle())
                .description(a.getDescription())
                .prix(a.getPrix())
                .imageUrl(a.getImageUrl())
                .categorieId(a.getCategorie().getIdCategorie())
                .nomCategorie(a.getCategorie().getNomCategorie())
                .stock(a.getStock())
                .build();
    }
}
