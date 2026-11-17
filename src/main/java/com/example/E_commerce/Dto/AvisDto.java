package com.example.E_commerce.Dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvisDto {

    @NotNull(message = "L'identifiant de l'article est obligatoire")
    private Long idArticle;

    @NotNull(message = "La note est obligatoire")
    @Min(value = 1, message = "La note minimale est 1")
    @Max(value = 5, message = "La note maximale est 5")
    private Integer note;

    @Size(max = 1000, message = "Commentaire trop long (1000 caracteres maximum)")
    private String commentaire;
}
