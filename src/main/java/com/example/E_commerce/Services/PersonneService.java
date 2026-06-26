package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.AvisResponseDto;
import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor

public class PersonneService {
    private final ArticleRepository articleRepository;
    private final PagnierRepository pagnierRepository;
    private final PersonneRepository personneRepository;
    private final CommandeRepository commandeRepository;
    private final PaiementRepository paiementRepository;
    private final AvisRepository avisRepository;

    public List<Article> voirAllArticle() {
        return articleRepository.findAll();
    }
    public List<Article> voirArticleParCategorie(Long Idcategorie) {
        return articleRepository.findByCategorieIdCategorie(Idcategorie);
    }

    public Personne registerUser(Personne personne) {
        return personneRepository.save(personne);
    }

    public List<AvisResponseDto> voirAvisArticle(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable"));
        return avisRepository.findByArticle(article)
                .stream()
                .map(AvisResponseDto::of)
                .toList();
    }

}
