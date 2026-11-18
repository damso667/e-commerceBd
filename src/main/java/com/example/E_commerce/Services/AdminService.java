package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.*;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Exception.AccesRefuseException;
import com.example.E_commerce.Exception.RegleMetierException;
import com.example.E_commerce.Exception.RequeteInvalideException;
import com.example.E_commerce.Exception.RessourceIntrouvableException;
import com.example.E_commerce.Models.*;
import com.example.E_commerce.Repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Back-office : catalogue, commandes, clients, modération.
 *
 * <p>Trois principes appliqués partout ici :</p>
 * <ul>
 *   <li><b>tout est paginé</b> — aucune liste ne peut grossir sans limite ;</li>
 *   <li><b>tout changement est tracé</b> via {@link JournalAdminService} ;</li>
 *   <li><b>les agrégats sont calculés par la base</b>, jamais par des
 *       {@code findAll()} suivis de streams.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final int TOP_VENTES = 10;
    private static final int DERNIERES_COMMANDES = 10;

    private final ArticleRepository articleRepository;
    private final CategorieRepository categorieRepository;
    private final CommandeRepository commandeRepository;
    private final PersonneRepository personneRepository;
    private final PagnierRepository pagnierRepository;
    private final AvisRepository avisRepository;
    private final PaiementRepository paiementRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final JournalAdminService journal;

    // ==================================================================
    //  Articles
    // ==================================================================

    @Transactional(readOnly = true)
    public PageReponse<ArticleResponseDto> rechercherArticles(String recherche, Long idCategorie,
                                                              Integer stockMax, BigDecimal prixMin,
                                                              BigDecimal prixMax, Pageable pageable) {
        return PageReponse.de(
                articleRepository.findAll(
                        Recherches.articles(idCategorie, stockMax, prixMin, prixMax, recherche),
                        pageable),
                ArticleResponseDto::of);
    }

    /** Conservé pour compatibilité : liste complète, sans pagination. */
    @Transactional(readOnly = true)
    public List<ArticleResponseDto> tousLesArticles() {
        return articleRepository.chargerTousAvecCategorie()
                .stream()
                .map(ArticleResponseDto::of)
                .toList();
    }

    @Transactional
    public ArticleResponseDto ajouterArticle(ArticleRequestDto dto) {
        Article article = new Article();
        appliquer(dto, article);
        Article enregistre = articleRepository.save(article);
        journal.tracer("ARTICLE_CREE", JournalAdminService.ARTICLE, enregistre.getId(),
                dto.getLibelle() + " | prix " + dto.getPrix() + " | stock " + dto.getStock());
        return ArticleResponseDto.of(enregistre);
    }

    @Transactional
    public ArticleResponseDto modifierArticle(Long idArticle, ArticleRequestDto dto) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));

        // On note ce qui change vraiment : un journal illisible ne sert à rien.
        List<String> changements = new ArrayList<>();
        if (!java.util.Objects.equals(article.getPrix(), dto.getPrix())) {
            changements.add("prix " + article.getPrix() + " -> " + dto.getPrix());
        }
        if (!java.util.Objects.equals(article.getStock(), dto.getStock())) {
            changements.add("stock " + article.getStock() + " -> " + dto.getStock());
        }
        if (!java.util.Objects.equals(article.getLibelle(), dto.getLibelle())) {
            changements.add("libelle " + article.getLibelle() + " -> " + dto.getLibelle());
        }

        appliquer(dto, article);
        Article enregistre = articleRepository.save(article);
        journal.tracer("ARTICLE_MODIFIE", JournalAdminService.ARTICLE, idArticle,
                changements.isEmpty() ? "aucun champ cle modifie" : String.join(" | ", changements));
        return ArticleResponseDto.of(enregistre);
    }

    /**
     * Ajustement de stock par variation.
     *
     * <p>Le verrou pessimiste rend deux réapprovisionnements simultanés
     * additifs au lieu de concurrents : sans lui, deux « +10 » partis
     * ensemble donneraient +10 au lieu de +20.</p>
     */
    @Transactional
    public ArticleResponseDto ajusterStock(Long idArticle, AjustementStockDto dto) {
        Article article = articleRepository.verrouillerPourStock(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));

        int avant = article.getStock() == null ? 0 : article.getStock();
        int apres = avant + dto.getVariation();
        if (apres < 0) {
            throw new RegleMetierException("Stock insuffisant : " + avant
                    + " en stock, variation demandee " + dto.getVariation());
        }

        article.setStock(apres);
        Article enregistre = articleRepository.save(article);
        journal.tracer("STOCK_AJUSTE", JournalAdminService.ARTICLE, idArticle,
                avant + " -> " + apres + " (" + (dto.getVariation() >= 0 ? "+" : "")
                + dto.getVariation() + ")"
                + (dto.getMotif() == null ? "" : " | motif : " + dto.getMotif()));
        return ArticleResponseDto.of(enregistre);
    }

    // ------------------------------------------------------------------
    //  Photo de l'article
    // ------------------------------------------------------------------

    /**
     * 1 Mo. Une photo de produit correctement compressee pour le web pese
     * 150 a 250 Ko ; 1 Mo laisse de la marge sans qu'un catalogue de
     * plusieurs centaines d'articles ne fasse enfler la base.
     */
    private static final long TAILLE_MAX_PHOTO = 1024L * 1024L;

    /**
     * Depot d'une photo. Elle remplace l'eventuelle URL externe dans ce que
     * voit le front : c'est la seule image que l'administrateur controle
     * depuis le back-office, et laisser les deux coexister en affichage
     * obligerait a expliquer laquelle gagne.
     */
    @Transactional
    public ArticleResponseDto deposerImage(Long idArticle, MultipartFile fichier) {
        ValidationImage.valider(fichier, TAILLE_MAX_PHOTO);
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));

        boolean remplacement = article.aUneImage();
        article.setImageDonnees(ValidationImage.lire(fichier));
        article.setImageType(fichier.getContentType());
        article.setImageVersion(ValidationImage.nouvelleVersion());

        Article enregistre = articleRepository.save(article);
        journal.tracer("ARTICLE_IMAGE_DEPOSEE", JournalAdminService.ARTICLE, idArticle,
                (remplacement ? "photo remplacee" : "photo ajoutee")
                + " (" + (fichier.getSize() / 1024) + " Ko, " + fichier.getContentType() + ")");
        return ArticleResponseDto.of(enregistre);
    }

    /**
     * Retrait de la photo. L'URL externe eventuelle reprend alors la main ;
     * sans elle, la boutique affiche le visuel de remplacement portant le
     * nom de l'article.
     */
    @Transactional
    public ArticleResponseDto retirerImage(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));
        if (!article.aUneImage()) {
            throw new RegleMetierException("Cet article n'a pas de photo televersee");
        }
        article.setImageDonnees(null);
        article.setImageType(null);
        article.setImageVersion(null);

        Article enregistre = articleRepository.save(article);
        journal.tracer("ARTICLE_IMAGE_RETIREE", JournalAdminService.ARTICLE, idArticle,
                "photo retiree");
        return ArticleResponseDto.of(enregistre);
    }

    /**
     * Un article déjà commandé n'est pas supprimé : on le met à zéro en stock.
     * Le supprimer casserait l'historique des commandes qui le référencent.
     */
    @Transactional
    public String supprimerArticle(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));

        if (ligneCommandeRepository.existsByArticleId(idArticle)) {
            article.setStock(0);
            articleRepository.save(article);
            journal.tracer("ARTICLE_RETIRE_VENTE", JournalAdminService.ARTICLE, idArticle,
                    article.getLibelle() + " : deja commande, stock mis a 0");
            return "Article deja commande : il a ete retire de la vente (stock 0) "
                 + "plutot que supprime, pour preserver l'historique des commandes";
        }
        articleRepository.delete(article);
        journal.tracer("ARTICLE_SUPPRIME", JournalAdminService.ARTICLE, idArticle,
                article.getLibelle());
        return "Article supprime";
    }

    private void appliquer(ArticleRequestDto dto, Article article) {
        Categorie categorie = categorieRepository.findById(dto.getIdCategorie())
                .orElseThrow(() -> RessourceIntrouvableException.de("Categorie", dto.getIdCategorie()));
        article.setLibelle(dto.getLibelle());
        article.setDescription(dto.getDescription());
        article.setPrix(dto.getPrix());
        article.setStock(dto.getStock());
        article.setImageUrl(vide(dto.getImageUrl()));
        article.setCategorie(categorie);
    }

    // ==================================================================
    //  Catégories
    // ==================================================================

    @Transactional(readOnly = true)
    public List<CategorieResponseDto> toutesLesCategories() {
        return categorieRepository.findAll(Sort.by("nomCategorie"))
                .stream().map(CategorieResponseDto::of).toList();
    }

    @Transactional
    public CategorieResponseDto ajouterCategorie(CategorieRequestDto dto) {
        categorieRepository.findByNomCategorie(dto.getNomCategorie()).ifPresent(c -> {
            throw new RegleMetierException("Cette categorie existe deja");
        });
        Categorie categorie = new Categorie();
        categorie.setNomCategorie(dto.getNomCategorie());
        categorie.setDescription(dto.getDescription());
        Categorie enregistree = categorieRepository.save(categorie);
        journal.tracer("CATEGORIE_CREEE", JournalAdminService.CATEGORIE,
                enregistree.getIdCategorie(), dto.getNomCategorie());
        return CategorieResponseDto.of(enregistree);
    }

    @Transactional
    public CategorieResponseDto modifierCategorie(Long idCategorie, CategorieRequestDto dto) {
        Categorie categorie = categorieRepository.findById(idCategorie)
                .orElseThrow(() -> RessourceIntrouvableException.de("Categorie", idCategorie));
        String ancien = categorie.getNomCategorie();
        categorie.setNomCategorie(dto.getNomCategorie());
        categorie.setDescription(dto.getDescription());
        journal.tracer("CATEGORIE_MODIFIEE", JournalAdminService.CATEGORIE, idCategorie,
                ancien + " -> " + dto.getNomCategorie());
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
        journal.tracer("CATEGORIE_SUPPRIMEE", JournalAdminService.CATEGORIE, idCategorie,
                categorie.getNomCategorie());
    }

    // ==================================================================
    //  Commandes
    // ==================================================================

    @Transactional(readOnly = true)
    public PageReponse<CommandeResumeDto> rechercherCommandes(StatutCommande statut, Long idClient,
                                                              LocalDate debut, LocalDate fin,
                                                              BigDecimal montantMin, String recherche,
                                                              Pageable pageable) {
        return PageReponse.de(
                commandeRepository.findAll(
                        Recherches.commandes(statut, idClient, debutDe(debut), finDe(fin),
                                montantMin, recherche),
                        pageable),
                CommandeResumeDto::of);
    }

    /** Conservé pour compatibilité : toutes les commandes, détails inclus. */
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
     * Changement de statut contrôlé par une machine à états.
     *
     * <p>{@code EN_ATTENTE -> PAYEE} est volontairement absent : seul un
     * paiement confirmé fait passer une commande à PAYEE, jamais un clic.</p>
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
            throw new RegleMetierException("Transition interdite : " + actuel + " -> " + cible);
        }

        // Annuler une commande réglée rend le stock : sans cela il reste
        // réservé pour une commande qui ne partira jamais.
        if (cible == StatutCommande.ANNULEE) {
            rendreStock(commande);
        }

        commande.setStatut(cible);
        Commande enregistree = commandeRepository.save(commande);
        journal.tracer("COMMANDE_STATUT_CHANGE", JournalAdminService.COMMANDE, idCommande,
                actuel + " -> " + cible);
        return CommandeResponseDto.of(enregistree);
    }

    private void rendreStock(Commande commande) {
        for (LigneCommande ligne : commande.getLignes()) {
            articleRepository.verrouillerPourStock(ligne.getArticle().getId())
                    .ifPresent(article -> {
                        article.setStock((article.getStock() == null ? 0 : article.getStock())
                                + ligne.getQuantite());
                        articleRepository.save(article);
                    });
        }
        log.info("Commande {} annulee par un administrateur : stock rendu", commande.getId());
    }

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

    // ==================================================================
    //  Clients
    // ==================================================================

    @Transactional(readOnly = true)
    public PageReponse<PersonneResponseDto> rechercherClients(String recherche, Role role,
                                                              Boolean actif, Pageable pageable) {
        return PageReponse.de(
                personneRepository.findAll(Recherches.comptes(role, actif, recherche), pageable),
                PersonneResponseDto::of);
    }

    /** Conservé pour compatibilité : tous les comptes USER. */
    @Transactional(readOnly = true)
    public List<PersonneResponseDto> tousLesClients() {
        return personneRepository.findByRole(Role.USER)
                .stream().map(PersonneResponseDto::of).toList();
    }

    /**
     * Fiche complète : identité, statistiques d'achat, panier en cours et
     * dernières commandes, en un seul appel.
     */
    @Transactional(readOnly = true)
    public ClientDetailDto detailClient(Long idClient) {
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Client", idClient));

        long nombreCommandes = 0;
        long nombrePayees = 0;
        BigDecimal totalDepense = BigDecimal.ZERO;
        Instant derniere = null;

        List<Object[]> stats = commandeRepository.statistiquesClient(idClient);
        if (!stats.isEmpty() && stats.get(0) != null) {
            Object[] ligne = stats.get(0);
            nombreCommandes = ligne[0] == null ? 0 : ((Number) ligne[0]).longValue();
            nombrePayees = ligne[1] == null ? 0 : ((Number) ligne[1]).longValue();
            totalDepense = ligne[2] == null ? BigDecimal.ZERO : (BigDecimal) ligne[2];
            derniere = (Instant) ligne[3];
        }

        List<CommandeResumeDto> dernieresCommandes = commandeRepository
                .findAll(Recherches.commandes(null, idClient, null, null, null, null),
                        PageRequest.of(0, DERNIERES_COMMANDES,
                                Sort.by(Sort.Direction.DESC, "dateCreation")))
                .getContent()
                .stream()
                .map(CommandeResumeDto::of)
                .toList();

        PanierResponseDto panier = pagnierRepository.chargerComplet(idClient)
                .map(PanierResponseDto::of)
                .orElse(null);

        return new ClientDetailDto(
                PersonneResponseDto.of(client),
                client.getDateInscription(),
                nombreCommandes,
                nombrePayees,
                totalDepense,
                nombrePayees == 0 ? BigDecimal.ZERO
                        : totalDepense.divide(BigDecimal.valueOf(nombrePayees), 2, RoundingMode.HALF_UP),
                derniere,
                panier,
                dernieresCommandes);
    }

    /** Active ou désactive un compte client. Un administrateur n'est pas blocable ici. */
    @Transactional
    public PersonneResponseDto basculerActivationClient(Long idClient) {
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Client", idClient));
        if (client.getRole() == Role.ADMIN) {
            throw new RegleMetierException("Un compte administrateur ne peut pas etre bloque ici");
        }
        client.setActif(!client.isActif());
        Personne enregistre = personneRepository.save(client);
        journal.tracer(client.isActif() ? "CLIENT_DEBLOQUE" : "CLIENT_BLOQUE",
                JournalAdminService.CLIENT, idClient, client.getEmail());
        return PersonneResponseDto.of(enregistre);
    }

    /**
     * Promotion ou rétrogradation d'un compte.
     *
     * <p>Deux garde-fous : on ne modifie pas son propre rôle (on se
     * verrouillerait dehors), et on ne retire pas le dernier administrateur
     * de la plateforme.</p>
     */
    @Transactional
    public PersonneResponseDto changerRole(Long idClient, Role nouveauRole, String emailActeur) {
        Personne compte = personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Compte", idClient));

        if (compte.getEmail() != null && compte.getEmail().equalsIgnoreCase(emailActeur)) {
            throw new AccesRefuseException("Vous ne pouvez pas modifier votre propre role");
        }
        Role ancien = compte.getRole();
        if (ancien == nouveauRole) {
            throw new RegleMetierException("Ce compte a deja le role " + nouveauRole);
        }
        if (ancien == Role.ADMIN && personneRepository.countByRole(Role.ADMIN) <= 1) {
            throw new RegleMetierException(
                    "Impossible de retrograder le dernier administrateur de la plateforme");
        }

        compte.setRole(nouveauRole);
        Personne enregistre = personneRepository.save(compte);
        journal.tracer("ROLE_CHANGE", JournalAdminService.CLIENT, idClient,
                compte.getEmail() + " : " + ancien + " -> " + nouveauRole);
        log.warn("Role de {} change : {} -> {} (par {})",
                compte.getEmail(), ancien, nouveauRole, emailActeur);
        return PersonneResponseDto.of(enregistre);
    }

    // ==================================================================
    //  Avis
    // ==================================================================

    @Transactional(readOnly = true)
    public PageReponse<AvisAdminDto> rechercherAvis(Long idArticle, Integer noteMin, Integer noteMax,
                                                    String recherche, Pageable pageable) {
        return PageReponse.de(
                avisRepository.findAll(
                        Recherches.avis(idArticle, noteMin, noteMax, recherche), pageable),
                AvisAdminDto::of);
    }

    /** Conservé pour compatibilité. */
    @Transactional(readOnly = true)
    public List<AvisResponseDto> tousLesAvis() {
        return avisRepository.chargerTous().stream().map(AvisResponseDto::of).toList();
    }

    @Transactional
    public void supprimerAvis(Long idAvis) {
        Avis avis = avisRepository.findById(idAvis)
                .orElseThrow(() -> RessourceIntrouvableException.de("Avis", idAvis));
        journal.tracer("AVIS_SUPPRIME", JournalAdminService.AVIS, idAvis,
                "note " + avis.getNote() + " sur l'article " + avis.getArticle().getId()
                + " par " + avis.getAuteur().getEmail());
        avisRepository.delete(avis);
    }

    // ==================================================================
    //  Statistiques (ancien format, conservé)
    // ==================================================================

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

    // ==================================================================

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static Instant debutDe(LocalDate debut) {
        return debut == null ? null : debut.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    /** Borne haute exclusive : « fin = aujourd'hui » doit inclure la journée. */
    private static Instant finDe(LocalDate fin) {
        return fin == null ? null : fin.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
