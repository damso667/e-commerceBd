package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Article;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository
        extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    Optional<Article> findByLibelle(String libelle);

    List<Article> findByCategorieIdCategorie(Long idcategorie);

    @Query("select a from Article a join fetch a.categorie")
    List<Article> chargerTousAvecCategorie();

    /**
     * Verrou pessimiste sur la ligne article le temps de decrementer le stock.
     * Sans lui, deux commandes simultanees du dernier exemplaire passent
     * toutes les deux et le stock devient negatif.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Article a where a.id = :id")
    Optional<Article> verrouillerPourStock(Long id);
}
