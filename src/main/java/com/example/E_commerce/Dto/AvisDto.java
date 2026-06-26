package com.example.E_commerce.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvisDto {
    private Long idArticle;
    private Integer note;
    private String commentaire;

}
