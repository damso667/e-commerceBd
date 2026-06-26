package com.example.E_commerce.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRequestDto {
    private String libelle;
    private String description;
    private BigDecimal prix;
    private Integer stock;
    private String imageUrl;
    private Long idCategorie;
}
