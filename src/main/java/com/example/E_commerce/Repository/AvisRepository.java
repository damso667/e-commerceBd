package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Avis;
import com.example.E_commerce.Models.Personne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AvisRepository extends JpaRepository<Avis, Long> {

    List<Avis> findByArticle(Article article);

    /** Un client ne note un article qu'une fois. */
    boolean existsByAuteurAndArticle(Personne auteur, Article article);

    @Query("""
           select a from Avis a
           join fetch a.auteur
           join fetch a.article
           where a.article = :article
           """)
    List<Avis> chargerParArticle(Article article);

    @Query("""
           select a from Avis a
           join fetch a.auteur
           join fetch a.article
           """)
    List<Avis> chargerTous();
}
