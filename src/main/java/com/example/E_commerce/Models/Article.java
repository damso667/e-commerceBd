package com.example.E_commerce.Models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@org.hibernate.annotations.BatchSize(size = 50)
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer stock;
    private String libelle;
    private String description;
    private BigDecimal prix;

    /**
     * Adresse d'une image **externe**. Conservee pour les articles amorces
     * avec une URL ; l'administrateur, lui, televerse un fichier.
     */
    private String imageUrl;

    // ---------- Photo televersee ----------
    //
    // Stockee en base et non sur le disque : celui de Render est ephemere,
    // un redeploiement effacerait toutes les photos du catalogue. Meme
    // raison que pour le logo de la boutique.

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "image_donnees")
    private byte[] imageDonnees;

    @Column(name = "image_type", length = 40)
    private String imageType;

    /** Change a chaque depot : sert d'ETag et casse le cache du navigateur. */
    @Column(name = "image_version", length = 32)
    private String imageVersion;

    /** Vrai quand une photo a ete televersee pour cet article. */
    public boolean aUneImage() {
        return imageVersion != null && !imageVersion.isBlank();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categorie", nullable = false)
    private Categorie categorie;

    @OneToMany(mappedBy = "article")
    @JsonIgnore
    private List<Avis> avis = new ArrayList<>();
}
