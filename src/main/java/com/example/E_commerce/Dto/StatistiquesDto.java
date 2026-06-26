package com.example.E_commerce.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatistiquesDto {
    private BigDecimal chiffreAffairesTotal;
    private Long nombreTotalClients;
    private Map<String, Long> commandesParStatut;
    private List<ArticleVenteDto> articlesLesPlusVendus;
}
