package com.example.E_commerce.Models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Journal des actions d'administration.
 *
 * <p>Sans cette trace, personne ne peut répondre à « qui a bloqué ce
 * client ? », « qui a remboursé cette commande ? » ou « quand ce prix
 * a-t-il changé ? ». C'est la première chose que l'on réclame le jour d'un
 * litige, et la dernière que l'on peut reconstituer après coup.</p>
 *
 * <p>Les écritures se font dans la transaction de l'action tracée : si
 * l'action est annulée, la ligne de journal l'est aussi. On ne journalise
 * donc que ce qui a réellement eu lieu.</p>
 */
@Entity
@Table(
        name = "journal_admin",
        indexes = {
                @Index(name = "idx_journal_date", columnList = "date_action"),
                @Index(name = "idx_journal_cible", columnList = "cible_type, cible_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class JournalAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email de l'administrateur à l'origine de l'action. */
    @Column(nullable = false, length = 150)
    private String acteur;

    /** Verbe stable et lisible, ex. ARTICLE_MODIFIE, PAIEMENT_REMBOURSE. */
    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "cible_type", length = 32)
    private String cibleType;

    @Column(name = "cible_id", length = 64)
    private String cibleId;

    /** Ce qui a changé, en clair. Jamais de mot de passe ni de jeton ici. */
    @Column(length = 1000)
    private String details;

    @Column(name = "date_action", nullable = false)
    private Instant dateAction = Instant.now();
}
