package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Commande;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Toutes les agrégations du tableau de bord, regroupées ici.
 *
 * <p>Pourquoi un dépôt dédié plutôt que d'éparpiller ces requêtes : elles ne
 * servent qu'au back-office, elles ne renvoient pas des entités mais des
 * lignes de chiffres, et les garder ensemble permet de vérifier d'un coup
 * d'œil qu'aucune ne ramène de données en mémoire pour les agréger en Java.</p>
 *
 * <p>Convention sur les bornes : {@code debut} est inclusif, {@code fin} est
 * exclusif. Le service convertit une période « du 1er au 17 » en
 * {@code [1er 00:00, 18 00:00)}, sinon la dernière journée serait tronquée.</p>
 *
 * <p>Convention sur le chiffre d'affaires : il vient des <b>paiements
 * réussis</b> ({@code datePaiement}), jamais des commandes. Une commande non
 * réglée n'est pas du revenu.</p>
 */
public interface StatistiquesRepository extends JpaRepository<Commande, Long> {

    // ==================================================================
    //  Chiffre d'affaires et volumes
    // ==================================================================

    @Query("""
           select coalesce(sum(p.montant), 0) from Paiement p
           where p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.PAYE
             and p.datePaiement >= :debut and p.datePaiement < :fin
           """)
    BigDecimal chiffreAffaires(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("""
           select count(p) from Paiement p
           where p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.PAYE
             and p.datePaiement >= :debut and p.datePaiement < :fin
           """)
    long nombreCommandesPayees(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("""
           select count(c) from Commande c
           where c.dateCreation >= :debut and c.dateCreation < :fin
           """)
    long nombreCommandesCreees(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Quantité d'articles effectivement vendus (commandes réglées). */
    @Query("""
           select coalesce(sum(l.quantite), 0) from LigneCommande l
           where l.commande.statut in (com.example.E_commerce.Enum.StatutCommande.PAYEE,
                                       com.example.E_commerce.Enum.StatutCommande.EN_COURS,
                                       com.example.E_commerce.Enum.StatutCommande.LIVREE)
             and l.commande.dateCreation >= :debut and l.commande.dateCreation < :fin
           """)
    Long nombreArticlesVendus(@Param("debut") Instant debut, @Param("fin") Instant fin);

    // ==================================================================
    //  Séries temporelles (pour les courbes)
    // ==================================================================

    /**
     * Chiffre d'affaires et nombre de paiements réussis, jour par jour.
     * Le regroupement est fait par PostgreSQL, pas en Java.
     *
     * @return lignes [LocalDate jour, BigDecimal montant, Long nombre]
     */
    @Query("""
           select cast(p.datePaiement as LocalDate), coalesce(sum(p.montant), 0), count(p)
           from Paiement p
           where p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.PAYE
             and p.datePaiement >= :debut and p.datePaiement < :fin
           group by cast(p.datePaiement as LocalDate)
           order by cast(p.datePaiement as LocalDate)
           """)
    List<Object[]> chiffreAffairesParJour(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** @return lignes [LocalDate jour, Long nombre de commandes créées] */
    @Query("""
           select cast(c.dateCreation as LocalDate), count(c)
           from Commande c
           where c.dateCreation >= :debut and c.dateCreation < :fin
           group by cast(c.dateCreation as LocalDate)
           order by cast(c.dateCreation as LocalDate)
           """)
    List<Object[]> commandesParJour(@Param("debut") Instant debut, @Param("fin") Instant fin);

    // ==================================================================
    //  Répartitions
    // ==================================================================

    /** @return lignes [StatutCommande, Long] sur la période */
    @Query("""
           select c.statut, count(c) from Commande c
           where c.dateCreation >= :debut and c.dateCreation < :fin
           group by c.statut
           """)
    List<Object[]> commandesParStatut(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** @return lignes [StatutPaiement, Long] sur la période */
    @Query("""
           select p.statutPaiement, count(p) from Paiement p
           where p.dateCreation >= :debut and p.dateCreation < :fin
           group by p.statutPaiement
           """)
    List<Object[]> paiementsParStatut(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /**
     * Performance de chaque opérateur : c'est l'indicateur qui dit s'il faut
     * appeler MTN ou Orange parce que quelque chose cloche de leur côté.
     *
     * @return lignes [FournisseurPaiement, total, réussis, échoués, expirés, montant encaissé]
     */
    @Query("""
           select p.fournisseur,
                  count(p),
                  sum(case when p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.PAYE then 1 else 0 end),
                  sum(case when p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.ECHOUE then 1 else 0 end),
                  sum(case when p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.EXPIRE then 1 else 0 end),
                  coalesce(sum(case when p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.PAYE then p.montant else 0 end), 0)
           from Paiement p
           where p.dateCreation >= :debut and p.dateCreation < :fin
           group by p.fournisseur
           """)
    List<Object[]> paiementsParFournisseur(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /**
     * Motifs d'échec les plus fréquents : savoir si l'on perd des ventes par
     * solde insuffisant ou par abandon change complètement les actions.
     *
     * @return lignes [codeErreur, Long]
     */
    @Query("""
           select coalesce(p.codeErreur, 'INCONNU'), count(p) from Paiement p
           where p.statutPaiement in (com.example.E_commerce.Enum.StatutPaiement.ECHOUE,
                                      com.example.E_commerce.Enum.StatutPaiement.EXPIRE)
             and p.dateCreation >= :debut and p.dateCreation < :fin
           group by coalesce(p.codeErreur, 'INCONNU')
           order by count(p) desc
           """)
    List<Object[]> motifsEchec(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** @return lignes [idCategorie, nomCategorie, chiffre d'affaires, quantité] */
    @Query("""
           select cat.idCategorie, cat.nomCategorie,
                  coalesce(sum(l.prixUnitaire * l.quantite), 0), coalesce(sum(l.quantite), 0)
           from LigneCommande l
           join l.article a
           join a.categorie cat
           where l.commande.statut in (com.example.E_commerce.Enum.StatutCommande.PAYEE,
                                       com.example.E_commerce.Enum.StatutCommande.EN_COURS,
                                       com.example.E_commerce.Enum.StatutCommande.LIVREE)
             and l.commande.dateCreation >= :debut and l.commande.dateCreation < :fin
           group by cat.idCategorie, cat.nomCategorie
           order by sum(l.prixUnitaire * l.quantite) desc
           """)
    List<Object[]> chiffreAffairesParCategorie(@Param("debut") Instant debut,
                                               @Param("fin") Instant fin);

    // ==================================================================
    //  Palmarès
    // ==================================================================

    /** @return lignes [idArticle, libelle, quantité, chiffre d'affaires] */
    @Query("""
           select a.id, a.libelle, coalesce(sum(l.quantite), 0),
                  coalesce(sum(l.prixUnitaire * l.quantite), 0)
           from LigneCommande l
           join l.article a
           where l.commande.statut in (com.example.E_commerce.Enum.StatutCommande.PAYEE,
                                       com.example.E_commerce.Enum.StatutCommande.EN_COURS,
                                       com.example.E_commerce.Enum.StatutCommande.LIVREE)
             and l.commande.dateCreation >= :debut and l.commande.dateCreation < :fin
           group by a.id, a.libelle
           order by sum(l.quantite) desc
           """)
    List<Object[]> meilleursArticles(@Param("debut") Instant debut, @Param("fin") Instant fin,
                                     Pageable pageable);

    /** @return lignes [idClient, nom, email, nombre de commandes, total dépensé] */
    @Query("""
           select cl.id, cl.nom, cl.email, count(distinct c.id), coalesce(sum(p.montant), 0)
           from Paiement p
           join p.commande c
           join c.client cl
           where p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.PAYE
             and p.datePaiement >= :debut and p.datePaiement < :fin
           group by cl.id, cl.nom, cl.email
           order by sum(p.montant) desc
           """)
    List<Object[]> meilleursClients(@Param("debut") Instant debut, @Param("fin") Instant fin,
                                    Pageable pageable);

    // ==================================================================
    //  Clients
    // ==================================================================

    @Query("""
           select count(pe) from Personne pe
           where pe.role = com.example.E_commerce.Enum.Role.USER
             and pe.dateInscription is not null
             and pe.dateInscription >= :debut and pe.dateInscription < :fin
           """)
    long nouveauxClients(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("""
           select count(pe) from Personne pe
           where pe.role = com.example.E_commerce.Enum.Role.USER and pe.actif = :actif
           """)
    long compterClientsParActivation(@Param("actif") boolean actif);

    // ==================================================================
    //  Ce qui demande une action
    // ==================================================================

    /** Commandes réglées qui attendent d'être préparées. */
    @Query("""
           select count(c) from Commande c
           where c.statut = com.example.E_commerce.Enum.StatutCommande.PAYEE
           """)
    long commandesAPreparer();

    /** Paiements encore en vol, toutes périodes confondues. */
    @Query("""
           select count(p) from Paiement p
           where p.statutPaiement in (com.example.E_commerce.Enum.StatutPaiement.EN_ATTENTE,
                                      com.example.E_commerce.Enum.StatutPaiement.EN_COURS)
           """)
    long paiementsEnCours();

    /** Paiements en vol dont le délai est dépassé : signe d'un souci. */
    @Query("""
           select count(p) from Paiement p
           where p.statutPaiement in (com.example.E_commerce.Enum.StatutPaiement.EN_ATTENTE,
                                      com.example.E_commerce.Enum.StatutPaiement.EN_COURS)
             and p.dateExpiration is not null and p.dateExpiration < :maintenant
           """)
    long paiementsBloques(@Param("maintenant") Instant maintenant);

    /** @return lignes [idArticle, libelle, stock, nomCategorie] sous le seuil */
    @Query("""
           select a.id, a.libelle, a.stock, cat.nomCategorie
           from Article a join a.categorie cat
           where a.stock is null or a.stock <= :seuil
           order by a.stock asc
           """)
    List<Object[]> stockFaible(@Param("seuil") int seuil, Pageable pageable);

    @Query("select count(a) from Article a where a.stock is null or a.stock <= :seuil")
    long compterStockFaible(@Param("seuil") int seuil);

    @Query("select count(a) from Article a where a.stock = 0")
    long compterRuptures();

    // ==================================================================
    //  Journée en cours (raccourci)
    // ==================================================================

    @Query("""
           select coalesce(sum(p.montant), 0) from Paiement p
           where p.statutPaiement = com.example.E_commerce.Enum.StatutPaiement.PAYE
             and cast(p.datePaiement as LocalDate) = :jour
           """)
    BigDecimal chiffreAffairesDuJour(@Param("jour") LocalDate jour);
}
