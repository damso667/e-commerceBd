package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Commande;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommandeResponseDto {
    private Long idCommande;
    private String nomClient;
    private LocalDate dateCommande;
    private String statut;
    private String modePaiement;
    private BigDecimal montantTotal;
    private List<LigneCommandeResponseDto> lignes;

    public static CommandeResponseDto of(Commande commande) {
        CommandeResponseDto dto = new CommandeResponseDto();
        dto.setIdCommande(commande.getId());
        dto.setNomClient(commande.getClient().getNom());
        dto.setDateCommande(commande.getDateCommande());
        dto.setStatut(commande.getStatut().name());
        dto.setModePaiement(commande.getPaiement() != null ?
                commande.getPaiement().getModePaiement() : null);
        dto.setLignes(commande.getLignes().stream()
                .map(LigneCommandeResponseDto::of)
                .toList());
        dto.setMontantTotal(dto.getLignes().stream()
                .map(LigneCommandeResponseDto::getSousTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return dto;
    }
}
