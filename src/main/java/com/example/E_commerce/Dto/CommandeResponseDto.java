package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.Commande;
import com.example.E_commerce.Models.Paiement;
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
    /** Operateur de la derniere tentative de paiement, null si aucune. */
    private String modePaiement;
    /** Statut de la derniere tentative de paiement, null si aucune. */
    private String statutPaiement;
    /** Reference a utiliser pour interroger GET /api/user/paiements/{reference}. */
    private String referencePaiement;
    private BigDecimal montantTotal;
    private List<LigneCommandeResponseDto> lignes;

    public static CommandeResponseDto of(Commande commande) {
        CommandeResponseDto dto = new CommandeResponseDto();
        dto.setIdCommande(commande.getId());
        dto.setNomClient(commande.getClient().getNom());
        dto.setDateCommande(commande.getDateCommande());
        dto.setStatut(commande.getStatut().name());

        Paiement paiement = commande.paiementCourant();
        if (paiement != null) {
            dto.setModePaiement(paiement.getFournisseur().name());
            dto.setStatutPaiement(paiement.getStatutPaiement().name());
            dto.setReferencePaiement(paiement.getReference());
        }

        dto.setLignes(commande.getLignes().stream()
                .map(LigneCommandeResponseDto::of)
                .toList());

        // Le montant fige a la commande fait foi. On ne recalcule a partir
        // des lignes que pour les anciennes commandes ou il est absent.
        dto.setMontantTotal(commande.getMontantTotal() != null
                ? commande.getMontantTotal()
                : dto.getLignes().stream()
                        .map(LigneCommandeResponseDto::getSousTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        return dto;
    }
}
