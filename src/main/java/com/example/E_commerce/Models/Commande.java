package com.example.E_commerce.Models;

import com.example.E_commerce.Enum.StatutCommande;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String libelle;
    private LocalDate dateCommande;

    /**
     * Horodatage precis de la creation. {@code dateCommande} est au jour
     * pres, ce qui ne permet pas de reperer une commande restee sans
     * paiement depuis 20 minutes.
     */
    private java.time.Instant dateCreation;
    private BigDecimal montantTotal;

    @Enumerated(EnumType.STRING)
    private StatutCommande statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client", nullable = false)
    private Personne client;

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = java.time.Instant.now();
        }
    }

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommande> lignes = new ArrayList<>();

    /**
     * Historique des tentatives de paiement, de la plus ancienne a la plus
     * recente. Une commande peut n'en avoir aucune (pas encore payee) ou
     * plusieurs (le client a reessaye apres un echec).
     */
    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Paiement> paiements = new ArrayList<>();

    /** Derniere tentative de paiement, ou null si la commande n'a jamais ete reglee. */
    public Paiement paiementCourant() {
        if (paiements == null || paiements.isEmpty()) {
            return null;
        }
        return paiements.stream()
                .max(java.util.Comparator.comparing(
                        Paiement::getDateCreation,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
    }

    /** true s'il existe une tentative en cours : interdit d'en lancer une autre. */
    public boolean aUnPaiementEnCours() {
        return paiements != null && paiements.stream()
                .anyMatch(p -> !p.getStatutPaiement().estTerminal());
    }

    public boolean estReglee() {
        return paiements != null && paiements.stream()
                .anyMatch(p -> p.getStatutPaiement().estSucces());
    }

}
