package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.LigneCommande;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LigneCommandeRepository extends JpaRepository<LigneCommande, Long> {

    /**
     * Meilleures ventes, agregees par la base.
     *
     * <p>L'ancienne version chargeait TOUTES les lignes de commande en memoire
     * pour les regrouper en Java : impraticable des quelques milliers de
     * commandes. Ici la base fait le travail et ne renvoie que le top N.</p>
     */
    @Query("""
           select l.article.id, l.article.libelle, sum(l.quantite), sum(l.prixUnitaire * l.quantite)
           from LigneCommande l
           where l.commande.statut in (com.example.E_commerce.Enum.StatutCommande.PAYEE,
                                       com.example.E_commerce.Enum.StatutCommande.EN_COURS,
                                       com.example.E_commerce.Enum.StatutCommande.LIVREE)
           group by l.article.id, l.article.libelle
           order by sum(l.quantite) desc
           """)
    List<Object[]> meilleuresVentes(Pageable pageable);

    boolean existsByArticleId(Long idArticle);
}
