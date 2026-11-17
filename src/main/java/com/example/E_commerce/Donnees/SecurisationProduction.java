package com.example.E_commerce.Donnees;

import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.PersonneRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mise en securite au demarrage, uniquement avec le profil {@code prod}.
 *
 * <p>Deux problemes concrets que ce composant resout :</p>
 *
 * <ol>
 *   <li><b>Les comptes de demonstration.</b> {@code Donnees} ne les cree plus
 *       en prod, mais ceux deja presents en base y restent : la meme base
 *       Supabase a servi en developpement. Un compte ADMIN dont le mot de
 *       passe est « password » sur une URL publique est une porte ouverte.
 *       Ils sont donc <b>desactives</b> (et non supprimes, pour ne pas perdre
 *       l'historique des commandes qui les referencent).</li>
 *
 *   <li><b>L'absence d'administrateur.</b> Sans compte de demonstration, il
 *       n'y aurait plus aucun acces au back-office. Un administrateur est
 *       donc cree, ou son mot de passe remis a jour, a partir des variables
 *       d'environnement {@code ADMIN_EMAIL} et {@code ADMIN_PASSWORD}.</li>
 * </ol>
 *
 * <p>Si {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} ne sont pas fournis,
 * rien n'est cree et un avertissement est journalise : mieux vaut un
 * back-office inaccessible qu'un back-office avec un mot de passe devine.</p>
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class SecurisationProduction implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurisationProduction.class);

    /** Comptes du jeu de demonstration, a neutraliser en production. */
    private static final List<String> COMPTES_DEMONSTRATION =
            List.of("adrien@gmail.com", "ralph@gmail.com");

    private static final int LONGUEUR_MINIMALE = 12;

    private final PersonneRepository personneRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String emailAdmin;

    @Value("${app.admin.password:}")
    private String motDePasseAdmin;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        desactiverComptesDemonstration();
        amorcerAdministrateur();
    }

    private void desactiverComptesDemonstration() {
        for (String email : COMPTES_DEMONSTRATION) {
            personneRepository.findByEmail(email).ifPresent(compte -> {
                if (compte.isActif()) {
                    compte.setActif(false);
                    personneRepository.save(compte);
                    log.warn("Profil prod : compte de demonstration {} desactive", email);
                }
            });
        }
    }

    private void amorcerAdministrateur() {
        if (emailAdmin == null || emailAdmin.isBlank()
                || motDePasseAdmin == null || motDePasseAdmin.isBlank()) {
            log.warn("Profil prod : ADMIN_EMAIL / ADMIN_PASSWORD absents, "
                     + "aucun administrateur n'a ete cree. Le back-office est inaccessible.");
            return;
        }
        if (motDePasseAdmin.length() < LONGUEUR_MINIMALE) {
            log.error("Profil prod : ADMIN_PASSWORD fait moins de {} caracteres, "
                      + "administrateur NON cree. Choisissez un mot de passe plus long.",
                    LONGUEUR_MINIMALE);
            return;
        }

        String email = emailAdmin.trim().toLowerCase();
        Personne admin = personneRepository.findByEmail(email).orElseGet(Personne::new);

        boolean creation = admin.getId() == null;
        admin.setEmail(email);
        admin.setMotDePasse(passwordEncoder.encode(motDePasseAdmin));
        admin.setRole(Role.ADMIN);
        admin.setActif(true);
        if (admin.getNom() == null || admin.getNom().isBlank()) {
            admin.setNom("Administrateur");
        }
        personneRepository.save(admin);

        // Jamais le mot de passe dans les journaux.
        log.info("Profil prod : administrateur {} {}", email, creation ? "cree" : "mis a jour");
    }
}
