package com.example.E_commerce.Paiement.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Demande de remboursement : le motif est obligatoire, il finit au journal. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemboursementDto {

    @NotBlank(message = "Le motif du remboursement est obligatoire")
    @Size(max = 500, message = "Motif trop long (500 caracteres maximum)")
    private String motif;
}
