package com.example.E_commerce.Controller;

import com.example.E_commerce.Dto.ArticleDto;
import com.example.E_commerce.Dto.AvisResponseDto;
import com.example.E_commerce.Dto.PersonneDto;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.PersonneRepository;
import com.example.E_commerce.Services.PersonneService;
import com.example.E_commerce.Services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/personne")
public class PersonneController {

    private  final PersonneService personneService;
    private  final PersonneRepository personneRepository;
    private  final UserService userService;
    private  final PasswordEncoder passwordEncoder;

    @GetMapping(value = "/Allarticle")
    public ResponseEntity<List<ArticleDto>> voirAllArticle() {

        List <Article> article = personneService.voirAllArticle();

        return ResponseEntity.ok(article.stream().map(ArticleDto::of).toList());
    }

    @GetMapping("/avisArticle/{idArticle}")
    public ResponseEntity<List<AvisResponseDto>> voirAvisArticle(@PathVariable Long idArticle) {
        return ResponseEntity.ok(personneService.voirAvisArticle(idArticle));
    }

    @GetMapping(value = "/articleParCategorie/{idCategorie}")
    public ResponseEntity<List<ArticleDto>> voirArticleParCategorie(@PathVariable Long idCategorie) {
        List<Article> article = personneService.voirArticleParCategorie(idCategorie);
        return ResponseEntity.ok(article.stream().map(ArticleDto::of).toList());
    }

    @PostMapping(value = "/register")
    public ResponseEntity<?> register(@RequestBody PersonneDto personneDto) {
        if (personneRepository.findByEmail(personneDto.getEmail()).isPresent()){
            return ResponseEntity.badRequest().body("Email déjà utilisé");
        }
        Personne personne = new Personne();
        personne.setNom(personneDto.getNom());
        personne.setMotDePasse(passwordEncoder.encode(personneDto.getMotDePasse()));
        personne.setRole(Role.USER);
        personne.setEmail(personneDto.getEmail());


        personneService.registerUser(personne);
        return ResponseEntity.created(null).body("Utilisateur enregistré avec succès");

    }

}
