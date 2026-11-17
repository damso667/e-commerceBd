package com.example.E_commerce.Repository;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Models.*;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Critères de recherche du back-office, écrits avec l'API Criteria.
 *
 * <p><b>Pourquoi pas du JPQL avec « {@code :param is null or ...} » ?</b>
 * Parce que PostgreSQL refuse ce motif : un paramètre qui n'apparaît que
 * dans un test {@code is null} n'a aucun contexte permettant d'en déduire
 * le type, et le serveur répond
 * {@code could not determine data type of parameter $5}. Les contournements
 * (casts explicites, valeurs sentinelles) fonctionnent mais encombrent la
 * requête.</p>
 *
 * <p>Avec l'API Criteria, un critère absent n'est tout simplement <b>pas
 * ajouté</b> à la requête. Conséquences : aucun paramètre non typé, un SQL
 * qui ne contient que les filtres réellement demandés, et donc des plans
 * d'exécution qui peuvent utiliser les index.</p>
 *
 * <p>Convention de bornes partout : {@code debut} inclusif, {@code fin}
 * exclusif.</p>
 */
public final class Recherches {

    private Recherches() {
    }

    // ==================================================================
    //  Commandes
    // ==================================================================

    public static Specification<Commande> commandes(StatutCommande statut, Long idClient,
                                                    Instant debut, Instant fin,
                                                    BigDecimal montantMin, String recherche) {
        return (racine, requete, cb) -> {
            List<Predicate> criteres = new ArrayList<>();
            Join<Commande, Personne> client = racine.join("client");

            if (statut != null) {
                criteres.add(cb.equal(racine.get("statut"), statut));
            }
            if (idClient != null) {
                criteres.add(cb.equal(client.get("id"), idClient));
            }
            if (debut != null) {
                criteres.add(cb.greaterThanOrEqualTo(racine.get("dateCreation"), debut));
            }
            if (fin != null) {
                criteres.add(cb.lessThan(racine.get("dateCreation"), fin));
            }
            if (montantMin != null) {
                criteres.add(cb.greaterThanOrEqualTo(racine.get("montantTotal"), montantMin));
            }
            if (renseigne(recherche)) {
                String motif = motif(recherche);
                List<Predicate> ou = new ArrayList<>(List.of(
                        contient(cb, client.get("nom"), motif),
                        contient(cb, client.get("prenom"), motif),
                        contient(cb, client.get("email"), motif),
                        contient(cb, racine.get("libelle"), motif)));
                // Permet de coller un numéro de commande dans le même champ.
                Long id = versLong(recherche);
                if (id != null) {
                    ou.add(cb.equal(racine.get("id"), id));
                }
                criteres.add(cb.or(ou.toArray(new Predicate[0])));
            }
            return cb.and(criteres.toArray(new Predicate[0]));
        };
    }

    // ==================================================================
    //  Paiements
    // ==================================================================

    public static Specification<Paiement> paiements(StatutPaiement statut,
                                                    FournisseurPaiement fournisseur,
                                                    Instant debut, Instant fin,
                                                    String recherche) {
        return (racine, requete, cb) -> {
            List<Predicate> criteres = new ArrayList<>();

            if (statut != null) {
                criteres.add(cb.equal(racine.get("statutPaiement"), statut));
            }
            if (fournisseur != null) {
                criteres.add(cb.equal(racine.get("fournisseur"), fournisseur));
            }
            if (debut != null) {
                criteres.add(cb.greaterThanOrEqualTo(racine.get("dateCreation"), debut));
            }
            if (fin != null) {
                criteres.add(cb.lessThan(racine.get("dateCreation"), fin));
            }
            if (renseigne(recherche)) {
                // La jointure n'est posée que si l'on cherche sur le client.
                Join<Paiement, Commande> commande = racine.join("commande");
                Join<Commande, Personne> client = commande.join("client");
                String motif = motif(recherche);
                criteres.add(cb.or(
                        contient(cb, racine.get("reference"), motif),
                        contient(cb, racine.get("referenceOperateur"), motif),
                        contient(cb, racine.get("numeroPayeur"), motif),
                        contient(cb, client.get("email"), motif),
                        contient(cb, client.get("nom"), motif)));
            }
            return cb.and(criteres.toArray(new Predicate[0]));
        };
    }

    // ==================================================================
    //  Comptes
    // ==================================================================

