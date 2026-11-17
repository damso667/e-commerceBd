package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.*;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Exception.RegleMetierException;
import com.example.E_commerce.Exception.RequeteInvalideException;
import com.example.E_commerce.Exception.RessourceIntrouvableException;
import com.example.E_commerce.Models.*;
import com.example.E_commerce.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Back-office.
 *
 * <p>Les statistiques sont desormais calculees par la base (GROUP BY, SUM)
 * et non plus par des {@code findAll()} suivis de streams : la version
 * precedente ramenait toutes les commandes, tous les paiements et toutes
 * les lignes en memoire a chaque affichage du tableau de bord.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int TOP_VENTES = 10;

    private final ArticleRepository articleRepository;
    private final CategorieRepository categorieRepository;
    private final CommandeRepository commandeRepository;
    private final PersonneRepository personneRepository;
    private final AvisRepository avisRepository;
    private final PaiementRepository paiementRepository;
    private final LigneCommandeRepository ligneCommandeRepository;

    // ================= Articles =================

    @Transactional(readOnly = true)
    public List<ArticleResponseDto> tousLesArticles() {
        return articleRepository.chargerTousAvecCategorie()
                .stream()
                .map(ArticleResponseDto::of)
                .toList();
    }

    @Transactional
    public ArticleResponseDto ajouterArticle(ArticleRequestDto dto) {
        valider(dto);
        Article article = new Article();
        appliquer(dto, article);
        return ArticleResponseDto.of(articleRepository.save(article));
    }

    @Transactional
    public ArticleResponseDto modifierArticle(Long idArticle, ArticleRequestDto dto) {
        valider(dto);
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));
        appliquer(dto, article);
        return ArticleResponseDto.of(articleRepository.save(article));
    }

    /**
     * Un article deja commande n'est pas supprime : on le met a zero en stock.
     * Le supprimer casserait l'historique des commandes qui le referencent.
     */
    @Transactional
    public String supprimerArticle(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));

        if (ligneCommandeRepository.existsByArticleId(idArticle)) {
            article.setStock(0);
            articleRepository.save(article);
            return "Article deja commande : il a ete retire de la vente (stock 0) "
                 + "plutot que supprime, pour preserver l'historique des commandes";
        }
        articleRepository.delete(article);
        return "Article supprime";
    }

    private void valider(ArticleRequestDto dto) {
        if (dto.getPrix() == null || dto.getPrix().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RequeteInvalideException("Le prix doit etre strictement positif");
        }
        if (dto.getStock() == null || dto.getStock() < 0) {
            throw new RequeteInvalideException("Le stock ne peut pas etre negatif");
        }
    }

    private void appliquer(ArticleRequestDto dto, Article article) {
        Categorie categorie = categorieRepository.findById(dto.getIdCategorie())
                .orElseThrow(() -> RessourceIntrouvableException.de("Categorie", dto.getIdCategorie()));
        article.setLibelle(dto.getLibelle());
        article.setDescription(dto.getDescription());
        article.setPrix(dto.getPrix());
        article.setStock(dto.getStock());
        article.setImageUrl(dto.getImageUrl());
        article.setCategorie(categorie);
    }

    // ================= Categories =================

    @Transactional(readOnly = true)
    public List<CategorieResponseDto> toutesLesCategories() {
        return categorieRepository.findAll().stream().map(CategorieResponseDto::of).toList();
    }

    @Transactional
    public CategorieResponseDto ajouterCategorie(CategorieRequestDto dto) {
        categorieRepository.findByNomCategorie(dto.getNomCategorie()).ifPresent(c -> {
            throw new RegleMetierException("Cette categorie existe deja");
        });
        Categorie categorie = new Categorie();
        categorie.setNomCategorie(dto.getNomCategorie());
        categorie.setDescription(dto.getDescription());
        return CategorieResponseDto.of(categorieRepository.save(categorie));
    }

    @Transactional
    public CategorieResponseDto modifierCategorie(Long idCategorie, CategorieRequestDto dto) {
        Categorie categorie = categorieRepository.findById(idCategorie)
                .orElseThrow(() -> RessourceIntrouvableException.de("Categorie", idCategorie));
        categorie.setNomCategorie(dto.getNomCategorie());
        categorie.setDescription(dto.getDescription());
        return CategorieResponseDto.of(categorieRepository.save(categorie));
    }

    @Transactional
    public void supprimerCategorie(Long idCategorie) {
        Categorie categorie = categorieRepository.findById(idCategorie)
                .orElseThrow(() -> RessourceIntrouvableException.de("Categorie", idCategorie));
        if (!articleRepository.findByCategorieIdCategorie(idCategorie).isEmpty()) {
            throw new RegleMetierException(
                    "Cette categorie contient encore des articles : videz-la d'abord");
        }
        categorieRepository.delete(categorie);
    }

    // ================= Commandes =================

    @Transactional(readOnly = true)
    public List<CommandeResponseDto> toutesLesCommandes() {
        return commandeRepository.chargerToutesCompletes()
                .stream()
                .map(CommandeResponseDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommandeResponseDto detailCommande(Long idCommande) {
        return CommandeResponseDto.of(commandeRepository.chargerComplete(idCommande)
                .orElseThrow(() -> RessourceIntrouvableException.de("Commande", idCommande)));
    }

    /**
     * Changement de statut controle.
     *
     * <p>L'ancienne version faisait un {@code StatutCommande.valueOf(statut)}
     * brut : une valeur inconnue produisait une 500, et il etait possible de
     * marquer LIVREE une commande jamais payee.</p>
     */
    @Transactional
    public CommandeResponseDto changerStatutCommande(Long idCommande, String statutDemande) {
        Commande commande = commandeRepository.chargerComplete(idCommande)
                .orElseThrow(() -> RessourceIntrouvableException.de("Commande", idCommande));

        StatutCommande cible;
        try {
            cible = StatutCommande.valueOf(statutDemande.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RequeteInvalideException("Statut inconnu : " + statutDemande
                    + ". Valeurs possibles : " + List.of(StatutCommande.values()));
        }

        StatutCommande actuel = commande.getStatut();
        if (!transitionAutorisee(actuel, cible)) {
            throw new RegleMetierException(
                    "Transition interdite : " + actuel + " -> " + cible);
        }
        commande.setStatut(cible);
        return CommandeResponseDto.of(commandeRepository.save(commande));
    }

    /**
     * EN_ATTENTE -> PAYEE est volontairement absent : seul un paiement
     * confirme fait passer une commande a PAYEE, jamais un clic d'admin.
     */
    private boolean transitionAutorisee(StatutCommande actuel, StatutCommande cible) {
        if (actuel == cible) {
            return false;
        }
        return switch (actuel) {
            case EN_ATTENTE -> cible == StatutCommande.ANNULEE;
            case PAYEE      -> cible == StatutCommande.EN_COURS || cible == StatutCommande.ANNULEE;
            case EN_COURS   -> cible == StatutCommande.LIVREE || cible == StatutCommande.ANNULEE;
            default         -> false;
        };
    }

    // ================= Clients =================

    @Transactional(readOnly = true)
    public List<PersonneResponseDto> tousLesClients() {
        return personneRepository.findByRole(Role.USER)
                .stream()
                .map(PersonneResponseDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonneResponseDto detailClient(Long idClient) {
        return PersonneResponseDto.of(personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Client", idClient)));
    }

    /** Active ou desactive un compte client. Un admin n'est pas blocable ici. */
    @Transactional
    public PersonneResponseDto basculerActivationClient(Long idClient) {
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Client", idClient));
        if (client.getRole() == Role.ADMIN) {
            throw new RegleMetierException("Un compte administrateur ne peut pas etre bloque ici");
        }
        client.setActif(!client.isActif());
        return PersonneResponseDto.of(personneRepository.save(client));
    }

    // ================= Avis =================

    @Transactional(readOnly = true)
    public List<AvisResponseDto> tousLesAvis() {
        return avisRepository.chargerTous().stream().map(AvisResponseDto::of).toList();
    }

    @Transactional
    public void supprimerAvis(Long idAvis) {
        Avis avis = avisRepository.findById(idAvis)
                .orElseThrow(() -> RessourceIntrouvableException.de("Avis", idAvis));
        avisRepository.delete(avis);
    }

    // ================= Statistiques =================

    @Transactional(readOnly = true)
    public StatistiquesDto tableauDeBord() {
        StatistiquesDto stats = new StatistiquesDto();
        stats.setChiffreAffairesTotal(chiffreAffairesTotal());
        stats.setNombreTotalClients(personneRepository.countByRole(Role.USER));
        stats.setCommandesParStatut(commandesParStatut());
        stats.setArticlesLesPlusVendus(articlesLesPlusVendus());
        return stats;
    }

    @Transactional(readOnly = true)
    public BigDecimal chiffreAffairesTotal() {
        BigDecimal total = paiementRepository.chiffreAffaires();
        return total == null ? BigDecimal.ZERO : total;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> commandesParStatut() {
        Map<String, Long> resultat = new LinkedHashMap<>();
        // Tous les statuts apparaissent, meme a zero : le front n'a pas a
        // gerer les cles manquantes.
        for (StatutCommande statut : StatutCommande.values()) {
            resultat.put(statut.name(), 0L);
        }
        for (Object[] ligne : commandeRepository.compterParStatut()) {
            resultat.put(((StatutCommande) ligne[0]).name(), ((Number) ligne[1]).longValue());
        }
        return resultat;
    }

    @Transactional(readOnly = true)
    public List<ArticleVenteDto> articlesLesPlusVendus() {
        List<ArticleVenteDto> resultat = new ArrayList<>();
        for (Object[] ligne : ligneCommandeRepository.meilleuresVentes(PageRequest.of(0, TOP_VENTES))) {
            ArticleVenteDto dto = new ArticleVenteDto();
            dto.setIdArticle(((Number) ligne[0]).longValue());
            dto.setLibelle((String) ligne[1]);
            dto.setQuantiteTotaleVendue(((Number) ligne[2]).intValue());
            dto.setChiffreAffaires((BigDecimal) ligne[3]);
            resultat.add(dto);
        }
        return resultat;
    }
}
