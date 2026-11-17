package com.example.E_commerce.Repository;

import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Models.Paiement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    Optional<Paiement> findByReference(String reference);

    /**
     * Charge le paiement en posant un verrou SELECT ... FOR UPDATE.
     *
     * <p>Indispensable : un callback d'operateur et le job de reconciliation
     * peuvent viser le meme paiement a la meme seconde. Sans verrou, les deux
     * transactions lisent EN_COURS, les deux appliquent un statut terminal et
     * les deux rendent le stock. Le verrou optimiste rattrapait le coup en
     * annulant la seconde transaction, mais au prix d'une erreur dans les
     * journaux. Ici la seconde transaction attend, relit la ligne committee,
     * constate l'etat terminal et s'arrete proprement.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Paiement p where p.reference = :reference")
    Optional<Paiement> verrouillerParReference(@Param("reference") String reference);

    /** Chiffre d'affaires : somme calculee par la base, pas en memoire. */
    @Query("""
           select coalesce(sum(p.montant), 0) from Paiement p
           where p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.PAYE
           """)
    java.math.BigDecimal chiffreAffaires();

    List<Paiement> findByCommandeIdOrderByDateCreationAsc(Long idCommande);

    List<Paiement> findByStatutPaiement(StatutPaiement statut);

    /** Paiements encore en vol dont le delai est depasse : cible du job d'expiration. */
    @Query("""
           select p.reference from Paiement p
           where p.statutPaiement in (:statuts)
             and p.dateExpiration is not null
             and p.dateExpiration < :maintenant
           """)
    List<String> trouverReferencesExpirees(@Param("statuts") List<StatutPaiement> statuts,
                                           @Param("maintenant") Instant maintenant);

    /** Paiements en vol non expires : cible du job de reconciliation. */
    @Query("""
           select p.reference from Paiement p
           where p.statutPaiement in (:statuts)
             and (p.dateExpiration is null or p.dateExpiration >= :maintenant)
           """)
    List<String> trouverReferencesEnVol(@Param("statuts") List<StatutPaiement> statuts,
                                        @Param("maintenant") Instant maintenant);
}
