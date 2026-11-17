package com.example.E_commerce.Models;

import com.example.E_commerce.Enum.FournisseurPaiement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Journal des notifications recues des operateurs (webhooks).
 *
 * <p>C'est la piece maitresse de l'idempotence cote callback : la contrainte
 * unique (fournisseur, id_evenement) fait que rejouer dix fois le meme
 * callback ne produit qu'une seule insertion. Les neuf autres levent une
 * violation de contrainte qu'on interprete comme "deja traite".</p>
 */
@Entity
@Table(
        name = "evenement_paiement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_evenement_fournisseur_id",
                columnNames = {"fournisseur", "id_evenement"})
)
@Getter
@Setter
@NoArgsConstructor
public class EvenementPaiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FournisseurPaiement fournisseur;

    /** Identifiant unique de l'evenement, fourni par l'operateur. */
    @Column(name = "id_evenement", nullable = false, length = 128)
    private String idEvenement;

    @Column(length = 64)
    private String referencePaiement;

    @Column(length = 32)
    private String statutRecu;

    @Lob
    @Column(columnDefinition = "text")
    private String charge;

    @Column(nullable = false)
    private Instant dateReception = Instant.now();
}
