package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Pagnier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PagnierRepository extends JpaRepository<Pagnier, Long> {

    Optional<Pagnier> findByClientId(Long id);

    /** Panier + lignes + articles + client en une requete. */
    @Query("""
           select distinct p from Pagnier p
           left join fetch p.lignes l
           left join fetch l.article
           left join fetch p.client
           where p.client.id = :idClient
           """)
    Optional<Pagnier> chargerComplet(Long idClient);
}