    public static Specification<Personne> comptes(Role role, Boolean actif, String recherche) {
        return (racine, requete, cb) -> {
            List<Predicate> criteres = new ArrayList<>();

            if (role != null) {
                criteres.add(cb.equal(racine.get("role"), role));
            }
            if (actif != null) {
                criteres.add(cb.equal(racine.get("actif"), actif));
            }
            if (renseigne(recherche)) {
                String motif = motif(recherche);
                criteres.add(cb.or(
                        contient(cb, racine.get("nom"), motif),
                        contient(cb, racine.get("prenom"), motif),
                        contient(cb, racine.get("email"), motif),
                        contient(cb, racine.get("numeroTelephone"), motif)));
            }
            return cb.and(criteres.toArray(new Predicate[0]));
        };
    }

    // ==================================================================
    //  Articles
    // ==================================================================

    public static Specification<Article> articles(Long idCategorie, Integer stockMax,
                                                  BigDecimal prixMin, BigDecimal prixMax,
                                                  String recherche) {
        return (racine, requete, cb) -> {
            List<Predicate> criteres = new ArrayList<>();
            Join<Article, Categorie> categorie = racine.join("categorie");

            if (idCategorie != null) {
                criteres.add(cb.equal(categorie.get("idCategorie"), idCategorie));
            }
            if (stockMax != null) {
                criteres.add(cb.lessThanOrEqualTo(racine.get("stock"), stockMax));
            }
            if (prixMin != null) {
                criteres.add(cb.greaterThanOrEqualTo(racine.get("prix"), prixMin));
            }
            if (prixMax != null) {
                criteres.add(cb.lessThanOrEqualTo(racine.get("prix"), prixMax));
            }
            if (renseigne(recherche)) {
                String motif = motif(recherche);
                criteres.add(cb.or(
                        contient(cb, racine.get("libelle"), motif),
                        contient(cb, racine.get("description"), motif),
                        contient(cb, categorie.get("nomCategorie"), motif)));
            }
            return cb.and(criteres.toArray(new Predicate[0]));
        };
    }

    // ==================================================================
    //  Avis
    // ==================================================================

    public static Specification<Avis> avis(Long idArticle, Integer noteMin, Integer noteMax,
                                           String recherche) {
        return (racine, requete, cb) -> {
            List<Predicate> criteres = new ArrayList<>();
            Join<Avis, Personne> auteur = racine.join("auteur");
            Join<Avis, Article> article = racine.join("article");

            if (idArticle != null) {
                criteres.add(cb.equal(article.get("id"), idArticle));
            }
            if (noteMin != null) {
                criteres.add(cb.greaterThanOrEqualTo(racine.get("note"), noteMin));
            }
            if (noteMax != null) {
                criteres.add(cb.lessThanOrEqualTo(racine.get("note"), noteMax));
            }
            if (renseigne(recherche)) {
                String motif = motif(recherche);
                criteres.add(cb.or(
                        contient(cb, racine.get("commentaire"), motif),
                        contient(cb, auteur.get("nom"), motif),
                        contient(cb, auteur.get("email"), motif),
                        contient(cb, article.get("libelle"), motif)));
            }
            return cb.and(criteres.toArray(new Predicate[0]));
        };
    }

    // ==================================================================
    //  Journal d'administration
    // ==================================================================

    public static Specification<JournalAdmin> journal(String acteur, String action,
                                                      String cibleType, String cibleId,
                                                      Instant debut, Instant fin) {
        return (racine, requete, cb) -> {
            List<Predicate> criteres = new ArrayList<>();

            if (renseigne(acteur)) {
                criteres.add(contient(cb, racine.get("acteur"), motif(acteur)));
            }
            if (renseigne(action)) {
                criteres.add(cb.equal(racine.get("action"), action.trim()));
            }
            if (renseigne(cibleType)) {
                criteres.add(cb.equal(racine.get("cibleType"), cibleType.trim()));
            }
            if (renseigne(cibleId)) {
                criteres.add(cb.equal(racine.get("cibleId"), cibleId.trim()));
            }
            if (debut != null) {
                criteres.add(cb.greaterThanOrEqualTo(racine.get("dateAction"), debut));
            }
            if (fin != null) {
                criteres.add(cb.lessThan(racine.get("dateAction"), fin));
            }
            return cb.and(criteres.toArray(new Predicate[0]));
        };
    }

    // ==================================================================
    //  Utilitaires
    // ==================================================================

    private static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    private static String motif(String recherche) {
        return "%" + recherche.trim().toLowerCase() + "%";
    }

    /** Insensible à la casse, et tolérant aux colonnes nulles. */
    private static Predicate contient(jakarta.persistence.criteria.CriteriaBuilder cb,
                                      Expression<String> colonne, String motif) {
        return cb.like(cb.lower(cb.coalesce(colonne, "")), motif);
    }

    private static Long versLong(String valeur) {
        try {
            return Long.valueOf(valeur.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
