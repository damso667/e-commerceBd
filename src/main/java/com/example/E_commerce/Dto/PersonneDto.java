package com.example.E_commerce.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Inscription.
 *
 * <p>{@code id} a ete retire : il n'a aucun sens dans une requete de
 * creation et laissait croire qu'un client pouvait choisir son identifiant.</p>
 */
@Data
public class PersonneDto {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100)
    private String nom;

    @Size(max = 100)
    private String prenom;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit faire au moins 8 caracteres")
    private String motDePasse;

    @Size(max = 255)
    private String adresse;

    @Pattern(regexp = "^$|^([+]?237)?[26][0-9]{8}$",
             message = "Numero de telephone camerounais invalide")
    private String numeroTelephone;
}
