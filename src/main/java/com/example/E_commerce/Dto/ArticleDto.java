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
                .imageUrl(adresseImage(a))
                .categorieId(a.getCategorie().getIdCategorie())
                .nomCategorie(a.getCategorie().getNomCategorie())
                .stock(a.getStock())
                .build();
    }

    /**
     * Ou le front doit chercher l'image.
     *
     * <p>Une photo televersee l'emporte sur une URL externe : c'est la
     * seule que l'administrateur controle depuis le back-office. La version
     * en parametre change a chaque depot, ce qui suffit a casser le cache
     * du navigateur sans toucher aux en-tetes.</p>
     */
    static String adresseImage(Article a) {
        if (a.aUneImage()) {
            return "/api/personne/articleImage/" + a.getId() + "?v=" + a.getImageVersion();
        }
        return a.getImageUrl();
    }
}
