package com.example.E_commerce.Models;

import com.example.E_commerce.Enum.StatutPaiement;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Paiement {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    private BigDecimal montant;
    private LocalDate datePaiement;
    private StatutPaiement statutPaiement;
    private String modePaiement;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_commande", nullable = false, unique = true)
    @JsonIgnore // évite la boucle infinie
    private Commande commande;

}
