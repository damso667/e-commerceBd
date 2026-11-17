package com.example.E_commerce.Dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ajout au panier.
 *
 * <p>Le champ {@code idClient} a ete retire : il laissait un client
 * ajouter des articles dans le panier d'un autre en changeant la valeur.
 * L'identite vient desormais de la session authentifiee, jamais du corps
 * de la requete.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PagnierDto {

    @NotNull(message = "L'identifiant de l'article est obligatoire")
    private Long idArticle;

    @Min(value = 1, message = "La quantite doit etre au moins 1")
    @Max(value = 20, message = "20 exemplaires maximum par article")
    private Integer quantite = 1;
}
