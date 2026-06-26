package com.example.E_commerce.Donnees;

import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Categorie;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.ArticleRepository;
import com.example.E_commerce.Repository.CategorieRepository;
import com.example.E_commerce.Repository.PersonneRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@AllArgsConstructor

@Component
public  class Donnees implements CommandLineRunner {
    private final CategorieRepository categorieRepository;
    private final ArticleRepository articleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonneRepository personneRepository;


    @Override
    public void run(String... args) throws Exception {

        Categorie c1 = new Categorie();
        c1.setNomCategorie("Vetement");
        c1.setDescription("Tout les vetements pour homme et femme");

        Categorie c2 = new Categorie();
        c2.setNomCategorie("Chaussure");
        c2.setDescription("Tout les chaussures pour homme et femme");

        Categorie c3 = new Categorie();
        c3.setNomCategorie("Accessoire");
        c3.setDescription("Tout les accessoires pour homme et femme");

        Categorie c4 = new Categorie();
        c4.setNomCategorie("Electronique");
        c4.setDescription("Tout les produits electronique pour homme et femme");

        Categorie c5 = new Categorie();
        c5.setNomCategorie("Mobilier");
        c5.setDescription("Tout les produits mobilier adapté pour votre maison");

        Optional<Categorie> CategorieOptional1 = categorieRepository.findByNomCategorie(c1.getNomCategorie());
        Optional<Categorie> CategorieOptional2 = categorieRepository.findByNomCategorie(c2.getNomCategorie());
        Optional<Categorie> CategorieOptional3 = categorieRepository.findByNomCategorie(c3.getNomCategorie());
        Optional<Categorie> CategorieOptional4 = categorieRepository.findByNomCategorie(c4.getNomCategorie());
        Optional<Categorie> CategorieOptional5 = categorieRepository.findByNomCategorie(c5.getNomCategorie());

// Récupérer ou sauvegarder les catégories
        Categorie ca1 = CategorieOptional1.isPresent() ? CategorieOptional1.get() : categorieRepository.save(c1);
        Categorie ca2 = CategorieOptional2.isPresent() ? CategorieOptional2.get() : categorieRepository.save(c2);
        Categorie ca3 = CategorieOptional3.isPresent() ? CategorieOptional3.get() : categorieRepository.save(c3);
        Categorie ca4 = CategorieOptional2.isPresent() ? CategorieOptional4.get() : categorieRepository.save(c4);
        Categorie ca5 = CategorieOptional3.isPresent() ? CategorieOptional5.get() : categorieRepository.save(c5);

        //article

        Article a1 = new Article();


        a1.setCategorie(ca1);
        a1.setDescription("ceci est un t-chirt vintage adapter pour un homme comme une femme");
        a1.setLibelle("t-schirt");
        a1.setStock(6);
        a1.setPrix(BigDecimal.valueOf(26600));
        a1.setImageUrl("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ69O_4EG_WXMXip-QKPRd4Rc-fSEby4cCCMn7y7oHuOQ&s=10");

        Article a2 = new Article();
        a2.setLibelle("pantalon GUCCI");
        a2.setStock(7);
        a2.setCategorie(ca1);
        a2.setPrix(BigDecimal.valueOf(5000));
        a2.setDescription("pantalon vintage tres stylé");
        a2.setImageUrl("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR-2eptk5UVXenLRqz5fRdvncEHIaLrcIptdPkAfxFQ4Q&s=10");

        Article a3 = new Article();
        a3.setCategorie(ca2);
        a3.setStock(4);
        a3.setPrix(BigDecimal.valueOf(25000));
        a3.setLibelle("Air Jordan Noir");
        a3.setImageUrl("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTU8-OSizvFExl9kuFe9tciPBI1Oy_4iowUSgFkuH3Sig&s=10");
        a3.setDescription("chausure vintage apter pour tous les sexe");

        Article a4 = new Article();
        a4.setCategorie(ca3);
        a4.setStock(8);
        a4.setPrix(BigDecimal.valueOf(250000));
        a4.setLibelle("Montre Gucci");
        a4.setImageUrl("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ69O_4EG_WXMXip-QKPRd4Rc-fSEby4cCCMn7y7oHuOQ&s=10");
        a4.setDescription("Montre vintage adapter pour un homme comme une femme");


        Optional<Article> articleOptional1 = articleRepository.findByLibelle(a1.getLibelle());
        Optional<Article> articleOptional2 = articleRepository.findByLibelle(a2.getLibelle());
        Optional<Article> articleOptional4 = articleRepository.findByLibelle(a4.getLibelle());
        Optional<Article> articleOptional3 = articleRepository.findByLibelle(a3.getLibelle());

        if (articleOptional1.isEmpty()) {
            articleRepository.save(a1);
        }
        if (articleOptional2.isEmpty()) {
            articleRepository.save(a2);
        }
        if (articleOptional3.isEmpty()) {
            articleRepository.save(a3);
        }
        if (articleOptional4.isEmpty()) {
            articleRepository.save(a4);
        }

        Personne personne1 = new Personne();
        personne1.setNom("noubam");
        personne1.setPrenom("Doe");
        personne1.setAdresse("Nkolbisson");
        personne1.setEmail("adrien@gmail.com");
        personne1.setMotDePasse(passwordEncoder.encode("password"));
        personne1.setRole(Role.USER);

        Personne personne2 = new Personne();
        personne2.setNom("ndoumbé");
        personne2.setPrenom("John");
        personne2.setAdresse("Nkolbisson");
        personne2.setEmail("ralph@gmail.com");
        personne2.setMotDePasse(passwordEncoder.encode("password"));
        personne2.setRole(Role.ADMIN);

        Optional<Personne> personneOptional1 = personneRepository.findByEmail(personne1.getEmail());
        Optional<Personne> personneOptional2 = personneRepository.findByEmail(personne2.getEmail());

        if (personneOptional1.isEmpty()){
            personneRepository.save(personne1);
        }
        if (personneOptional2.isEmpty()){
            personneRepository.save(personne2);
        }



    }
}
