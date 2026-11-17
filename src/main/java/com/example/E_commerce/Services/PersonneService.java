package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.ArticleDto;
import com.example.E_commerce.Dto.AvisResponseDto;
import com.example.E_commerce.Dto.PersonneDto;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Exception.RegleMetierException;
import com.example.E_commerce.Exception.RessourceIntrouvableException;
import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.ArticleRepository;
import com.example.E_commerce.Repository.AvisRepository;
import com.example.E_commerce.Repository.PersonneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Operations accessibles sans compte : catalogue public et inscription. */
@Service
@RequiredArgsConstructor
public class PersonneService {

    private final ArticleRepository articleRepository;
    private final PersonneRepository personneRepository;
    private final AvisRepository avisRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<ArticleDto> voirAllArticle() {
        return articleRepository.chargerTousAvecCategorie()
                .stream()
                .map(ArticleDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArticleDto> voirArticleParCategorie(Long idCategorie) {
        return articleRepository.findByCategorieIdCategorie(idCategorie)
                .stream()
                .map(ArticleDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvisResponseDto> voirAvisArticle(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));
        return avisRepository.chargerParArticle(article)
                .stream()
                .map(AvisResponseDto::of)
                .toList();
    }

    /**
     * Inscription.
     *
     * <p>Le role est impose a USER cote serveur : il n'est pas lu depuis la
     * requete, sinon n'importe qui s'inscrirait en ADMIN.</p>
     */
    @Transactional
    public Personne inscrire(PersonneDto dto) {
        String email = dto.getEmail().trim().toLowerCase();
        if (personneRepository.findByEmail(email).isPresent()) {
            throw new RegleMetierException("Cet email est deja utilise");
        }
        Personne personne = new Personne();
        personne.setNom(dto.getNom());
        personne.setPrenom(dto.getPrenom());
        personne.setEmail(email);
        personne.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        personne.setAdresse(dto.getAdresse());
        personne.setNumeroTelephone(dto.getNumeroTelephone());
        personne.setRole(Role.USER);
        personne.setActif(true);
        return personneRepository.save(personne);
    }
}
