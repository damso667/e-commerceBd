package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Pagnier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PanierResponseDto {
    private Long idPanier;
    private String nomClient;
    private List<LignePanierResponseDto> lignes;
    private BigDecimal total;

    public static PanierResponseDto of(Pagnier pagnier) {
        PanierResponseDto dto = new PanierResponseDto();
        dto.setIdPanier(pagnier.getId());
        dto.setNomClient(pagnier.getClient().getNom());
        dto.setLignes(pagnier.getLignes().stream()
                .map(LignePanierResponseDto::of)
                .toList());
        dto.setTotal(dto.getLignes().stream()
                .map(LignePanierResponseDto::getSousTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return dto;
    }
}
