package com.example.E_commerce.Dto;

import com.example.E_commerce.Enum.FournisseurPaiement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Tableau de bord complet, sur une période paramétrable.
 *
 * <p>Tout est regroupé en un seul objet, donc un seul appel HTTP : un
 * back-office qui déclenche douze requêtes pour afficher une page est lent
 * et difficile à faire évoluer.</p>
 *
 * <p>Les sous-structures sont des records imbriqués plutôt que douze
 * fichiers : elles n'ont aucune existence en dehors de ce tableau de bord.</p>
 *
 * @param periode          bornes effectivement appliquées
 * @param ventes           chiffres de la période et évolution vs période précédente
 * @param aujourdhui       raccourci sur la journée en cours
 * @param serie            un point par jour, pour les courbes
 * @param commandesParStatut répartition des commandes de la période
 * @param paiements        santé de l'encaissement, par statut et par opérateur
 * @param meilleursArticles top ventes de la période
 * @param meilleursClients top clients de la période
 * @param caParCategorie   répartition du chiffre d'affaires
 * @param clients          effectifs clients
 * @param alertes          ce qui demande une action immédiate
 */
public record TableauDeBordDto(
        Periode periode,
        Ventes ventes,
        Aujourdhui aujourdhui,
        List<PointJour> serie,
        Map<String, Long> commandesParStatut,
        Paiements paiements,
        List<ArticleVenteDto> meilleursArticles,
        List<ClientVente> meilleursClients,
        List<CategorieVente> caParCategorie,
        Clients clients,
        Alertes alertes
) {

    /** @param nombreJours longueur de la période, bornes incluses */
    public record Periode(LocalDate debut, LocalDate fin, long nombreJours) {}

    /**
     * @param chiffreAffaires      encaissé sur la période
     * @param nombreCommandesPayees commandes effectivement réglées
     * @param nombreCommandesCreees commandes créées, réglées ou non
     * @param panierMoyen          chiffre d'affaires / commandes payées
     * @param nombreArticlesVendus quantité totale d'articles
     * @param tauxConversion       part des commandes créées qui ont été payées, en %
     * @param evolutionChiffreAffaires variation vs période précédente de même durée, en %
     * @param evolutionCommandes   idem pour le nombre de commandes payées, en %
     */
    public record Ventes(
            BigDecimal chiffreAffaires,
            long nombreCommandesPayees,
            long nombreCommandesCreees,
            BigDecimal panierMoyen,
            long nombreArticlesVendus,
            BigDecimal tauxConversion,
            BigDecimal evolutionChiffreAffaires,
            BigDecimal evolutionCommandes,
            BigDecimal chiffreAffairesPeriodePrecedente
    ) {}

    public record Aujourdhui(
            LocalDate date,
            BigDecimal chiffreAffaires,
            long nombreCommandes,
            long nombreCommandesPayees,
            long nouveauxClients
    ) {}

    /** Un jour de la série temporelle. Les jours sans activité valent zéro. */
    public record PointJour(
            LocalDate jour,
            BigDecimal chiffreAffaires,
            long nombrePaiementsReussis,
            long nombreCommandes
    ) {}

    /**
     * @param parStatut      répartition des tentatives
     * @param parFournisseur performance par opérateur
     * @param motifsEchec    codes d'erreur les plus fréquents, du plus au moins courant
     * @param tauxReussiteGlobal en %
     */
    public record Paiements(
            Map<String, Long> parStatut,
            List<Fournisseur> parFournisseur,
            Map<String, Long> motifsEchec,
            BigDecimal tauxReussiteGlobal
    ) {}

    public record Fournisseur(
            FournisseurPaiement fournisseur,
            String libelle,
            long total,
            long reussis,
            long echoues,
            long expires,
            BigDecimal tauxReussite,
            BigDecimal montantEncaisse
    ) {}

    public record ClientVente(
            Long idClient,
            String nom,
            String email,
            long nombreCommandes,
            BigDecimal totalDepense
    ) {}

    public record CategorieVente(
            Long idCategorie,
            String nomCategorie,
            BigDecimal chiffreAffaires,
            long quantiteVendue
    ) {}

    public record Clients(
            long total,
            long actifs,
            long bloques,
            long nouveauxSurPeriode
    ) {}

    /**
     * Ce qui demande une action humaine maintenant. C'est la partie du
     * tableau de bord que l'on regarde en arrivant le matin.
     *
     * @param commandesAPreparer commandes payées en attente d'expédition
     * @param paiementsEnCours   tentatives encore en vol
     * @param paiementsBloques   tentatives en vol dont le délai est dépassé
     * @param articlesEnRupture  stock à zéro
     * @param articlesStockFaible nombre d'articles sous le seuil
     * @param seuilStockFaible   seuil appliqué
     * @param stockFaible        détail des articles concernés
     */
    public record Alertes(
            long commandesAPreparer,
            long paiementsEnCours,
            long paiementsBloques,
            long articlesEnRupture,
            long articlesStockFaible,
            int seuilStockFaible,
            List<ArticleStock> stockFaible
    ) {}

    public record ArticleStock(
            Long idArticle,
            String libelle,
            Integer stock,
            String nomCategorie
    ) {}
}
