package com.example.E_commerce.Controller;

import com.example.E_commerce.Dto.*;
import com.example.E_commerce.Services.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ---------------- Articles ----------------

    @GetMapping("/articles")
    public ResponseEntity<List<ArticleResponseDto>> tousLesArticles() {
        return ResponseEntity.ok(adminService.tousLesArticles());
    }

    @PostMapping("/articles")
    public ResponseEntity<ArticleResponseDto> ajouterArticle(@Valid @RequestBody ArticleRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.ajouterArticle(dto));
    }

    @PutMapping("/articles/{idArticle}")
    public ResponseEntity<ArticleResponseDto> modifierArticle(
            @PathVariable Long idArticle, @Valid @RequestBody ArticleRequestDto dto) {
        return ResponseEntity.ok(adminService.modifierArticle(idArticle, dto));
    }

    @DeleteMapping("/articles/{idArticle}")
    public ResponseEntity<Map<String, String>> supprimerArticle(@PathVariable Long idArticle) {
        return ResponseEntity.ok(Map.of("message", adminService.supprimerArticle(idArticle)));
    }

    // ---------------- Categories ----------------

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

    // ---------------- Commandes ----------------

    @GetMapping("/commandes")
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

    // ---------------- Clients ----------------

    @GetMapping("/clients")
    public ResponseEntity<List<PersonneResponseDto>> tousLesClients() {
        return ResponseEntity.ok(adminService.tousLesClients());
    }

    @GetMapping("/clients/{idClient}")
    public ResponseEntity<PersonneResponseDto> detailClient(@PathVariable Long idClient) {
        return ResponseEntity.ok(adminService.detailClient(idClient));
    }

    /** Bascule actif / inactif. */
    @PatchMapping("/clients/{idClient}/activation")
    public ResponseEntity<PersonneResponseDto> basculerActivation(@PathVariable Long idClient) {
        return ResponseEntity.ok(adminService.basculerActivationClient(idClient));
    }

    // ---------------- Avis ----------------

    @GetMapping("/avis")
    public ResponseEntity<List<AvisResponseDto>> tousLesAvis() {
        return ResponseEntity.ok(adminService.tousLesAvis());
    }

    @DeleteMapping("/avis/{idAvis}")
    public ResponseEntity<Void> supprimerAvis(@PathVariable Long idAvis) {
        adminService.supprimerAvis(idAvis);
        return ResponseEntity.noContent().build();
    }

    // ---------------- Statistiques ----------------

    @GetMapping("/statistiques")
    public ResponseEntity<StatistiquesDto> tableauDeBord() {
        return ResponseEntity.ok(adminService.tableauDeBord());
    }
}
