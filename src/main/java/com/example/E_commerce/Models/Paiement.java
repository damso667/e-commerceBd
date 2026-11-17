package com.example.E_commerce.Models;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutPaiement;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une tentative de reglement d'une commande.
 *
 * <p>Points importants pour l'idempotence :</p>
 * <ul>
 *   <li>{@code reference} est notre identifiant public, unique, et c'est lui
 *       que l'operateur nous renvoie dans son callback.</li>
 *   <li>{@code referenceOperateur} est l'identifiant cote MTN / Orange.</li>
 *   <li>{@code version} (verrou optimiste) empeche deux webhooks simultanes
 *       d'ecraser le meme paiement.</li>
 * </ul>
 */
@Entity
@Table(
        name = "paiement",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_paiement_reference", columnNames = "reference"),
                @UniqueConstraint(name = "uk_paiement_commande", columnNames = "id_commande")
        },
        indexes = @Index(name = "idx_paiement_statut", columnList = "statut_paiement")
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant public du paiement, ex. PAY-42-1-9F3A2B. Jamais reutilise. */
    @Column(nullable = false, length = 64)
    private String reference;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    private String devise;

    @Enumerated(EnumType.STRING)
    @Column(name = "fournisseur", nullable = false, length = 32)
    private FournisseurPaiement fournisseur;

    /**
     * ATTENTION : {@code EnumType.STRING} est indispensable. Sans lui JPA
     * stocke la position de la valeur dans l'enum, et ajouter un statut
     * change retroactivement le sens des lignes deja en base.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_paiement", nullable = false, length = 32)
    private StatutPaiement statutPaiement;

    /** Numero mobile money debite. */
    @Column(length = 32)
    private String numeroPayeur;

    /** Identifiant de la transaction cote operateur (rempli apres l'initiation). */
    @Column(length = 128)
    private String referenceOperateur;

    /** Code d'erreur brut renvoye par l'operateur, utile pour le support. */
    @Column(length = 64)
    private String codeErreur;

    @Column(length = 512)
    private String message;

    /** Numero de la tentative pour cette commande (1, 2, 3...). */
    @Column(nullable = false)
    private Integer tentative = 1;

    @Column(nullable = false)
    private Instant dateCreation;

    private Instant dateMiseAJour;

    /** Au-dela de cette date, un paiement non confirme bascule en EXPIRE. */
    private Instant dateExpiration;

    /** Date de confirmation du succes. */
    private Instant datePaiement;

    /** Verrou optimiste : protege contre deux callbacks concurrents. */
    @Version
    private Long version;

    /**
     * Plusieurs tentatives peuvent exister pour une meme commande : un echec
     * n'efface pas la trace, il ouvre une nouvelle tentative. L'invariant
     * "une seule tentative active a la fois" est verifie dans le service.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_commande", nullable = false)
    @JsonIgnore
    private Commande commande;

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = Instant.now();
        }
        dateMiseAJour = dateCreation;
    }

    @PreUpdate
    void avantMiseAJour() {
        dateMiseAJour = Instant.now();
    }

    public boolean estExpire(Instant maintenant) {
        return dateExpiration != null && maintenant.isAfter(dateExpiration);
    }
}
