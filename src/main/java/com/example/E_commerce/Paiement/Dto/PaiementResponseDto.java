package com.example.E_commerce.Paiement.Dto;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Models.Paiement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Vue publique d'un paiement. Aucune donnee sensible n'en sort. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaiementResponseDto {

    private String reference;
    private Long idCommande;
    private BigDecimal montant;
    private String devise;
    private FournisseurPaiement fournisseur;
    private StatutPaiement statut;
    /** true tant que le client doit encore valider sur son telephone. */
    private boolean enAttenteDeValidation;
    private boolean terminal;
    private String numeroPayeur;
    private String message;
    private String codeErreur;
    private Integer tentative;
    private Instant dateCreation;
    private Instant dateExpiration;
    private Instant datePaiement;

    public static PaiementResponseDto of(Paiement p) {
        PaiementResponseDto dto = new PaiementResponseDto();
        dto.setReference(p.getReference());
        dto.setIdCommande(p.getCommande() == null ? null : p.getCommande().getId());
        dto.setMontant(p.getMontant());
        dto.setDevise(p.getDevise());
        dto.setFournisseur(p.getFournisseur());
        dto.setStatut(p.getStatutPaiement());
        dto.setEnAttenteDeValidation(!p.getStatutPaiement().estTerminal());
        dto.setTerminal(p.getStatutPaiement().estTerminal());
        dto.setNumeroPayeur(masquer(p.getNumeroPayeur()));
        dto.setMessage(p.getMessage());
        dto.setCodeErreur(p.getCodeErreur());
        dto.setTentative(p.getTentative());
        dto.setDateCreation(p.getDateCreation());
        dto.setDateExpiration(p.getDateExpiration());
        dto.setDatePaiement(p.getDatePaiement());
        return dto;
    }

    /** On ne renvoie jamais le numero complet dans une reponse d'API. */
    private static String masquer(String numero) {
        if (numero == null || numero.length() < 4) {
            return numero;
        }
        return "*****" + numero.substring(numero.length() - 4);
    }
}
