package com.example.E_commerce.Paiement.Dto;

import com.example.E_commerce.Enum.FournisseurPaiement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corps attendu pour declencher un paiement.
 *
 * <p>Noter ce qui n'y figure PAS : le montant. Il est recalcule par le
 * serveur a partir des lignes de la commande. Laisser le client proposer
 * un montant est une faille : il suffirait de poster 1 FCFA.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitierPaiementDto {

    @NotNull(message = "Le moyen de paiement est obligatoire (MTN_MOMO ou ORANGE_MONEY)")
    private FournisseurPaiement fournisseur;

    @NotBlank(message = "Le numero de telephone est obligatoire")
    @Pattern(regexp = "^([+]?237)?[26][0-9]{8}$",
             message = "Numero camerounais invalide (ex : 677123456 ou +237677123456)")
    private String numeroPayeur;
}
