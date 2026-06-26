package com.example.E_commerce.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PagnierDto {
    private Long idClient;
    private Long idArticle;
    private Integer quantite;
}
