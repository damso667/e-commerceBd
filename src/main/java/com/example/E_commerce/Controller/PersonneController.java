package com.example.E_commerce.Controller;

import com.example.E_commerce.Dto.ArticleDto;
import com.example.E_commerce.Dto.AvisResponseDto;
import com.example.E_commerce.Dto.CategorieResponseDto;
import com.example.E_commerce.Dto.PersonneDto;
import com.example.E_commerce.Dto.PersonneResponseDto;
import com.example.E_commerce.Services.PersonneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Catalogue public et inscription : aucune authentification requise. */
@RestController
@RequestMapping("/api/personne")
@RequiredArgsConstructor
public class PersonneController {

    private final PersonneService personneService;

    @GetMapping("/Allarticle")
    public ResponseEntity<List<ArticleDto>> voirAllArticle() {
        return ResponseEntity.ok(personneService.voirAllArticle());
    }

    /** Rayons de la boutique, pour afficher un nom plutot qu'un numero. */
    @GetMapping("/categories")
    public ResponseEntity<List<CategorieResponseDto>> voirCategories() {
        return ResponseEntity.ok(personneService.voirCategories());
    }

    @GetMapping("/articleParCategorie/{idCategorie}")
    public ResponseEntity<List<ArticleDto>> voirArticleParCategorie(@PathVariable Long idCategorie) {
        return ResponseEntity.ok(personneService.voirArticleParCategorie(idCategorie));
    }

    @GetMapping("/avisArticle/{idArticle}")
    public ResponseEntity<List<AvisResponseDto>> voirAvisArticle(@PathVariable Long idArticle) {
        return ResponseEntity.ok(personneService.voirAvisArticle(idArticle));
    }

    /**
     * Inscription. Renvoie la ressource creee plutot qu'une phrase :
     * le front peut ainsi enchainer sans refaire un appel.
     */
    @PostMapping("/register")
    public ResponseEntity<PersonneResponseDto> register(@Valid @RequestBody PersonneDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PersonneResponseDto.of(personneService.inscrire(dto)));
    }
}
