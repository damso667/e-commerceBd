package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.*;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Models.*;
import com.example.E_commerce.Repository.*;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.E_commerce.Enum.StatutCommande.EN_ATTENTE;

@Service
@AllArgsConstructor
public class UserService {

    private final ArticleRepository articleRepository;
    private final PagnierRepository pagnierRepository;
    private final PersonneRepository personneRepository;
    private final CommandeRepository commandeRepository;
    private final PaiementRepository paiementRepository;
    private final AvisRepository avisRepository;


    @Transactional
    public CommandeResponseDto passerCommande(Long idClient, String modePaiement, BigDecimal montantClient) {

        // 1. Récupérer le panier du client
        Pagnier pagnier = pagnierRepository.findByClientId(idClient).orElse(null);

        // 2. Vérifier que le panier n'est pas vide
        if (pagnier == null || pagnier.getLignes().isEmpty()) {
            throw new IllegalArgumentException("Le panier est vide");
        }

        List<LignePagnier> lignesPanier = pagnier.getLignes();

        // 3. Vérifier le stock de chaque article
        for (LignePagnier ligne : lignesPanier) {
            Article article = ligne.getArticle();
            if (article.getStock() < ligne.getQuantite()) {
                throw new IllegalArgumentException(
                        "Stock insuffisant pour : " + article.getLibelle()
                );
            }
        }

        // Calculer le montant total réel côté serveur
        BigDecimal montantTotal = lignesPanier.stream()
                .map(l -> l.getArticle().getPrix()
                        .multiply(BigDecimal.valueOf(l.getQuantite())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //  Comparer avec le montant envoyé par le client
        if (montantClient.compareTo(montantTotal) < 0) {
            throw new IllegalArgumentException(
                    "Montant insuffisant. Montant attendu : " + montantTotal + " FCFA"
            );
        }

        // Créer la commande
        Commande commande = new Commande();
        commande.setClient(personneRepository.findById(idClient).orElseThrow());
        commande.setDateCommande(LocalDate.now());
        commande.setStatut(StatutCommande.EN_ATTENTE);

        // Créer les lignes de commande + déduire le stock
        List<LigneCommande> lignesCommande = new ArrayList<>();
        List<Article> articlesModifies = new ArrayList<>();

        for (LignePagnier ligne : lignesPanier) {
            Article article = ligne.getArticle();

            LigneCommande ligneCommande = new LigneCommande();
            ligneCommande.setArticle(article);
            ligneCommande.setQuantite(ligne.getQuantite());
            ligneCommande.setPrixUnitaire(article.getPrix());
            ligneCommande.setCommande(commande);
            lignesCommande.add(ligneCommande);

            article.setStock(article.getStock() - ligne.getQuantite());
            articlesModifies.add(article);
        }

        articleRepository.saveAll(articlesModifies);
        commande.setLignes(lignesCommande);
        Commande commandeSauvegardee = commandeRepository.save(commande);

        //  Créer le paiement
        Paiement paiement = new Paiement();
        paiement.setMontant(montantTotal);
        paiement.setDatePaiement(LocalDate.now());
        paiement.setModePaiement(modePaiement);
        paiement.setStatutPaiement(StatutPaiement.PAYE);
        paiement.setCommande(commandeSauvegardee);
        paiementRepository.save(paiement);

        //  Vider le panier
        pagnier.getLignes().clear();
        pagnierRepository.save(pagnier);

        // Retourner la commande
        return CommandeResponseDto.of(commandeSauvegardee);
    }



    @Transactional
    public PanierResponseDto ajoutPagnier(Long idClient, Long idArticle) {

        //  Vérifier que le client existe
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));

        //Vérifier que l'article existe
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable"));

        // Vérifier le stock
        if (article.getStock() < 1) {
            throw new IllegalArgumentException("Stock insuffisant pour : " + article.getLibelle());
        }

        //  Récupérer ou créer le panier
        Pagnier pagnier = pagnierRepository.findByClientId(idClient).orElse(null);
        if (pagnier == null) {
            pagnier = new Pagnier();
            pagnier.setClient(client);
            pagnier.setDateAjout(LocalDate.now());
            pagnier.setLignes(new ArrayList<>());
            pagnierRepository.save(pagnier);
        }

        //  Vérifier si l'article est déjà dans le panier
        Optional<LignePagnier> ligneExistante = pagnier.getLignes()
                .stream()
                .filter(l -> l.getArticle().getId().equals(idArticle))
                .findFirst();

        if (ligneExistante.isPresent()) {
            // Incrémenter de 1
            LignePagnier ligne = ligneExistante.get();
            ligne.setQuantite(ligne.getQuantite() + 1);
        } else {
            // Créer une nouvelle ligne avec quantité 1
            LignePagnier nouvelleLigne = new LignePagnier();
            nouvelleLigne.setArticle(article);
            nouvelleLigne.setQuantite(1);
            nouvelleLigne.setPagnier(pagnier);
            pagnier.getLignes().add(nouvelleLigne);
        }

        return PanierResponseDto.of(pagnierRepository.save(pagnier));
    }

