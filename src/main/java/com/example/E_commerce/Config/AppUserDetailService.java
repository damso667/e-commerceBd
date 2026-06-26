package com.example.E_commerce.Config;

import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.PersonneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailService implements UserDetailsService {
    private final PersonneRepository personneRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        //  On cherche l'utilisateur dans la base
        Personne personne = personneRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + email));

        //  On transforme le rôle en une autorité Spring  "ROLE_CLIENT"
        String roleAvecPrefixe = "ROLE_" + personne.getRole();

        //  On retourne l'objet User standard de Spring Security
        return org.springframework.security.core.userdetails.User.builder()
                .username(personne.getEmail())
                .password(personne.getMotDePasse())
                .authorities(roleAvecPrefixe) // Spring va créer la SimpleGrantedAuthority avec le bon nom
                .build();
    }
}
