package com.example.E_commerce.Dto;

import jakarta.validation.constraints.*;
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

    @NotBlank(message = "Le libelle est obligatoire")
    @Size(max = 200, message = "Libelle trop long (200 caracteres maximum)")
    private String libelle;

    @Size(max = 2000, message = "Description trop longue (2000 caracteres maximum)")
    private String description;

    @NotNull(message = "Le prix est obligatoire")
    @DecimalMin(value = "1", message = "Le prix doit etre au moins de 1 FCFA")
    @Digits(integer = 12, fraction = 2, message = "Montant invalide")
    private BigDecimal prix;

    @NotNull(message = "Le stock est obligatoire")
    @Min(value = 0, message = "Le stock ne peut pas etre negatif")
    @Max(value = 1000000, message = "Stock irrealiste")
    private Integer stock;

    @Pattern(regexp = "^$|^https?://.+", message = "L'URL de l'image doit commencer par http:// ou https://")
    @Size(max = 1000)
    private String imageUrl;

    @NotNull(message = "La categorie est obligatoire")
    private Long idCategorie;
}
