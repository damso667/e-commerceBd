package com.example.E_commerce.Config;

import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.PersonneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppUserDetailService implements UserDetailsService {

    private final PersonneRepository personneRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        Personne personne = personneRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouve : " + email));

        String roleAvecPrefixe = "ROLE_" + personne.getRole();

        return org.springframework.security.core.userdetails.User.builder()
                .username(personne.getEmail())
                .password(personne.getMotDePasse())
                .authorities(roleAvecPrefixe)
                // Sans ce drapeau, un compte bloque pouvait toujours
                // s'authentifier : seul UtilisateurCourant verifiait "actif",
                // et les endpoints qui ne l'appellent pas restaient ouverts.
                // Desormais Spring Security refuse la connexion elle-meme
                // (DisabledException -> 403).
                .disabled(!personne.isActif())
                .build();
    }
}
