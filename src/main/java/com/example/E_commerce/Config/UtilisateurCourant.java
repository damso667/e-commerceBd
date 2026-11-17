package com.example.E_commerce.Config;

import com.example.E_commerce.Exception.AccesRefuseException;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.PersonneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recupere l'utilisateur authentifie.
 *
 * <p>Ces huit lignes etaient copiees-collees dans chaque methode de chaque
 * controleur. Les centraliser evite qu'un oubli ponctuel ouvre un trou de
 * securite, et rend les controleurs lisibles.</p>
 */
@Component
@RequiredArgsConstructor
public class UtilisateurCourant {

    private final PersonneRepository personneRepository;

    @Transactional(readOnly = true)
    public Personne get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccesRefuseException("Authentification requise");
        }
        Personne personne = personneRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccesRefuseException("Compte introuvable"));
        if (!personne.isActif()) {
            throw new AccesRefuseException("Votre compte est desactive");
        }
        return personne;
    }

    public Long id() {
        return get().getId();
    }
}
