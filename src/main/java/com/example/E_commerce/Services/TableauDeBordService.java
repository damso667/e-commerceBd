package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.ArticleVenteDto;
import com.example.E_commerce.Dto.TableauDeBordDto;
import com.example.E_commerce.Dto.TableauDeBordDto.*;
import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Exception.RequeteInvalideException;
import com.example.E_commerce.Repository.PersonneRepository;
import com.example.E_commerce.Repository.StatistiquesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construction du tableau de bord.
 *
 * <p>Aucune agrégation n'est faite en Java : chaque chiffre vient d'une
 * requête SQL qui ne renvoie que des totaux. Le service ne fait que
 * l'assemblage, le calcul des pourcentages et le remplissage des jours
 * creux de la série.</p>
 */
@Service
@RequiredArgsConstructor
public class TableauDeBordService {

    /** Période par défaut quand l'appelant n'en donne pas. */
    private static final int JOURS_PAR_DEFAUT = 30;

    /** Au-delà, la série temporelle devient illisible et la requête lourde. */
    private static final int JOURS_MAX = 366;

    private static final int TAILLE_PALMARES = 10;

    private final StatistiquesRepository statistiques;
    private final PersonneRepository personneRepository;

    @Value("${app.admin.seuil-stock-faible:5}")
    private int seuilStockFaible;

    @Transactional(readOnly = true)
    public TableauDeBordDto construire(LocalDate debutDemande, LocalDate finDemande) {

        ZoneId zone = ZoneId.systemDefault();
        LocalDate aujourdhui = LocalDate.now(zone);

        LocalDate fin = finDemande == null ? aujourdhui : finDemande;
        LocalDate debut = debutDemande == null ? fin.minusDays(JOURS_PAR_DEFAUT - 1L) : debutDemande;

        if (debut.isAfter(fin)) {
            throw new RequeteInvalideException(
                    "La date de debut (" + debut + ") est posterieure a la date de fin (" + fin + ")");
        }
        long nombreJours = ChronoUnit.DAYS.between(debut, fin) + 1;
        if (nombreJours > JOURS_MAX) {
            throw new RequeteInvalideException(
                    "Periode trop longue : " + nombreJours + " jours (maximum " + JOURS_MAX + ")");
        }

        Instant debutInstant = debut.atStartOfDay(zone).toInstant();
        Instant finInstant = fin.plusDays(1).atStartOfDay(zone).toInstant();

        // Période précédente de même durée, pour les évolutions.
        LocalDate debutPrecedent = debut.minusDays(nombreJours);
        Instant debutPrecedentInstant = debutPrecedent.atStartOfDay(zone).toInstant();

        return new TableauDeBordDto(
                new Periode(debut, fin, nombreJours),
                ventes(debutInstant, finInstant, debutPrecedentInstant),
                aujourdhui(aujourdhui, zone),
                serie(debut, fin, debutInstant, finInstant),
                commandesParStatut(debutInstant, finInstant),
                paiements(debutInstant, finInstant),
                meilleursArticles(debutInstant, finInstant),
                meilleursClients(debutInstant, finInstant),
                caParCategorie(debutInstant, finInstant),
                clients(debutInstant, finInstant),
                alertes());
    }

    // ==================================================================

    private Ventes ventes(Instant debut, Instant fin, Instant debutPrecedent) {
        BigDecimal ca = zero(statistiques.chiffreAffaires(debut, fin));
        long payees = statistiques.nombreCommandesPayees(debut, fin);
        long creees = statistiques.nombreCommandesCreees(debut, fin);
        Long articlesVendus = statistiques.nombreArticlesVendus(debut, fin);
        long articles = articlesVendus == null ? 0L : articlesVendus;

        BigDecimal caPrecedent = zero(statistiques.chiffreAffaires(debutPrecedent, debut));
        long payeesPrecedent = statistiques.nombreCommandesPayees(debutPrecedent, debut);

        return new Ventes(
                ca,
                payees,
                creees,
                payees == 0 ? BigDecimal.ZERO
                            : ca.divide(BigDecimal.valueOf(payees), 2, RoundingMode.HALF_UP),
                articles,
                pourcentage(payees, creees),
                evolution(ca, caPrecedent),
                evolution(BigDecimal.valueOf(payees), BigDecimal.valueOf(payeesPrecedent)),
                caPrecedent);
    }

