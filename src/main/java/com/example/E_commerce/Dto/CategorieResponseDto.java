package com.example.E_commerce.Dto;


import com.example.E_commerce.Models.Categorie;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorieResponseDto {
    private Long idCategorie;
    private String nomCategorie;
    private String description;

    public static CategorieResponseDto of(Categorie categorie) {
        CategorieResponseDto dto = new CategorieResponseDto();
        dto.setIdCategorie(categorie.getIdCategorie());
        dto.setNomCategorie(categorie.getNomCategorie());
        dto.setDescription(categorie.getDescription());
        return dto;
    }
}
