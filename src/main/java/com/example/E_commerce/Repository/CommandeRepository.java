package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Commande;
import com.example.E_commerce.Models.Personne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CommandeRepository extends JpaRepository<Commande, Long> {

    List<Commande> findByClient(Personne client);

    /**
     * Charge la commande avec ses lignes et leurs articles en une seule requete.
     * Evite le probleme N+1 et les LazyInitializationException maintenant que
     * spring.jpa.open-in-view est a false.
     */
    @Query("""
           select distinct c from Commande c
           left join fetch c.lignes l
           left join fetch l.article
           left join fetch c.client
           where c.id = :id
           """)
    Optional<Commande> chargerComplete(Long id);

    @Query("""
           select distinct c from Commande c
           left join fetch c.lignes l
           left join fetch l.article
           left join fetch c.client
           where c.client = :client
           """)
    List<Commande> chargerCompletesParClient(Personne client);

    @Query("""
           select distinct c from Commande c
           left join fetch c.lignes l
           left join fetch l.article
           left join fetch c.client
           """)
    List<Commande> chargerToutesCompletes();

    /**
     * Commandes restees EN_ATTENTE sans aucune tentative de paiement active,
     * au-dela du delai. Cas typique : le client a cree sa commande puis a
     * ferme l'onglet, ou l'initiation du paiement a echoue. Sans ce
     * ramassage, leur stock reste reserve pour toujours.
     */
    @Query("""
           select c.id from Commande c
           where c.statut = com.example.E_commerce.Enum.StatutCommande.EN_ATTENTE
             and c.dateCreation is not null
             and c.dateCreation < :limite
             and not exists (
                 select 1 from Paiement p
                 where p.commande = c
                   and p.statutPaiement in (
                       com.example.E_commerce.Enum.StatutPaiement.EN_ATTENTE,
                       com.example.E_commerce.Enum.StatutPaiement.EN_COURS,
                       com.example.E_commerce.Enum.StatutPaiement.PAYE)
             )
           """)
    List<Long> trouverOrphelines(java.time.Instant limite);

    /** Nombre de commandes par statut, agrege par la base. */
    @Query("select c.statut, count(c) from Commande c group by c.statut")
    List<Object[]> compterParStatut();
}
