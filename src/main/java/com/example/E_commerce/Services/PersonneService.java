package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.ArticleDto;
import com.example.E_commerce.Dto.CategorieResponseDto;
import com.example.E_commerce.Dto.AvisResponseDto;
import com.example.E_commerce.Dto.PersonneDto;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Exception.RegleMetierException;
import com.example.E_commerce.Exception.RessourceIntrouvableException;
import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.ArticleRepository;
import com.example.E_commerce.Repository.CategorieRepository;
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
    private final CategorieRepository categorieRepository;
    private final PersonneRepository personneRepository;
    private final AvisRepository avisRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Categories visibles par un visiteur non connecte.
     *
     * Sans cet endpoint, la boutique ne peut afficher qu'un identifiant
     * numerique a la place du nom de la categorie : les categories
     * n'etaient exposees que sous /api/admin, donc reservees aux
     * administrateurs. Un « Categorie 3 » en tete de rayon est un defaut
     * visible par tous les clients.
     */
    @Transactional(readOnly = true)
    public List<CategorieResponseDto> voirCategories() {
        return categorieRepository.findAll(org.springframework.data.domain.Sort.by("nomCategorie"))
                .stream()
                .map(CategorieResponseDto::of)
                .toList();
    }

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

    /**
     * Un article precis.
     *
     * <p>Sans cet endpoint, la fiche produit devait telecharger le catalogue
     * entier pour en extraire une ligne : inutilement lourd sur une
     * connexion mobile.</p>
     */
    @Transactional(readOnly = true)
    public ArticleDto voirArticle(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));
        return ArticleDto.of(article);
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

    /**
     * Photo d'un article, telle qu'elle a ete televersee.
     *
     * <p>La lecture est transactionnelle parce que le binaire est charge
     * paresseusement : hors transaction, et avec {@code open-in-view=false},
     * y acceder leverait une LazyInitializationException.</p>
     *
     * @return la photo, ou {@code null} si l'article n'en a pas
     */
    @Transactional(readOnly = true)
    public ImageArticle lireImage(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));
        if (!article.aUneImage() || article.getImageDonnees() == null) {
            return null;
        }
        return new ImageArticle(article.getImageDonnees(), article.getImageType(),
                article.getImageVersion());
    }

    /** Photo prete a servir : octets, type MIME et version pour l'ETag. */
    public record ImageArticle(byte[] donnees, String type, String version) {
    }
}
