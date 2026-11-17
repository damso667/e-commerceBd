package com.example.E_commerce.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorieRequestDto {

    @NotBlank(message = "Le nom de la categorie est obligatoire")
    @Size(max = 100, message = "Nom trop long (100 caracteres maximum)")
    private String nomCategorie;

    @Size(max = 500, message = "Description trop longue (500 caracteres maximum)")
    private String description;
}
