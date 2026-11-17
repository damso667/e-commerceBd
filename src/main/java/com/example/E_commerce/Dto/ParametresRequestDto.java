package com.example.E_commerce.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Modification de l'identite de la boutique par l'administrateur. */
@Data
public class ParametresRequestDto {

    @NotBlank(message = "Le nom de la boutique est obligatoire")
    @Size(max = 60, message = "60 caracteres maximum")
    private String nom;

    @Size(max = 120, message = "120 caracteres maximum")
    private String slogan;

    @NotBlank(message = "La couleur est obligatoire")
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$",
             message = "Couleur attendue au format hexadecimal, par exemple #0B6B5E")
    private String couleurPrimaire;

    @Pattern(regexp = "^$|^([+]?237)?[26][0-9]{8}$",
             message = "Numero camerounais invalide")
    private String telephoneContact;

    @Size(max = 150)
    @Email(message = "Format d'email invalide")
    private String emailContact;

    @Size(max = 255, message = "255 caracteres maximum")
    private String adresse;

    @Size(max = 300, message = "300 caracteres maximum")
    private String messageAccueil;
}
