package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.JournalAdminDto;
import com.example.E_commerce.Dto.PageReponse;
import com.example.E_commerce.Models.JournalAdmin;
import com.example.E_commerce.Repository.JournalAdminRepository;
import com.example.E_commerce.Repository.Recherches;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Écriture et lecture du journal d'administration. */
@Service
@RequiredArgsConstructor
public class JournalAdminService {

    /** Types de cible, pour éviter les chaînes libres dispersées. */
    public static final String ARTICLE = "ARTICLE";
    public static final String CATEGORIE = "CATEGORIE";
    public static final String COMMANDE = "COMMANDE";
    public static final String CLIENT = "CLIENT";
    public static final String AVIS = "AVIS";
    public static final String PAIEMENT = "PAIEMENT";

    private final JournalAdminRepository repository;

    /**
     * Trace une action. Appelé depuis la transaction de l'action elle-même :
     * si celle-ci est annulée, la trace disparaît avec elle.
     *
     * <p>L'acteur est lu directement dans le contexte de sécurité, sans
     * requête en base : tracer ne doit rien coûter.</p>
     */
    public void tracer(String action, String cibleType, Object cibleId, String details) {
        JournalAdmin entree = new JournalAdmin();
        entree.setActeur(acteurCourant());
        entree.setAction(action);
        entree.setCibleType(cibleType);
        entree.setCibleId(cibleId == null ? null : String.valueOf(cibleId));
        entree.setDetails(tronquer(details));
        entree.setDateAction(Instant.now());
        repository.save(entree);
    }

    @Transactional(readOnly = true)
    public PageReponse<JournalAdminDto> rechercher(String acteur, String action, String cibleType,
                                                   String cibleId, LocalDate debut, LocalDate fin,
                                                   Pageable pageable) {
        ZoneId zone = ZoneId.systemDefault();
        Instant debutInstant = debut == null ? null : debut.atStartOfDay(zone).toInstant();
        // Borne haute exclusive : "fin = aujourd'hui" doit inclure toute la journée.
        Instant finInstant = fin == null ? null : fin.plusDays(1).atStartOfDay(zone).toInstant();

        return PageReponse.de(
                repository.findAll(
                        Recherches.journal(acteur, action, cibleType, cibleId,
                                debutInstant, finInstant),
                        pageable),
                JournalAdminDto::of);
    }

    private String acteurCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "systeme" : auth.getName();
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static String tronquer(String details) {
        if (details == null) {
            return null;
        }
        return details.length() <= 1000 ? details : details.substring(0, 1000);
    }
}