    private Aujourdhui aujourdhui(LocalDate jour, ZoneId zone) {
        Instant debut = jour.atStartOfDay(zone).toInstant();
        Instant fin = jour.plusDays(1).atStartOfDay(zone).toInstant();
        return new Aujourdhui(
                jour,
                zero(statistiques.chiffreAffairesDuJour(jour)),
                statistiques.nombreCommandesCreees(debut, fin),
                statistiques.nombreCommandesPayees(debut, fin),
                statistiques.nouveauxClients(debut, fin));
    }

    /**
     * Série jour par jour. Les jours sans vente sont remplis à zéro : sans
     * cela une courbe front relie le 3 au 7 en ignorant le creux, ce qui
     * donne une lecture fausse.
     */
    private List<PointJour> serie(LocalDate debut, LocalDate fin, Instant debutInstant, Instant finInstant) {

        Map<LocalDate, BigDecimal> caParJour = new HashMap<>();
        Map<LocalDate, Long> paiementsParJour = new HashMap<>();
        for (Object[] ligne : statistiques.chiffreAffairesParJour(debutInstant, finInstant)) {
            LocalDate jour = versDate(ligne[0]);
            caParJour.put(jour, (BigDecimal) ligne[1]);
            paiementsParJour.put(jour, ((Number) ligne[2]).longValue());
        }

        Map<LocalDate, Long> commandesParJour = new HashMap<>();
        for (Object[] ligne : statistiques.commandesParJour(debutInstant, finInstant)) {
            commandesParJour.put(versDate(ligne[0]), ((Number) ligne[1]).longValue());
        }

        List<PointJour> points = new ArrayList<>();
        for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
            points.add(new PointJour(
                    jour,
                    caParJour.getOrDefault(jour, BigDecimal.ZERO),
                    paiementsParJour.getOrDefault(jour, 0L),
                    commandesParJour.getOrDefault(jour, 0L)));
        }
        return points;
    }

    private Map<String, Long> commandesParStatut(Instant debut, Instant fin) {
        Map<String, Long> resultat = new LinkedHashMap<>();
        for (StatutCommande statut : StatutCommande.values()) {
            resultat.put(statut.name(), 0L);
        }
        for (Object[] ligne : statistiques.commandesParStatut(debut, fin)) {
            resultat.put(((StatutCommande) ligne[0]).name(), ((Number) ligne[1]).longValue());
        }
        return resultat;
    }

    private Paiements paiements(Instant debut, Instant fin) {

        Map<String, Long> parStatut = new LinkedHashMap<>();
        for (StatutPaiement statut : StatutPaiement.values()) {
            parStatut.put(statut.name(), 0L);
        }
        for (Object[] ligne : statistiques.paiementsParStatut(debut, fin)) {
            parStatut.put(((StatutPaiement) ligne[0]).name(), ((Number) ligne[1]).longValue());
        }

        List<Fournisseur> parFournisseur = new ArrayList<>();
        long totalGlobal = 0;
        long reussisGlobal = 0;
        for (Object[] ligne : statistiques.paiementsParFournisseur(debut, fin)) {
            FournisseurPaiement fournisseur = (FournisseurPaiement) ligne[0];
            long total = ((Number) ligne[1]).longValue();
            long reussis = ((Number) ligne[2]).longValue();
            long echoues = ((Number) ligne[3]).longValue();
            long expires = ((Number) ligne[4]).longValue();
            totalGlobal += total;
            reussisGlobal += reussis;
            parFournisseur.add(new Fournisseur(
                    fournisseur, fournisseur.getLibelle(), total, reussis, echoues, expires,
                    pourcentage(reussis, total), (BigDecimal) ligne[5]));
        }

        Map<String, Long> motifs = new LinkedHashMap<>();
        for (Object[] ligne : statistiques.motifsEchec(debut, fin)) {
            motifs.put((String) ligne[0], ((Number) ligne[1]).longValue());
        }

        return new Paiements(parStatut, parFournisseur, motifs,
                pourcentage(reussisGlobal, totalGlobal));
    }

    private List<ArticleVenteDto> meilleursArticles(Instant debut, Instant fin) {
        List<ArticleVenteDto> resultat = new ArrayList<>();
        for (Object[] ligne : statistiques.meilleursArticles(debut, fin,
                PageRequest.of(0, TAILLE_PALMARES))) {
            ArticleVenteDto dto = new ArticleVenteDto();
            dto.setIdArticle(((Number) ligne[0]).longValue());
            dto.setLibelle((String) ligne[1]);
            dto.setQuantiteTotaleVendue(((Number) ligne[2]).intValue());
            dto.setChiffreAffaires((BigDecimal) ligne[3]);
            resultat.add(dto);
        }
        return resultat;
    }

    private List<ClientVente> meilleursClients(Instant debut, Instant fin) {
        List<ClientVente> resultat = new ArrayList<>();
        for (Object[] ligne : statistiques.meilleursClients(debut, fin,
                PageRequest.of(0, TAILLE_PALMARES))) {
            resultat.add(new ClientVente(
                    ((Number) ligne[0]).longValue(),
                    (String) ligne[1],
                    (String) ligne[2],
                    ((Number) ligne[3]).longValue(),
                    (BigDecimal) ligne[4]));
        }
        return resultat;
    }

    private List<CategorieVente> caParCategorie(Instant debut, Instant fin) {
        List<CategorieVente> resultat = new ArrayList<>();
        for (Object[] ligne : statistiques.chiffreAffairesParCategorie(debut, fin)) {
            resultat.add(new CategorieVente(
                    ((Number) ligne[0]).longValue(),
                    (String) ligne[1],
                    (BigDecimal) ligne[2],
                    ((Number) ligne[3]).longValue()));
        }
        return resultat;
    }

    private Clients clients(Instant debut, Instant fin) {
        long actifs = statistiques.compterClientsParActivation(true);
        long bloques = statistiques.compterClientsParActivation(false);
        return new Clients(
                personneRepository.countByRole(Role.USER),
                actifs,
                bloques,
                statistiques.nouveauxClients(debut, fin));
    }

    private Alertes alertes() {
        List<ArticleStock> stockFaible = new ArrayList<>();
        for (Object[] ligne : statistiques.stockFaible(seuilStockFaible, PageRequest.of(0, 20))) {
            stockFaible.add(new ArticleStock(
                    ((Number) ligne[0]).longValue(),
                    (String) ligne[1],
                    ligne[2] == null ? null : ((Number) ligne[2]).intValue(),
                    (String) ligne[3]));
        }
        return new Alertes(
                statistiques.commandesAPreparer(),
                statistiques.paiementsEnCours(),
                statistiques.paiementsBloques(Instant.now()),
                statistiques.compterRuptures(),
                statistiques.compterStockFaible(seuilStockFaible),
                seuilStockFaible,
                stockFaible);
    }

    // ==================================================================
    //  Utilitaires
    // ==================================================================

    private static BigDecimal zero(BigDecimal valeur) {
        return valeur == null ? BigDecimal.ZERO : valeur;
    }

    /** part / total en pourcentage, 0 si le total est nul. */
    private static BigDecimal pourcentage(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /**
     * Évolution en pourcentage. Si la période précédente était à zéro, on
     * renvoie null plutôt que 0 ou l'infini : « +∞ % » n'est pas une
     * information, « pas de point de comparaison » en est une.
     */
    private static BigDecimal evolution(BigDecimal actuel, BigDecimal precedent) {
        if (precedent == null || precedent.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return actuel.subtract(precedent)
                .multiply(BigDecimal.valueOf(100))
                .divide(precedent, 1, RoundingMode.HALF_UP);
    }

    /** JPQL peut rendre une date en java.sql.Date ou en LocalDate selon le pilote. */
    private static LocalDate versDate(Object valeur) {
        if (valeur instanceof LocalDate date) {
            return date;
        }
        if (valeur instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (valeur instanceof java.time.temporal.TemporalAccessor temporal) {
            return LocalDate.from(temporal);
        }
        return LocalDate.parse(String.valueOf(valeur));
    }
}
