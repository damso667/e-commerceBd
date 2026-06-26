package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.*;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Models.*;
import com.example.E_commerce.Repository.*;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ArticleRepository articleRepository;
    private final CategorieRepository categorieRepository;
    private final CommandeRepository commandeRepository;
    private  final PersonneRepository personneRepository;
    private  final AvisRepository avisRepository;
    private  final PaiementRepository paiementRepository;
    private final LigneCommandeRepository ligneCommandeRepository;

    // Voir tous les articles
    public List<ArticleResponseDto> tousLesArticles() {
        return articleRepository.findAll()
                .stream()
                .map(ArticleResponseDto::of)
                .toList();
    }

    // Ajouter un article
    public ArticleResponseDto ajouterArticle(ArticleRequestDto dto) {
        Categorie categorie = categorieRepository.findById(dto.getIdCategorie())
                .orElseThrow(() -> new IllegalArgumentException("Categorie introuvable"));

        Article article = new Article();
        article.setLibelle(dto.getLibelle());
        article.setDescription(dto.getDescription());
        article.setPrix(dto.getPrix());
        article.setStock(dto.getStock());
        article.setImageUrl(dto.getImageUrl());
        article.setCategorie(categorie);

        return ArticleResponseDto.of(articleRepository.save(article));
    }

    // Modifier un article
    public ArticleResponseDto modifierArticle(Long idArticle, ArticleRequestDto dto) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable"));

        Categorie categorie = categorieRepository.findById(dto.getIdCategorie())
                .orElseThrow(() -> new IllegalArgumentException("Categorie introuvable"));

        article.setLibelle(dto.getLibelle());
        article.setDescription(dto.getDescription());
        article.setPrix(dto.getPrix());
        article.setStock(dto.getStock());
        article.setImageUrl(dto.getImageUrl());
        article.setCategorie(categorie);

        return ArticleResponseDto.of(articleRepository.save(article));
    }

    // Supprimer un article
    public String supprimerArticle(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable"));
        articleRepository.delete(article);
        return "Article supprimé avec succès";
    }

    public String modifierCategorie(Long idCategorie, Categorie categorie) {
        Categorie existingCategorie = categorieRepository.findById(idCategorie)
                .orElseThrow(() -> new IllegalArgumentException("Categorie introuvable"));

        existingCategorie.setNomCategorie(categorie.getNomCategorie());
        existingCategorie.setDescription(categorie.getDescription());

        categorieRepository.save(existingCategorie);
        return "Categorie modifiée avec succès";
    }

    public List<CategorieResponseDto> toutesLesCategories() {
        return categorieRepository.findAll()
                .stream()
                .map(CategorieResponseDto::of)
                .toList();
    }

    public CategorieResponseDto ajouterCategorie(CategorieRequestDto dto) {
        Categorie categorie = new Categorie();
        categorie.setNomCategorie(dto.getNomCategorie());
        categorie.setDescription(dto.getDescription());
        return CategorieResponseDto.of(categorieRepository.save(categorie));
    }

    public CategorieResponseDto modifierCategorie(Long idCategorie, CategorieRequestDto dto) {
        Categorie categorie = categorieRepository.findById(idCategorie)
                .orElseThrow(() -> new IllegalArgumentException("Categorie introuvable"));
        categorie.setNomCategorie(dto.getNomCategorie());
        categorie.setDescription(dto.getDescription());
        return CategorieResponseDto.of(categorieRepository.save(categorie));
    }

    public void supprimerCategorie(Long idCategorie) {
        Categorie categorie = categorieRepository.findById(idCategorie)
                .orElseThrow(() -> new IllegalArgumentException("Categorie introuvable"));
        categorieRepository.delete(categorie);
    }

    public List<CommandeResponseDto> toutesLesCommandes() {
        return commandeRepository.findAll()
                .stream()
                .map(CommandeResponseDto::of)
                .toList();
    }

    public CommandeResponseDto detailCommande(Long idCommande) {
        Commande commande = commandeRepository.findById(idCommande)
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable"));
        return CommandeResponseDto.of(commande);
    }

    public CommandeResponseDto changerStatutCommande(Long idCommande, String statut) {
        Commande commande = commandeRepository.findById(idCommande)
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable"));
        commande.setStatut(StatutCommande.valueOf(statut));
        return CommandeResponseDto.of(commandeRepository.save(commande));
    }

    public List<PersonneResponseDto> tousLesClients() {
        return personneRepository.findAll()
                .stream()//stream() permet de créer un flux de données à partir d'une collection, ce qui permet d'appliquer des opérations de transformation et de filtrage sur les éléments de la collection.
                .filter(p -> p.getRole() == Role.USER)
                .map(PersonneResponseDto::of)
                .toList();
    }

    public PersonneResponseDto detailClient(Long idClient) {
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));
        return PersonneResponseDto.of(client);
    }

    public PersonneResponseDto bloquerClient(Long idClient) {
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));
        client.setActif(!client.isActif());
        return PersonneResponseDto.of(personneRepository.save(client));
    }

    public List<AvisResponseDto> tousLesAvis() {
        return avisRepository.findAll()
                .stream()
                .map(AvisResponseDto::of)
                .toList();
    }

    public void supprimerAvis(Long idAvis) {
        Avis avis = avisRepository.findById(idAvis)
                .orElseThrow(() -> new IllegalArgumentException("Avis introuvable"));
        avisRepository.delete(avis);
    }

    // Chiffre d'affaires total
    public BigDecimal chiffreAffairesTotal() {
        return paiementRepository.findAll()
                .stream()
                .filter(p -> p.getStatutPaiement() == StatutPaiement.PAYE)
                .map(Paiement::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Nombre de commandes par statut
    public Map<String, Long> commandesParStatut() {
        return commandeRepository.findAll()
                .stream()
                .collect(Collectors.groupingBy(
                        c -> c.getStatut().name(),
                        Collectors.counting()
                ));
    }

    // Articles les plus vendus
    public List<ArticleVenteDto> articlesLesPlusVendus() {
        return ligneCommandeRepository.findAll()
                .stream()
                .collect(Collectors.groupingBy(
                        l -> l.getArticle(),
                        Collectors.summingInt(LigneCommande::getQuantite)
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<Article, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> ArticleVenteDto.of(e.getKey(), e.getValue()))
                .toList();
    }

    // Nombre total de clients
    public Long nombreTotalClients() {
        return personneRepository.findAll()
                .stream()
                .filter(p -> p.getRole() == Role.USER)
                .count();
    }

    public StatistiquesDto tableauDeBord() {
        StatistiquesDto stats = new StatistiquesDto();
        stats.setChiffreAffairesTotal(chiffreAffairesTotal());
        stats.setNombreTotalClients(nombreTotalClients());
        stats.setCommandesParStatut(commandesParStatut());
        stats.setArticlesLesPlusVendus(articlesLesPlusVendus());
        return stats;
    }
}
