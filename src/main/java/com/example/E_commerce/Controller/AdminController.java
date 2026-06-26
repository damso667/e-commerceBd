package com.example.E_commerce.Controller;

import com.example.E_commerce.Dto.*;
import com.example.E_commerce.Models.Categorie;
import com.example.E_commerce.Repository.CategorieRepository;
import com.example.E_commerce.Services.AdminService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

     private final AdminService adminService;
     private final CategorieRepository categorieRepository;

     @GetMapping("/articles")
     public ResponseEntity<List<ArticleResponseDto>> tousLesArticles() {
          return ResponseEntity.ok(adminService.tousLesArticles());
     }

     @PostMapping("/articles")
     public ResponseEntity<ArticleResponseDto> ajouterArticle(@RequestBody ArticleRequestDto dto) {
          return ResponseEntity.ok(adminService.ajouterArticle(dto));
     }

     @GetMapping("/Allcategories")
     public ResponseEntity<List<CategorieResponseDto>> toutesLesCategories() {
          return ResponseEntity.ok(adminService.toutesLesCategories());
     }

     @PostMapping("/Postcategories")
     public ResponseEntity<CategorieResponseDto> ajouterCategorie(@RequestBody CategorieRequestDto dto) {
          return ResponseEntity.ok(adminService.ajouterCategorie(dto));
     }

     @PutMapping("/categories/{idCategorie}")
     public ResponseEntity<CategorieResponseDto> modifierCategorie(
             @PathVariable Long idCategorie,
             @RequestBody CategorieRequestDto dto) {
          return ResponseEntity.ok(adminService.modifierCategorie(idCategorie, dto));
     }

     @DeleteMapping("/categories/{idCategorie}")
     public ResponseEntity<Void> supprimerCategorie(@PathVariable Long idCategorie) {
          adminService.supprimerCategorie(idCategorie);
          return ResponseEntity.ok().build();
     }

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
             @PathVariable Long idCommande,
             @RequestParam String statut) {
          return ResponseEntity.ok(adminService.changerStatutCommande(idCommande, statut));
     }

     @GetMapping("/avis")
     public ResponseEntity<List<AvisResponseDto>> tousLesAvis() {
          return ResponseEntity.ok(adminService.tousLesAvis());
     }

     @DeleteMapping("/avis/{idAvis}")
     public ResponseEntity<Void> supprimerAvis(@PathVariable Long idAvis) {
          adminService.supprimerAvis(idAvis);
          return ResponseEntity.ok().build(); //build() pour retourner une réponse vide avec un code de statut 200 OK
     }

     @GetMapping("/statistiques")
     public ResponseEntity<StatistiquesDto> tableauDeBord() {
          return ResponseEntity.ok(adminService.tableauDeBord());
     }



}
