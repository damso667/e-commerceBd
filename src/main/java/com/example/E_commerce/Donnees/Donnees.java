package com.example.E_commerce.Donnees;

import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Categorie;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.ArticleRepository;
import com.example.E_commerce.Repository.CategorieRepository;
import com.example.E_commerce.Repository.PersonneRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Jeu de donnees de demarrage.
 *
 * <p>Deux corrections par rapport a la version precedente :</p>
 * <ul>
 *   <li>un copier-coller testait {@code CategorieOptional2} et
 *       {@code CategorieOptional3} pour decider du sort des categories 4 et 5,
 *       ce qui provoquait un {@code NoSuchElementException} au deuxieme
 *       demarrage ;</li>
 *   <li>les comptes de demonstration ne sont plus crees quand le profil
 *       {@code prod} est actif : un compte ADMIN avec le mot de passe
 *       "password" ne doit jamais exister en production.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class Donnees implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Donnees.class);

    private final CategorieRepository categorieRepository;
    private final ArticleRepository articleRepository;
    private final PersonneRepository personneRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {

        Categorie vetement = categorie("Vetement", "Tous les vetements pour homme et femme");
        Categorie chaussure = categorie("Chaussure", "Toutes les chaussures pour homme et femme");
        Categorie accessoire = categorie("Accessoire", "Tous les accessoires pour homme et femme");
        categorie("Electronique", "Produits electroniques");
        categorie("Mobilier", "Mobilier pour votre maison");

        article("t-shirt", vetement, 6, 26600,
                "T-shirt vintage adapte aux hommes comme aux femmes",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ69O_4EG_WXMXip-QKPRd4Rc-fSEby4cCCMn7y7oHuOQ&s=10");
        article("pantalon GUCCI", vetement, 7, 5000,
                "Pantalon vintage tres style",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR-2eptk5UVXenLRqz5fRdvncEHIaLrcIptdPkAfxFQ4Q&s=10");
        article("Air Jordan Noir", chaussure, 4, 25000,
                "Chaussure vintage mixte",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTU8-OSizvFExl9kuFe9tciPBI1Oy_4iowUSgFkuH3Sig&s=10");
        article("Montre Gucci", accessoire, 8, 250000,
                "Montre vintage mixte",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ69O_4EG_WXMXip-QKPRd4Rc-fSEby4cCCMn7y7oHuOQ&s=10");

        if (environment.matchesProfiles("prod")) {
            log.info("Profil prod : aucun compte de demonstration cree");
            return;
        }
        compte("adrien@gmail.com", "noubam", "Doe", Role.USER);
        compte("ralph@gmail.com", "ndoumbe", "John", Role.ADMIN);
    }

    private Categorie categorie(String nom, String description) {
        return categorieRepository.findByNomCategorie(nom).orElseGet(() -> {
            Categorie categorie = new Categorie();
            categorie.setNomCategorie(nom);
            categorie.setDescription(description);
            return categorieRepository.save(categorie);
        });
    }

    private void article(String libelle, Categorie categorie, int stock,
                         long prix, String description, String imageUrl) {
        if (articleRepository.findByLibelle(libelle).isPresent()) {
            return;
        }
        Article article = new Article();
        article.setLibelle(libelle);
        article.setCategorie(categorie);
        article.setStock(stock);
        article.setPrix(BigDecimal.valueOf(prix));
        article.setDescription(description);
        article.setImageUrl(imageUrl);
        articleRepository.save(article);
    }

    private void compte(String email, String nom, String prenom, Role role) {
        if (personneRepository.findByEmail(email).isPresent()) {
            return;
        }
        Personne personne = new Personne();
        personne.setEmail(email);
        personne.setNom(nom);
        personne.setPrenom(prenom);
        personne.setAdresse("Nkolbisson");
        personne.setNumeroTelephone(role == Role.ADMIN ? "677000001" : "677123456");
        personne.setMotDePasse(passwordEncoder.encode("password"));
        personne.setRole(role);
        personne.setActif(true);
        personneRepository.save(personne);
        log.info("Compte de demonstration cree : {} ({})", email, role);
    }
}
