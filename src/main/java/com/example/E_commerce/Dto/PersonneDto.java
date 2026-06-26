package com.example.E_commerce.Dto;

import lombok.Data;

@Data
public class PersonneDto {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String motDePasse;
}
