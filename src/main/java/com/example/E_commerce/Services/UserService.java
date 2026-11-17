package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.AvisResponseDto;
import com.example.E_commerce.Dto.CommandeResponseDto;
import com.example.E_commerce.Dto.PanierResponseDto;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Exception.RegleMetierException;
import com.example.E_commerce.Exception.RequeteInvalideException;
import com.example.E_commerce.Exception.RessourceIntrouvableException;
import com.example.E_commerce.Models.*;
import com.example.E_commerce.Repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Garde-fou : evite qu'un client remplisse son panier a l'infini. */
    private static final int QUANTITE_MAX_PAR_LIGNE = 20;

    private final ArticleRepository articleRepository;
    private final PagnierRepository pagnierRepository;
    private final PersonneRepository personneRepository;
    private final CommandeRepository commandeRepository;
    private final AvisRepository avisRepository;

    // ==================================================================
    //  Commande
    // ==================================================================

    /**
     * Cree la commande a partir du panier et <b>reserve</b> le stock.
     *
     * <p>Changements par rapport a la version precedente :</p>
     * <ul>
     *   <li>le montant n'est plus fourni par le client : il etait possible de
     *       poster un montant de 1 FCFA et de valider la commande ;</li>
     *   <li>{@code montantTotal} est reellement enregistre sur la commande,
     *       au prix du jour, alors qu'il restait null auparavant ;</li>
     *   <li>le stock est decremente sous verrou pessimiste, sinon deux
     *       commandes simultanees du dernier exemplaire passent toutes les
     *       deux et le stock devient negatif ;</li>
     *   <li>plus aucun paiement n'est marque PAYE d'office : la commande nait
     *       EN_ATTENTE et c'est l'operateur qui tranche.</li>
     * </ul>
     */
    @Transactional
    public Commande creerCommandeDepuisPanier(Long idClient) {

        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Client", idClient));

        Pagnier panier = pagnierRepository.chargerComplet(idClient)
                .orElseThrow(() -> new RegleMetierException("Votre panier est vide"));

        if (panier.getLignes().isEmpty()) {
            throw new RegleMetierException("Votre panier est vide");
        }

        Commande commande = new Commande();
        commande.setClient(client);
        commande.setDateCommande(LocalDate.now());
        commande.setStatut(StatutCommande.EN_ATTENTE);
        commande.setLibelle("Commande du " + LocalDate.now());

        List<LigneCommande> lignes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (LignePagnier lignePanier : panier.getLignes()) {

            // Verrou sur la ligne article pendant toute la transaction :
            // deux commandes concurrentes sont serialisees ici.
            Article article = articleRepository.verrouillerPourStock(lignePanier.getArticle().getId())
                    .orElseThrow(() -> RessourceIntrouvableException.de(
                            "Article", lignePanier.getArticle().getId()));

            if (article.getStock() == null || article.getStock() < lignePanier.getQuantite()) {
                throw new RegleMetierException("Stock insuffisant pour : " + article.getLibelle()
                        + " (reste " + article.getStock() + ")");
            }

            LigneCommande ligne = new LigneCommande();
            ligne.setArticle(article);
            ligne.setQuantite(lignePanier.getQuantite());
            // On fige le prix : une hausse ulterieure ne doit pas changer
            // le montant d'une commande deja passee.
            ligne.setPrixUnitaire(article.getPrix());
            ligne.setCommande(commande);
            lignes.add(ligne);

            total = total.add(article.getPrix().multiply(BigDecimal.valueOf(lignePanier.getQuantite())));

            article.setStock(article.getStock() - lignePanier.getQuantite());
            articleRepository.save(article);
        }

        commande.setLignes(lignes);
        commande.setMontantTotal(total);
        Commande enregistree = commandeRepository.save(commande);

        // Le panier n'est vide qu'une fois la commande ecrite.
        panier.getLignes().clear();
        pagnierRepository.save(panier);

        log.info("Commande {} creee pour le client {} : {} {}",
                enregistree.getId(), idClient, total, "XAF");
        return enregistree;
    }

    @Transactional(readOnly = true)
    public List<CommandeResponseDto> voirMesCommandes(Long idClient) {
        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Client", idClient));
        return commandeRepository.chargerCompletesParClient(client)
                .stream()
                .map(CommandeResponseDto::of)
                .toList();
    }

    // ==================================================================
    //  Panier
    // ==================================================================

    @Transactional
    public PanierResponseDto ajoutPagnier(Long idClient, Long idArticle, Integer quantiteDemandee) {

        int quantite = quantiteDemandee == null ? 1 : quantiteDemandee;
        if (quantite < 1) {
            throw new RequeteInvalideException("La quantite doit etre au moins 1");
        }

        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Client", idClient));

        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));

        Pagnier panier = pagnierRepository.chargerComplet(idClient).orElseGet(() -> {
            Pagnier nouveau = new Pagnier();
            nouveau.setClient(client);
            nouveau.setDateAjout(LocalDate.now());
            nouveau.setLignes(new ArrayList<>());
            return pagnierRepository.save(nouveau);
        });

        Optional<LignePagnier> existante = panier.getLignes().stream()
                .filter(l -> l.getArticle().getId().equals(idArticle))
                .findFirst();

        int quantiteFinale = existante.map(LignePagnier::getQuantite).orElse(0) + quantite;

        if (quantiteFinale > QUANTITE_MAX_PAR_LIGNE) {
            throw new RegleMetierException(
                    "Maximum " + QUANTITE_MAX_PAR_LIGNE + " exemplaires par article");
        }
        // Le stock est verifie ici a titre indicatif ; la verification qui
        // fait foi est celle de creerCommandeDepuisPanier, sous verrou.
        if (article.getStock() == null || article.getStock() < quantiteFinale) {
            throw new RegleMetierException("Stock insuffisant pour : " + article.getLibelle());
        }

        if (existante.isPresent()) {
            existante.get().setQuantite(quantiteFinale);
        } else {
            LignePagnier ligne = new LignePagnier();
            ligne.setArticle(article);
            ligne.setQuantite(quantite);
            ligne.setPagnier(panier);
            panier.getLignes().add(ligne);
        }

        return PanierResponseDto.of(pagnierRepository.save(panier));
    }

    @Transactional(readOnly = true)
    public PanierResponseDto voirMonPanier(Long idClient) {
        Pagnier panier = pagnierRepository.chargerComplet(idClient)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun panier pour ce client"));
        return PanierResponseDto.of(panier);
    }

    @Transactional
    public void viderPanier(Long idClient) {
        pagnierRepository.chargerComplet(idClient).ifPresent(panier -> {
            panier.getLignes().clear();
            pagnierRepository.save(panier);
        });
    }

    @Transactional
    public PanierResponseDto supprimerArticlePanier(Long idClient, Long idArticle) {
        Pagnier panier = pagnierRepository.chargerComplet(idClient)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun panier pour ce client"));
        boolean retire = panier.getLignes().removeIf(l -> l.getArticle().getId().equals(idArticle));
        if (!retire) {
            throw new RessourceIntrouvableException("Cet article n'est pas dans votre panier");
        }
        return PanierResponseDto.of(pagnierRepository.save(panier));
    }

    @Transactional
    public PanierResponseDto diminuerQuantite(Long idClient, Long idArticle) {
        Pagnier panier = pagnierRepository.chargerComplet(idClient)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun panier pour ce client"));

        LignePagnier ligne = panier.getLignes().stream()
                .filter(l -> l.getArticle().getId().equals(idArticle))
                .findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Cet article n'est pas dans votre panier"));

        if (ligne.getQuantite() <= 1) {
            panier.getLignes().remove(ligne);
        } else {
            ligne.setQuantite(ligne.getQuantite() - 1);
        }
        return PanierResponseDto.of(pagnierRepository.save(panier));
    }

    // ==================================================================
    //  Avis
    // ==================================================================

    @Transactional
    public AvisResponseDto laisserAvis(Long idClient, Long idArticle, Integer note, String commentaire) {

        if (note == null || note < 1 || note > 5) {
            throw new RequeteInvalideException("La note doit etre comprise entre 1 et 5");
        }

        Personne client = personneRepository.findById(idClient)
                .orElseThrow(() -> RessourceIntrouvableException.de("Client", idClient));

        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> RessourceIntrouvableException.de("Article", idArticle));

        // On n'accepte un avis que sur un article effectivement paye :
        // sinon n'importe qui peut noter en creant une commande non reglee.
        boolean aAchete = commandeRepository.chargerCompletesParClient(client).stream()
                .filter(c -> c.getStatut() == StatutCommande.PAYEE
                          || c.getStatut() == StatutCommande.EN_COURS
                          || c.getStatut() == StatutCommande.LIVREE)
                .flatMap(c -> c.getLignes().stream())
                .anyMatch(l -> l.getArticle().getId().equals(idArticle));

        if (!aAchete) {
            throw new RegleMetierException(
                    "Vous devez avoir achete et paye cet article pour laisser un avis");
        }
        if (avisRepository.existsByAuteurAndArticle(client, article)) {
            throw new RegleMetierException("Vous avez deja laisse un avis sur cet article");
        }

        Avis avis = new Avis();
        avis.setAuteur(client);
        avis.setArticle(article);
        avis.setNote(note);
        avis.setCommentaire(commentaire);
        avis.setDateAvis(LocalDate.now());

        return AvisResponseDto.of(avisRepository.save(avis));
    }
}
