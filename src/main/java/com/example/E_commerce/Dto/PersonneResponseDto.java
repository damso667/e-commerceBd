package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Personne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonneResponseDto {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String adresse;
    private String numeroTelephone;
    private String role;
    private boolean actif;

    public static PersonneResponseDto of(Personne personne) {
        PersonneResponseDto dto = new PersonneResponseDto();
        dto.setId(personne.getId());
        dto.setNom(personne.getNom());
        dto.setPrenom(personne.getPrenom());
        dto.setEmail(personne.getEmail());
        dto.setAdresse(personne.getAdresse());
        dto.setNumeroTelephone(personne.getNumeroTelephone());
        dto.setRole(personne.getRole().name());
        dto.setActif(personne.isActif());
        return dto;
    }
}
