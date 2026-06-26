package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Avis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvisRepository extends JpaRepository<Avis, Long> {

    List<Avis> findByArticle(Article article);
}
