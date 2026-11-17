package com.example.E_commerce.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Avis vu par l'admin : on sait qui a ecrit, et sur quoi. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvisAdminDto {
    private Long idAvis;
    private Integer note;
    private String commentaire;
    private LocalDate dateAvis;
    private Long idAuteur;
    private String nomAuteur;
    private String emailAuteur;
    private Long idArticle;
    private String libelleArticle;

    public static AvisAdminDto of(com.example.E_commerce.Models.Avis avis) {
        AvisAdminDto dto = new AvisAdminDto();
        dto.setIdAvis(avis.getId());
        dto.setNote(avis.getNote());
        dto.setCommentaire(avis.getCommentaire());
        dto.setDateAvis(avis.getDateAvis());
        if (avis.getAuteur() != null) {
            dto.setIdAuteur(avis.getAuteur().getId());
            dto.setNomAuteur(avis.getAuteur().getNom());
            dto.setEmailAuteur(avis.getAuteur().getEmail());
        }
        if (avis.getArticle() != null) {
            dto.setIdArticle(avis.getArticle().getId());
            dto.setLibelleArticle(avis.getArticle().getLibelle());
        }
        return dto;
    }
}
