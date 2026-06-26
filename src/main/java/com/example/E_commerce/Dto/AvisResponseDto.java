package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Avis;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvisResponseDto {
    private Long idAvis;
    private String nomAuteur;
    private Integer note;
    private String commentaire;
    private LocalDate dateAvis;

    public static AvisResponseDto of(Avis avis) {
        AvisResponseDto dto = new AvisResponseDto();
        dto.setIdAvis(avis.getId());
        dto.setNomAuteur(avis.getAuteur().getNom());
        dto.setNote(avis.getNote());
        dto.setCommentaire(avis.getCommentaire());
        dto.setDateAvis(avis.getDateAvis());
        return dto;
    }
}
