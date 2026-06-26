package com.example.E_commerce.Models;

import com.example.E_commerce.Enum.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Personne {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String motDePasse;
    private String adresse;
    private String numeroTelephone;

    @Column(nullable = false)
    private boolean actif = true;

    @Enumerated(EnumType.STRING)
    private Role role;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Commande> commandes = new ArrayList<>();

    @OneToMany(mappedBy = "auteur")
    @JsonIgnore
    private List<Avis> avis = new ArrayList<>();

    // Un client possède un seul panier actif (1..1)
    @OneToOne(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Pagnier pagnier;
}
