package com.example.E_commerce.Paiement.Service;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutCommande;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Exception.AccesRefuseException;
import com.example.E_commerce.Exception.RegleMetierException;
import com.example.E_commerce.Exception.RequeteInvalideException;
import com.example.E_commerce.Exception.RessourceIntrouvableException;
import com.example.E_commerce.Models.*;
import com.example.E_commerce.Paiement.Config.PaiementProperties;
import com.example.E_commerce.Paiement.Dto.PaiementResponseDto;
import com.example.E_commerce.Paiement.Gateway.*;
import com.example.E_commerce.Repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Coeur du paiement : creation des tentatives, application des resultats,
 * liberation du stock, reconciliation.
 *
 * <p>Ce service ne connait aucun operateur. Il parle a
 * {@link PasserellePaiement} via {@link RegistrePasserelles}. C'est pour cela
 * que passer de la simulation aux vraies API ne demande aucune modification
 * ici.</p>
 *
 * <h2>Garanties d'idempotence</h2>
 * <ul>
 *   <li><b>Cote client</b> : en-tete Idempotency-Key, gere en amont par
 *       {@code ServiceIdempotence}.</li>
 *   <li><b>Cote metier</b> : une commande ne peut jamais avoir deux tentatives
 *       actives, et une commande deja reglee refuse toute nouvelle tentative.</li>
 *   <li><b>Cote operateur</b> : la reference interne sert d'identifiant de
 *       transaction, donc rejouer une initiation ne debite pas deux fois.</li>
 *   <li><b>Cote callback</b> : chaque notification est journalisee avec une
 *       contrainte unique (fournisseur, idEvenement) ; un doublon est ignore.
 *       De plus, un statut terminal n'est jamais ecrase.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PaiementService implements RecepteurNotification {

    private static final Logger log = LoggerFactory.getLogger(PaiementService.class);

    /** Statuts pour lesquels on attend encore une reponse de l'operateur. */
    private static final List<StatutPaiement> EN_VOL =
            List.of(StatutPaiement.EN_ATTENTE, StatutPaiement.EN_COURS);

    private final PaiementRepository paiementRepository;
    private final CommandeRepository commandeRepository;
    private final ArticleRepository articleRepository;
    private final EvenementPaiementRepository evenementRepository;
    private final RegistrePasserelles registre;
    private final PaiementProperties properties;

    // ==================================================================
    //  0. Pre-validation
    // ==================================================================

    /**
     * Verifie operateur et numero <b>avant</b> toute creation de commande.
     *
     * <p>Sans ce controle prealable, un numero Orange envoye a MTN faisait
     * echouer l'initiation apres que la commande avait deja ete creee et le
     * stock reserve : la commande restait orpheline, en attente d'un paiement
     * qui n'existait pas.</p>
     */
    public void verifierMoyenDePaiement(FournisseurPaiement fournisseur, String numeroPayeur) {
        PasserellePaiement passerelle = registre.pour(fournisseur);
        if (!passerelle.accepteNumero(numeroPayeur)) {
            FournisseurPaiement suggere = registre.deduireDepuisNumero(numeroPayeur);
            throw new RequeteInvalideException(
                    "Le numero fourni n'est pas un numero " + fournisseur.getLibelle()
                    + (suggere == null ? "" : ". Essayez " + suggere.getLibelle() + "."));
        }
    }

    // ==================================================================
    //  1. Initiation
    // ==================================================================

    /**
     * Lance une tentative de paiement pour une commande.
     *
     * <p>Idempotent par construction : si une tentative est deja en cours ou
     * si la commande est deja reglee, on renvoie la tentative existante au
     * lieu d'en creer une seconde.</p>
     */
    @Transactional
    public PaiementResponseDto initier(Long idCommande, Long idClient,
                                       FournisseurPaiement fournisseur, String numeroPayeur) {

        Commande commande = commandeRepository.chargerComplete(idCommande)
                .orElseThrow(() -> RessourceIntrouvableException.de("Commande", idCommande));

        if (!commande.getClient().getId().equals(idClient)) {
            throw new AccesRefuseException("Cette commande ne vous appartient pas");
        }
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            throw new RegleMetierException("Cette commande a ete annulee, elle ne peut plus etre payee");
        }

        List<Paiement> tentatives =
                paiementRepository.findByCommandeIdOrderByDateCreationAsc(idCommande);

        // Deja regle : on renvoie le paiement reussi, on ne redebite pas.
        Paiement reussi = tentatives.stream()
                .filter(p -> p.getStatutPaiement().estSucces())
                .findFirst().orElse(null);
        if (reussi != null) {
            log.info("Commande {} deja reglee par {} : initiation ignoree", idCommande, reussi.getReference());
            return PaiementResponseDto.of(reussi);
        }

        // Tentative deja en vol : on la renvoie, pas de second debit.
        Paiement enVol = tentatives.stream()
                .filter(p -> !p.getStatutPaiement().estTerminal())
                .findFirst().orElse(null);
        if (enVol != null) {
            log.info("Commande {} : tentative {} deja en cours, initiation ignoree",
                    idCommande, enVol.getReference());
            return PaiementResponseDto.of(enVol);
        }

        verifierMoyenDePaiement(fournisseur, numeroPayeur);
        PasserellePaiement passerelle = registre.pour(fournisseur);

        BigDecimal montant = montantDe(commande);
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegleMetierException("Montant de commande invalide");
        }

        int tentative = tentatives.size() + 1;
        Paiement paiement = new Paiement();
        paiement.setReference(nouvelleReference(idCommande, tentative));
        paiement.setCommande(commande);
        paiement.setMontant(montant);
        paiement.setDevise(properties.getDevise());
        paiement.setFournisseur(fournisseur);
        paiement.setNumeroPayeur(normaliser(numeroPayeur));
        paiement.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        paiement.setTentative(tentative);
        paiement.setDateCreation(Instant.now());
        paiement.setDateExpiration(Instant.now().plus(
                properties.getDelaiExpirationMinutes(), ChronoUnit.MINUTES));
        paiementRepository.saveAndFlush(paiement);

        ResultatPasserelle resultat = passerelle.initier(new DemandePaiement(
                paiement.getReference(),
                montant,
                properties.getDevise(),
                paiement.getNumeroPayeur(),
                "Commande n" + idCommande,
                urlCallbackPour(fournisseur)));

        appliquer(paiement, resultat.statut(), resultat.referenceOperateur(),
                resultat.codeFournisseur(), resultat.message());

        return PaiementResponseDto.of(paiementRepository.save(paiement));
    }

    // ==================================================================
    //  2. Notification entrante (webhook reel ou simulation)
    // ==================================================================

    /**
     * Applique une notification d'operateur. Appelee par le controleur de
     * webhook ET par la passerelle simulee : le chemin de code est donc
     * identique en simulation et en production.
     *
     * <p>Propagation REQUIRES_NEW : la notification arrive sur un autre fil
     * (ordonnanceur ou requete HTTP), elle doit avoir sa propre transaction.</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void traiterNotification(NotificationPaiement notification) {

        // --- Deduplication : la contrainte unique fait le travail -------
        if (evenementRepository.existsByFournisseurAndIdEvenement(
                notification.fournisseur(), notification.idEvenement())) {
            log.info("Notification {} deja traitee, ignoree", notification.idEvenement());
            return;
        }
        EvenementPaiement evenement = new EvenementPaiement();
        evenement.setFournisseur(notification.fournisseur());
        evenement.setIdEvenement(notification.idEvenement());
        evenement.setReferencePaiement(notification.referenceInterne());
        evenement.setStatutRecu(String.valueOf(notification.statut()));
        evenement.setCharge(notification.chargeBrute());
        try {
            evenementRepository.saveAndFlush(evenement);
        } catch (DataIntegrityViolationException doublonConcurrent) {
            // Deux callbacks identiques exactement en meme temps.
            log.info("Notification {} traitee en parallele, ignoree", notification.idEvenement());
            return;
        }

        // Verrou sur la ligne : si le job de reconciliation traite le meme
        // paiement en parallele, l'un des deux attend l'autre.
        Paiement paiement = paiementRepository
                .verrouillerParReference(notification.referenceInterne())
                .orElse(null);
        if (paiement == null) {
            // Peut arriver si le callback double la transaction d'initiation.
            // Le job de reconciliation rattrapera le paiement.
            log.warn("Notification pour une reference inconnue : {}", notification.referenceInterne());
            return;
        }

        // --- Un statut terminal ne se renie jamais ----------------------
        if (paiement.getStatutPaiement().estTerminal()) {
            log.info("Paiement {} deja {} : notification {} ignoree",
                    paiement.getReference(), paiement.getStatutPaiement(), notification.statut());
            return;
        }

        // --- Controle du montant : protection anti-falsification --------
        if (notification.montant() != null
                && notification.montant().compareTo(paiement.getMontant()) != 0) {
            log.error("Montant incoherent pour {} : attendu {}, recu {}",
                    paiement.getReference(), paiement.getMontant(), notification.montant());
            appliquer(paiement, StatutPaiement.ECHOUE, notification.referenceOperateur(),
                    "MONTANT_INCOHERENT", "Montant notifie different du montant de la commande");
            paiementRepository.save(paiement);
            return;
        }

        appliquer(paiement, notification.statut(), notification.referenceOperateur(),
                notification.codeFournisseur(), notification.message());
        paiementRepository.save(paiement);
    }

    // ==================================================================
    //  3. Consultation / polling
    // ==================================================================

    @Transactional
    public PaiementResponseDto consulter(String reference, Long idClient) {
        Paiement paiement = paiementRepository.verrouillerParReference(reference)
                .orElseThrow(() -> RessourceIntrouvableException.de("Paiement", reference));

        if (!paiement.getCommande().getClient().getId().equals(idClient)) {
            throw new AccesRefuseException("Ce paiement ne vous appartient pas");
        }

        // Tant que l'operateur ne s'est pas prononce, on va lui demander.
        if (!paiement.getStatutPaiement().estTerminal()) {
            reconcilier(paiement);
            paiementRepository.save(paiement);
        }
        return PaiementResponseDto.of(paiement);
    }

    // ==================================================================
    //  4. Jobs : expiration et reconciliation
    // ==================================================================

    /** References des paiements dont on attend encore la reponse. */
    @Transactional(readOnly = true)
    public List<String> referencesEnVol() {
        return paiementRepository.trouverReferencesEnVol(EN_VOL, Instant.now());
    }

    /** References des paiements dont le delai est depasse. */
    @Transactional(readOnly = true)
    public List<String> referencesExpirees() {
        return paiementRepository.trouverReferencesExpirees(EN_VOL, Instant.now());
    }

    /**
     * Filet de securite : si un callback s'est perdu, on va chercher le
     * statut nous-memes. Indispensable en production, aucun operateur ne
     * garantit la livraison de ses webhooks.
     *
     * <p>Une transaction par paiement : un operateur lent ne doit pas
     * maintenir un verrou sur toute la table.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcilierUn(String reference) {
        Paiement paiement = paiementRepository.verrouillerParReference(reference).orElse(null);
        if (paiement == null || paiement.getStatutPaiement().estTerminal()) {
            return;
        }
        reconcilier(paiement);
        paiementRepository.save(paiement);
    }

    /** Ferme une tentative jamais confirmee et rend le stock reserve. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expirerUn(String reference) {
        Paiement paiement = paiementRepository.verrouillerParReference(reference).orElse(null);
        if (paiement == null || paiement.getStatutPaiement().estTerminal()) {
            return;
        }
        log.info("Paiement {} expire (aucune confirmation avant {})",
                paiement.getReference(), paiement.getDateExpiration());
        appliquer(paiement, StatutPaiement.EXPIRE, paiement.getReferenceOperateur(),
                "TIMEOUT", "Aucune confirmation recue dans le delai imparti");
        paiementRepository.save(paiement);
    }

    /**
     * Commandes creees puis abandonnees, sans aucune tentative de paiement
     * active. Leur stock est rendu, sinon il reste bloque indefiniment.
     */
    @Transactional(readOnly = true)
    public List<Long> commandesOrphelines() {
        return commandeRepository.trouverOrphelines(
                Instant.now().minus(properties.getDelaiExpirationMinutes(), ChronoUnit.MINUTES));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void annulerOrpheline(Long idCommande) {
        Commande commande = commandeRepository.chargerComplete(idCommande).orElse(null);
        if (commande == null || commande.getStatut() != StatutCommande.EN_ATTENTE) {
            return;
        }
        log.info("Commande {} abandonnee sans paiement : annulation et restitution du stock",
                idCommande);
        annulerCommande(commande);
    }

    // ==================================================================
    //  Interne
    // ==================================================================

    private void reconcilier(Paiement paiement) {
        ResultatPasserelle resultat = registre.pour(paiement.getFournisseur())
                .consulter(paiement.getReference(), paiement.getReferenceOperateur());
        if (resultat.statut() != paiement.getStatutPaiement()) {
            appliquer(paiement, resultat.statut(), resultat.referenceOperateur(),
                    resultat.codeFournisseur(), resultat.message());
        }
    }

    /**
     * Unique endroit ou le statut d'un paiement change. Toute transition
     * passe par la machine a etats, et les consequences metier (statut de la
     * commande, stock) sont appliquees ici, jamais ailleurs.
     */
    private void appliquer(Paiement paiement, StatutPaiement cible, String referenceOperateur,
                           String code, String message) {

        if (referenceOperateur != null) {
            paiement.setReferenceOperateur(referenceOperateur);
        }
        paiement.setCodeErreur(code);
        paiement.setMessage(tronquer(message));

        StatutPaiement actuel = paiement.getStatutPaiement();
        if (cible == null || cible == actuel) {
            return;
        }
        if (!actuel.peutAllerVers(cible)) {
            log.warn("Transition refusee pour {} : {} -> {}", paiement.getReference(), actuel, cible);
            return;
        }

        paiement.setStatutPaiement(cible);
        paiement.setDateMiseAJour(Instant.now());
        log.info("Paiement {} : {} -> {}", paiement.getReference(), actuel, cible);

        if (cible.estSucces()) {
            paiement.setDatePaiement(Instant.now());
            marquerCommandePayee(paiement.getCommande());
        } else if (cible == StatutPaiement.ECHOUE
                || cible == StatutPaiement.EXPIRE
                || cible == StatutPaiement.ANNULE) {
            annulerCommande(paiement.getCommande());
        }
    }

    private void marquerCommandePayee(Commande commande) {
        commande.setStatut(StatutCommande.PAYEE);
        commandeRepository.save(commande);
    }

    /**
     * Echec de paiement : la commande est annulee et le stock reserve est
     * rendu. Le garde-fou sur le statut evite de recrediter deux fois si
     * deux notifications d'echec arrivent.
     */
    private void annulerCommande(Commande commande) {
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            return;
        }
        commande.setStatut(StatutCommande.ANNULEE);
        for (LigneCommande ligne : commande.getLignes()) {
            Article article = articleRepository.verrouillerPourStock(ligne.getArticle().getId())
                    .orElse(null);
            if (article != null) {
                article.setStock(article.getStock() + ligne.getQuantite());
                articleRepository.save(article);
            }
        }
        commandeRepository.save(commande);
        log.info("Commande {} annulee, stock rendu", commande.getId());
    }

    /** Le montant vient TOUJOURS des lignes de commande, jamais du client. */
    private BigDecimal montantDe(Commande commande) {
        return commande.getLignes().stream()
                .map(l -> l.getPrixUnitaire().multiply(BigDecimal.valueOf(l.getQuantite())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String nouvelleReference(Long idCommande, int tentative) {
        return "PAY-" + idCommande + "-" + tentative + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String urlCallbackPour(FournisseurPaiement fournisseur) {
        return fournisseur == FournisseurPaiement.MTN_MOMO
                ? properties.getMtn().getCallbackUrl()
                : properties.getOrange().getCallbackUrl();
    }

    private static String normaliser(String numero) {
        if (numero == null) {
            return null;
        }
        String n = numero.replaceAll("[^0-9]", "");
        return n.startsWith("237") && n.length() > 9 ? n.substring(3) : n;
    }

    private static String tronquer(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
