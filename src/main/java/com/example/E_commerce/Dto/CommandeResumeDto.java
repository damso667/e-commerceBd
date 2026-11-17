package com.example.E_commerce.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Ligne de liste pour le back-office.
 *
 * <p>Volontairement sans les lignes de commande : une liste de 20 commandes
 * n'a pas besoin de leurs 60 articles, et les charger declencherait 20
 * requetes supplementaires. Le detail reste disponible sur
 * {@code GET /api/admin/commandes/{id}}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandeResumeDto {
    private Long idCommande;
    private LocalDate dateCommande;
    private Instant dateCreation;
    private String statut;
    private BigDecimal montantTotal;
    private Long idClient;
    private String nomClient;
    private String emailClient;
    private int nombreLignes;

    public static CommandeResumeDto of(com.example.E_commerce.Models.Commande commande) {
        CommandeResumeDto dto = new CommandeResumeDto();
        dto.setIdCommande(commande.getId());
        dto.setDateCommande(commande.getDateCommande());
        dto.setDateCreation(commande.getDateCreation());
        dto.setStatut(commande.getStatut() == null ? null : commande.getStatut().name());
        dto.setMontantTotal(commande.getMontantTotal());
        if (commande.getClient() != null) {
            dto.setIdClient(commande.getClient().getId());
            dto.setNomClient(commande.getClient().getNom());
            dto.setEmailClient(commande.getClient().getEmail());
        }
        // @BatchSize sur Commande.lignes fait charger les collections de
        // toute la page en une requete : pas de N+1 sur une vue de liste.
        dto.setNombreLignes(commande.getLignes() == null ? 0 : commande.getLignes().size());
        return dto;
    }
}
