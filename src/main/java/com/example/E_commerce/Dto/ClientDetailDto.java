package com.example.E_commerce.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Fiche client complete pour le back-office.
 *
 * <p>L'ancien {@code detailClient} ne renvoyait que l'identite. Au telephone
 * avec un client, il faut aussi savoir ce qu'il a commande, combien il a
 * depense et ce qu'il a dans son panier.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetailDto {
    private PersonneResponseDto client;
    private Instant dateInscription;
    private long nombreCommandes;
    private long nombreCommandesPayees;
    private BigDecimal totalDepense;
    private BigDecimal panierMoyen;
    private Instant dateDerniereCommande;
    /** Articles actuellement dans son panier. */
    private PanierResponseDto panierEnCours;
    /** Ses dernieres commandes, les plus recentes d'abord. */
    private List<CommandeResumeDto> dernieresCommandes;
}
