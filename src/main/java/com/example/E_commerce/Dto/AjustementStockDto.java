package com.example.E_commerce.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ajustement de stock.
 *
 * <p>Endpoint dedie plutot qu'un PUT sur l'article entier : reapprovisionner
 * ne doit pas obliger a renvoyer prix, description et image, au risque de
 * les ecraser par inadvertance.</p>
 *
 * <p>On exprime une <b>variation</b> et non une valeur absolue : "+10 recus"
 * est l'operation reelle, et deux reapprovisionnements simultanes
 * s'additionnent au lieu de s'ecraser.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AjustementStockDto {

    @NotNull(message = "La variation est obligatoire (positive pour un reapprovisionnement)")
    private Integer variation;

    @Size(max = 300, message = "Motif trop long (300 caracteres maximum)")
    private String motif;
}
