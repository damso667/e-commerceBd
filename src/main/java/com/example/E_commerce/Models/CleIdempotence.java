package com.example.E_commerce.Models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Trace d'une requete entrante portant un en-tete {@code Idempotency-Key}.
 *
 * <p>Principe (identique a celui de Stripe) :</p>
 * <ol>
 *   <li>Premier appel : on insere la cle, on execute, on memorise la reponse.</li>
 *   <li>Rejeu avec la meme cle ET le meme corps : on renvoie la reponse
 *       memorisee sans rien re-executer.</li>
 *   <li>Rejeu avec la meme cle mais un corps different : 409, car la cle a
 *       deja servi pour autre chose.</li>
 * </ol>
 */
@Entity
@Table(
        name = "cle_idempotence",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotence_cle_endpoint",
                columnNames = {"cle", "endpoint"})
)
@Getter
@Setter
@NoArgsConstructor
public class CleIdempotence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String cle;

    /** Methode + chemin, pour qu'une meme cle sur deux routes ne se telescope pas. */
    @Column(nullable = false, length = 256)
    private String endpoint;

    /** Utilisateur proprietaire de la cle : une cle ne fuite pas d'un compte a l'autre. */
    @Column(nullable = false)
    private Long idUtilisateur;

    /** SHA-256 du corps de la requete. */
    @Column(nullable = false, length = 64)
    private String empreinteRequete;

    /** null tant que la requete est en cours de traitement. */
    private Integer statutHttp;

    @Lob
    @Column(columnDefinition = "text")
    private String corpsReponse;

    @Column(nullable = false)
    private Instant dateCreation = Instant.now();

    private Instant dateExpiration;

    public boolean estTerminee() {
        return statutHttp != null;
    }
}
