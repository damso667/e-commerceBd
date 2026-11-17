package com.example.E_commerce.Paiement.Dto;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Models.Paiement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vue administrateur d'un paiement.
 *
 * <p>Plus complete que {@code PaiementResponseDto} destine au client : le
 * numero payeur est en clair (indispensable pour traiter une reclamation
 * ou effectuer un remboursement manuel), le code d'erreur de l'operateur
 * et le nombre de tentatives sont exposes.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaiementAdminDto {

    private String reference;
    private Long idCommande;
    private Long idClient;
    private String nomClient;
    private String emailClient;
    private BigDecimal montant;
    private String devise;
    private FournisseurPaiement fournisseur;
    private StatutPaiement statut;
    private boolean terminal;
    /** En clair : l'administrateur en a besoin pour le support. */
    private String numeroPayeur;
    private String referenceOperateur;
    private String codeErreur;
    private String message;
    private Integer tentative;
    private Instant dateCreation;
    private Instant dateMiseAJour;
    private Instant dateExpiration;
    private Instant datePaiement;

    public static PaiementAdminDto of(Paiement p) {
        PaiementAdminDto dto = new PaiementAdminDto();
        dto.setReference(p.getReference());
        dto.setMontant(p.getMontant());
        dto.setDevise(p.getDevise());
        dto.setFournisseur(p.getFournisseur());
        dto.setStatut(p.getStatutPaiement());
        dto.setTerminal(p.getStatutPaiement().estTerminal());
        dto.setNumeroPayeur(p.getNumeroPayeur());
        dto.setReferenceOperateur(p.getReferenceOperateur());
        dto.setCodeErreur(p.getCodeErreur());
        dto.setMessage(p.getMessage());
        dto.setTentative(p.getTentative());
        dto.setDateCreation(p.getDateCreation());
        dto.setDateMiseAJour(p.getDateMiseAJour());
        dto.setDateExpiration(p.getDateExpiration());
        dto.setDatePaiement(p.getDatePaiement());
        if (p.getCommande() != null) {
            dto.setIdCommande(p.getCommande().getId());
            if (p.getCommande().getClient() != null) {
                dto.setIdClient(p.getCommande().getClient().getId());
                dto.setNomClient(p.getCommande().getClient().getNom());
                dto.setEmailClient(p.getCommande().getClient().getEmail());
            }
        }
        return dto;
    }
}
