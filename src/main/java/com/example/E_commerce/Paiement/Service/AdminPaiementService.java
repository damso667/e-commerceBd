package com.example.E_commerce.Paiement.Service;

import com.example.E_commerce.Dto.CommandeResponseDto;
import com.example.E_commerce.Dto.PageReponse;
import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Exception.RegleMetierException;
import com.example.E_commerce.Exception.RessourceIntrouvableException;
import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Commande;
import com.example.E_commerce.Models.LigneCommande;
import com.example.E_commerce.Models.Paiement;
import com.example.E_commerce.Paiement.Dto.*;
import com.example.E_commerce.Paiement.Gateway.RegistrePasserelles;
import com.example.E_commerce.Repository.ArticleRepository;
import com.example.E_commerce.Repository.CommandeRepository;
import com.example.E_commerce.Repository.EvenementPaiementRepository;
import com.example.E_commerce.Repository.PaiementRepository;
import com.example.E_commerce.Repository.Recherches;
import com.example.E_commerce.Services.JournalAdminService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Back-office du paiement : consultation, instruction d'une reclamation,
 * remboursement.
 *
 * <p>Separe de {@link PaiementService} a dessein : celui-ci porte les
 * invariants du parcours client et doit rester petit et verifiable. Les
 * besoins du support (recherche, fiche complete, remboursement) n'ont pas
 * a l'alourdir.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminPaiementService {

    private static final Logger log = LoggerFactory.getLogger(AdminPaiementService.class);

    private final PaiementRepository paiementRepository;
    private final CommandeRepository commandeRepository;
    private final ArticleRepository articleRepository;
    private final EvenementPaiementRepository evenementRepository;
    private final RegistrePasserelles registre;
    private final JournalAdminService journal;

    // ==================================================================
    //  Consultation
    // ==================================================================

    @Transactional(readOnly = true)
    public PageReponse<PaiementAdminDto> rechercher(StatutPaiement statut,
                                                    FournisseurPaiement fournisseur,
                                                    LocalDate debut, LocalDate fin,
                                                    String recherche, Pageable pageable) {
        return PageReponse.de(
                paiementRepository.findAll(
                        Recherches.paiements(statut, fournisseur,
                                debutDe(debut), finDe(fin), recherche),
                        pageable),
                PaiementAdminDto::of);
    }

    /** Fiche complete : paiement, commande, tentatives precedentes, callbacks. */
    @Transactional(readOnly = true)
    public PaiementDetailAdminDto detail(String reference) {
        Paiement paiement = paiementRepository.findByReference(reference)
                .orElseThrow(() -> RessourceIntrouvableException.de("Paiement", reference));

        Long idCommande = paiement.getCommande().getId();
        Commande commande = commandeRepository.chargerComplete(idCommande)
                .orElseThrow(() -> RessourceIntrouvableException.de("Commande", idCommande));

        List<PaiementAdminDto> tentatives = paiementRepository
                .findByCommandeIdOrderByDateCreationAsc(idCommande)
                .stream()
                .map(PaiementAdminDto::of)
                .toList();

        List<EvenementPaiementDto> evenements = evenementRepository
                .findByReferencePaiementOrderByDateReceptionAsc(reference)
                .stream()
                .map(EvenementPaiementDto::of)
                .toList();

        return new PaiementDetailAdminDto(
                PaiementAdminDto.of(paiement),
                CommandeResponseDto.of(commande),
                tentatives,
                evenements);
    }

    /** Historique des tentatives d'une commande. */
    @Transactional(readOnly = true)
    public List<PaiementAdminDto> tentativesDeCommande(Long idCommande) {
        if (!commandeRepository.existsById(idCommande)) {
            throw RessourceIntrouvableException.de("Commande", idCommande);
        }
        return paiementRepository.findByCommandeIdOrderByDateCreationAsc(idCommande)
                .stream()
                .map(PaiementAdminDto::of)
                .toList();
    }

    // ==================================================================
    //  Remboursement
    // ==================================================================

    /**
     * Rembourse un paiement reussi.
     *
     * <p>Deroulement :</p>
     * <ol>
     *   <li>verrou sur la ligne de paiement, pour ne pas rembourser deux fois
     *       si deux administrateurs cliquent en meme temps ;</li>
     *   <li>transition PAYE -&gt; REMBOURSE validee par la machine a etats :
     *       un paiement non reussi, ou deja rembourse, est refuse ;</li>
     *   <li>appel a l'operateur, qui peut ne pas savoir le faire ;</li>
     *   <li>annulation de la commande et restitution du stock <b>seulement</b>
     *       si la marchandise n'est pas encore partie ;</li>
     *   <li>trace au journal avec le motif.</li>
     * </ol>
     *
     * <p>Idempotence : l'etat terminal REMBOURSE bloque tout second appel,
     * et l'en-tete Idempotency-Key du controleur rend le rejeu reseau
     * inoffensif.</p>
     */
    @Transactional
    public ResultatRemboursementDto rembourser(String reference, String motif) {

        Paiement paiement = paiementRepository.verrouillerParReference(reference)
                .orElseThrow(() -> RessourceIntrouvableException.de("Paiement", reference));

        if (paiement.getStatutPaiement() == StatutPaiement.REMBOURSE) {
            throw new RegleMetierException("Ce paiement a deja ete rembourse");
        }
        if (!paiement.getStatutPaiement().peutAllerVers(StatutPaiement.REMBOURSE)) {
            throw new RegleMetierException(
                    "Seul un paiement reussi peut etre rembourse (statut actuel : "
                    + paiement.getStatutPaiement() + ")");
        }

        boolean automatique;
        try {
            automatique = registre.pour(paiement.getFournisseur())
                    .rembourser(paiement.getReference(), paiement.getReferenceOperateur(),
                            paiement.getMontant());
        } catch (RuntimeException e) {
            // L'operateur a repondu non : on n'invente pas un succes.
            log.error("Remboursement refuse par l'operateur pour {}", reference, e);
            throw new RegleMetierException(
                    "L'operateur a refuse le remboursement : " + e.getMessage());
        }

        paiement.setStatutPaiement(StatutPaiement.REMBOURSE);
        paiement.setDateMiseAJour(Instant.now());
        paiement.setMessage(tronquer("Rembourse : " + motif));
        paiement.setCodeErreur(automatique ? "REMBOURSE" : "REMBOURSEMENT_A_EFFECTUER");
        paiementRepository.save(paiement);

        Commande commande = commandeRepository.chargerComplete(paiement.getCommande().getId())
                .orElseThrow();
        String consigne = annulerSiPossible(commande);

        journal.tracer("PAIEMENT_REMBOURSE", JournalAdminService.PAIEMENT, reference,
                "Montant " + paiement.getMontant() + " " + paiement.getDevise()
                + " | commande " + commande.getId()
                + " | automatique=" + automatique
                + " | motif : " + motif);

        log.info("Paiement {} rembourse (automatique={}) : {}", reference, automatique, motif);

        return new ResultatRemboursementDto(
                PaiementAdminDto.of(paiement),
                automatique,
                (automatique
                        ? "Remboursement execute par l'operateur."
                        : "ATTENTION : l'operateur ne gere pas le remboursement automatique. "
                          + "Effectuez le transfert de " + paiement.getMontant() + " "
                          + paiement.getDevise() + " vers le " + paiement.getNumeroPayeur()
                          + " manuellement.")
                + " " + consigne);
    }

    /**
     * Rend le stock seulement si la commande n'est pas partie.
     *
     * <p>Rembourser une commande deja livree est legitime (geste commercial,
     * retour), mais recrediter le stock dans ce cas creerait du stock
     * fantome. On le dit explicitement a l'administrateur.</p>
     */
    private String annulerSiPossible(Commande commande) {
        StatutCommande statut = commande.getStatut();

        if (statut == StatutCommande.LIVREE) {
            return "La commande etant deja livree, son statut et le stock n'ont pas ete modifies.";
        }
        if (statut == StatutCommande.ANNULEE) {
            return "La commande etait deja annulee.";
        }

        commande.setStatut(StatutCommande.ANNULEE);
        for (LigneCommande ligne : commande.getLignes()) {
            Article article = articleRepository
                    .verrouillerPourStock(ligne.getArticle().getId())
                    .orElse(null);
            if (article != null) {
                article.setStock(article.getStock() + ligne.getQuantite());
                articleRepository.save(article);
            }
        }
        commandeRepository.save(commande);
        return "Commande annulee et stock restitue.";
    }

    // ==================================================================

    static Instant debutDe(LocalDate debut) {
        return debut == null ? null : debut.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    /** Borne haute exclusive : "fin = aujourd'hui" doit inclure la journee. */
    static Instant finDe(LocalDate fin) {
        return fin == null ? null : fin.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static String tronquer(String message) {
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
