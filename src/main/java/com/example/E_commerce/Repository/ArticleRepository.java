package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    Optional<Article> findByLibelle(String libelle);
    List<Article> findByCategorieIdCategorie(Long idcategorie);

}