    public PanierResponseDto voirMonPanier(Long idClient) {
        Pagnier pagnier = pagnierRepository.findByClientId(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Panier introuvable"));
        return PanierResponseDto.of(pagnier);
    }
    public List<CommandeResponseDto> voirMesCommandes(Long idClient) {
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));
        return commandeRepository.findByClient(client)
                .stream()
                .map(CommandeResponseDto::of)
                .toList();
    }

    @Transactional
    public Avis laisserAvis(Long idClient, Long idArticle, Integer note, String commentaire) {

        //  Vérifier que le client existe
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));

        //  Vérifier que l'article existe
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable"));

        //Vérifier que le client a déjà commandé cet article
        boolean aCommande = commandeRepository.findByClient(client)
                .stream()
                .flatMap(c -> c.getLignes().stream())
                .anyMatch(l -> l.getArticle().getId().equals(idArticle));

        if (!aCommande) {
            throw new IllegalArgumentException(
                    "Vous devez avoir commandé cet article pour laisser un avis"
            );
        }

        // Vérifier que la note est entre 1 et 5
        if (note < 1 || note > 5) {
            throw new IllegalArgumentException("La note doit être entre 1 et 5");
        }

        // Créer l'avis
        Avis avis = new Avis();
        avis.setAuteur(client);
        avis.setArticle(article);
        avis.setNote(note);
        avis.setCommentaire(commentaire);
        avis.setDateAvis(LocalDate.now());

        return avisRepository.save(avis);
    }

    @Transactional
    public void viderPanier(Long idClient) {
        Pagnier pagnier = pagnierRepository.findByClientId(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Panier introuvable"));
        pagnier.getLignes().clear();
        pagnierRepository.save(pagnier);
    }

    @Transactional
    public PanierResponseDto supprimerArticlePanier(Long idClient, Long idArticle) {
        Pagnier pagnier = pagnierRepository.findByClientId(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Panier introuvable"));

        pagnier.getLignes().removeIf(l -> l.getArticle().getId().equals(idArticle));
        return PanierResponseDto.of(pagnierRepository.save(pagnier));
    }

    @Transactional
    public PanierResponseDto diminuerQuantite(Long idClient, Long idArticle) {
        Pagnier pagnier = pagnierRepository.findByClientId(idClient)
                .orElseThrow(() -> new IllegalArgumentException("Panier introuvable"));

        LignePagnier ligne = pagnier.getLignes().stream()
                .filter(l -> l.getArticle().getId().equals(idArticle))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Article non trouvé dans le panier"));

        if (ligne.getQuantite() <= 1) {
            // Si quantité = 1, on supprime la ligne
            pagnier.getLignes().remove(ligne);
        } else {
            // Sinon on décrémente de 1
            ligne.setQuantite(ligne.getQuantite() - 1);
        }

        return PanierResponseDto.of(pagnierRepository.save(pagnier));
    }








}
