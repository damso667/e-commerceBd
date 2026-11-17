package com.example.E_commerce.Controller;

import com.example.E_commerce.Config.UtilisateurCourant;
import com.example.E_commerce.Dto.*;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Services.AdminService;
import com.example.E_commerce.Services.JournalAdminService;
import com.example.E_commerce.Services.TableauDeBordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Back-office.
 *
 * <p>Toutes les listes acceptent la pagination et le tri standard de Spring
 * Data : {@code ?page=0&size=20&sort=dateCreation,desc}. Les anciennes
 * routes non paginées sont conservées sous {@code /tous} pour ne pas casser
 * un front existant, mais elles ne doivent pas être utilisées sur une base
 * qui grossit.</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final TableauDeBordService tableauDeBordService;
    private final JournalAdminService journalAdminService;
    private final UtilisateurCourant utilisateurCourant;

    // ==================================================================
    //  Tableau de bord
    // ==================================================================

    /**
     * Tableau de bord sur une période. Sans paramètre : les 30 derniers jours.
     *
     * <pre>
     *   GET /api/admin/tableau-de-bord
     *   GET /api/admin/tableau-de-bord?debut=2026-11-01&amp;fin=2026-11-17
     * </pre>
     */
    @GetMapping("/tableau-de-bord")
    public ResponseEntity<TableauDeBordDto> tableauDeBord(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(tableauDeBordService.construire(debut, fin));
    }

    /** Ancien tableau de bord, sans dimension temporelle. Conservé. */
    @GetMapping("/statistiques")
    public ResponseEntity<StatistiquesDto> statistiques() {
        return ResponseEntity.ok(adminService.tableauDeBord());
    }

    // ==================================================================
    //  Articles
    // ==================================================================

    @GetMapping("/articles")
    public ResponseEntity<PageReponse<ArticleResponseDto>> rechercherArticles(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Long idCategorie,
            @RequestParam(required = false) Integer stockMax,
            @RequestParam(required = false) BigDecimal prixMin,
            @RequestParam(required = false) BigDecimal prixMax,
            @PageableDefault(size = 20, sort = "libelle") Pageable pageable) {
        return ResponseEntity.ok(adminService.rechercherArticles(
                recherche, idCategorie, stockMax, prixMin, prixMax, pageable));
    }

    @GetMapping("/articles/tous")
    public ResponseEntity<List<ArticleResponseDto>> tousLesArticles() {
        return ResponseEntity.ok(adminService.tousLesArticles());
    }

    @PostMapping("/articles")
    public ResponseEntity<ArticleResponseDto> ajouterArticle(
            @Valid @RequestBody ArticleRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.ajouterArticle(dto));
    }

    @PutMapping("/articles/{idArticle}")
    public ResponseEntity<ArticleResponseDto> modifierArticle(
            @PathVariable Long idArticle, @Valid @RequestBody ArticleRequestDto dto) {
        return ResponseEntity.ok(adminService.modifierArticle(idArticle, dto));
    }

    /** Réapprovisionnement ou correction d'inventaire, par variation. */
    @PatchMapping("/articles/{idArticle}/stock")
    public ResponseEntity<ArticleResponseDto> ajusterStock(
            @PathVariable Long idArticle, @Valid @RequestBody AjustementStockDto dto) {
        return ResponseEntity.ok(adminService.ajusterStock(idArticle, dto));
    }

    @DeleteMapping("/articles/{idArticle}")
    public ResponseEntity<Map<String, String>> supprimerArticle(@PathVariable Long idArticle) {
        return ResponseEntity.ok(Map.of("message", adminService.supprimerArticle(idArticle)));
    }

    // ==================================================================
    //  Catégories
    // ==================================================================

    @GetMapping({"/categories", "/Allcategories"})
    public ResponseEntity<List<CategorieResponseDto>> toutesLesCategories() {
        return ResponseEntity.ok(adminService.toutesLesCategories());
    }

    @PostMapping({"/categories", "/Postcategories"})
    public ResponseEntity<CategorieResponseDto> ajouterCategorie(
            @Valid @RequestBody CategorieRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.ajouterCategorie(dto));
    }

    @PutMapping("/categories/{idCategorie}")
    public ResponseEntity<CategorieResponseDto> modifierCategorie(
            @PathVariable Long idCategorie, @Valid @RequestBody CategorieRequestDto dto) {
        return ResponseEntity.ok(adminService.modifierCategorie(idCategorie, dto));
    }

    @DeleteMapping("/categories/{idCategorie}")
    public ResponseEntity<Void> supprimerCategorie(@PathVariable Long idCategorie) {
        adminService.supprimerCategorie(idCategorie);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    //  Commandes
    // ==================================================================

    /**
     * <pre>
     *   GET /api/admin/commandes?statut=PAYEE&amp;debut=2026-11-01&amp;recherche=adrien
     *                           &amp;page=0&amp;size=20&amp;sort=dateCreation,desc
     * </pre>
     */
    @GetMapping("/commandes")
    public ResponseEntity<PageReponse<CommandeResumeDto>> rechercherCommandes(
            @RequestParam(required = false) StatutCommande statut,
            @RequestParam(required = false) Long idClient,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) BigDecimal montantMin,
            @RequestParam(required = false) String recherche,
            @PageableDefault(size = 20, sort = "dateCreation", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.rechercherCommandes(
                statut, idClient, debut, fin, montantMin, recherche, pageable));
    }

    @GetMapping("/commandes/tous")
    public ResponseEntity<List<CommandeResponseDto>> toutesLesCommandes() {
        return ResponseEntity.ok(adminService.toutesLesCommandes());
    }

    @GetMapping("/commandes/{idCommande}")
    public ResponseEntity<CommandeResponseDto> detailCommande(@PathVariable Long idCommande) {
        return ResponseEntity.ok(adminService.detailCommande(idCommande));
    }

    @PatchMapping("/commandes/{idCommande}/statut")
    public ResponseEntity<CommandeResponseDto> changerStatut(
            @PathVariable Long idCommande, @RequestParam String statut) {
        return ResponseEntity.ok(adminService.changerStatutCommande(idCommande, statut));
    }

    // ==================================================================
    //  Clients
    // ==================================================================

    @GetMapping("/clients")
    public ResponseEntity<PageReponse<PersonneResponseDto>> rechercherClients(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean actif,
            @PageableDefault(size = 20, sort = "nom") Pageable pageable) {
        return ResponseEntity.ok(adminService.rechercherClients(recherche, role, actif, pageable));
    }

    @GetMapping("/clients/tous")
    public ResponseEntity<List<PersonneResponseDto>> tousLesClients() {
        return ResponseEntity.ok(adminService.tousLesClients());
    }

    /** Fiche complète : identité, achats, panier en cours, dernières commandes. */
    @GetMapping("/clients/{idClient}")
    public ResponseEntity<ClientDetailDto> detailClient(@PathVariable Long idClient) {
        return ResponseEntity.ok(adminService.detailClient(idClient));
    }

    @PatchMapping("/clients/{idClient}/activation")
    public ResponseEntity<PersonneResponseDto> basculerActivation(@PathVariable Long idClient) {
        return ResponseEntity.ok(adminService.basculerActivationClient(idClient));
    }

    /** Promotion ou rétrogradation. On ne peut pas changer son propre rôle. */
    @PatchMapping("/clients/{idClient}/role")
    public ResponseEntity<PersonneResponseDto> changerRole(
            @PathVariable Long idClient, @Valid @RequestBody ChangementRoleDto dto) {
        return ResponseEntity.ok(adminService.changerRole(
                idClient, dto.getRole(), utilisateurCourant.get().getEmail()));
    }

    // ==================================================================
    //  Avis
    // ==================================================================

    @GetMapping("/avis")
    public ResponseEntity<PageReponse<AvisAdminDto>> rechercherAvis(
            @RequestParam(required = false) Long idArticle,
            @RequestParam(required = false) Integer noteMin,
            @RequestParam(required = false) Integer noteMax,
            @RequestParam(required = false) String recherche,
            @PageableDefault(size = 20, sort = "dateAvis", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.rechercherAvis(
                idArticle, noteMin, noteMax, recherche, pageable));
    }

    @GetMapping("/avis/tous")
    public ResponseEntity<List<AvisResponseDto>> tousLesAvis() {
        return ResponseEntity.ok(adminService.tousLesAvis());
    }

    @DeleteMapping("/avis/{idAvis}")
    public ResponseEntity<Void> supprimerAvis(@PathVariable Long idAvis) {
        adminService.supprimerAvis(idAvis);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    //  Journal d'administration
    // ==================================================================

    /**
     * Qui a fait quoi, et quand.
     *
     * <pre>
     *   GET /api/admin/journal?cibleType=PAIEMENT&amp;action=PAIEMENT_REMBOURSE
     *   GET /api/admin/journal?acteur=ralph&amp;debut=2026-11-01
     * </pre>
     */
    @GetMapping("/journal")
    public ResponseEntity<PageReponse<JournalAdminDto>> journal(
            @RequestParam(required = false) String acteur,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String cibleType,
            @RequestParam(required = false) String cibleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(journalAdminService.rechercher(
                acteur, action, cibleType, cibleId, debut, fin, pageable));
    }
}
