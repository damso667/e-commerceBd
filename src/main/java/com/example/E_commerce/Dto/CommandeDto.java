package com.example.E_commerce.Dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CommandeDto {
    private String modePaiement;
    private BigDecimal montant;
}
