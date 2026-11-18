package com.example.E_commerce.Config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserDetailService userDetailsService;

    /** Origines autorisees, lues depuis le .env : plus rien en dur dans le code. */
    @Value("${app.cors.allowed-origins}")
    private String originesAutorisees;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.authenticationProvider(daoAuthenticationProvider());
        return builder.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF desactive : API sans formulaire, consommee par un front
                // separe. Acceptable ici parce que les origines CORS sont
                // strictement listees et que les cookies sont SameSite.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // Sonde interrogee par Render pour savoir si
                        // l'instance est vivante. Ne divulgue aucun detail
                        // (management.endpoint.health.show-details=never).
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        .requestMatchers("/api/auth/**").permitAll()

                        // Identite de la boutique : lue par le front avant
                        // tout affichage, y compris par un visiteur anonyme.
                        // En ecriture elle reste sous /api/admin.
                        .requestMatchers(HttpMethod.GET, "/api/parametres", "/api/parametres/**")
                            .permitAll()

                        // Webhooks operateurs : appeles par MTN / Orange, donc
                        // sans session. Proteges par la signature HMAC, pas par
                        // l'authentification.
                        .requestMatchers(HttpMethod.POST, "/api/paiements/webhook/**").permitAll()

                        // Catalogue public en lecture ; l'inscription reste ouverte.
                        .requestMatchers(HttpMethod.GET, "/api/personne/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/personne/register").permitAll()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Un administrateur doit pouvoir acheter comme un
                        // client : sans ROLE_ADMIN dans cette liste, son
                        // propre panier lui serait refuse.
                        .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated()
                )
                /*
                 * Sans ces deux gestionnaires, Spring Security repond 403 a
                 * TOUT refus, y compris a un visiteur simplement non
                 * authentifie. Le front ne peut alors pas distinguer « votre
                 * session a expire, reconnectez-vous » de « votre compte n'a
                 * pas les droits », et affiche un refus incomprehensible la
                 * ou il suffisait de proposer la connexion.
                 */
                .exceptionHandling(gestion -> gestion
                        .authenticationEntryPoint(SecurityConfig::nonAuthentifie)
                        .accessDeniedHandler(SecurityConfig::droitsInsuffisants))
                .build();
    }

    /** 401 : aucune session valide. Le front propose la connexion. */
    private static void nonAuthentifie(HttpServletRequest requete,
                                       HttpServletResponse reponse,
                                       AuthenticationException exception) throws IOException {
        ecrire(requete, reponse, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "Vous devez vous connecter pour continuer.");
    }

    /** 403 : session valide, mais le role ne suffit pas. */
    private static void droitsInsuffisants(HttpServletRequest requete,
                                           HttpServletResponse reponse,
                                           AccessDeniedException exception) throws IOException {
        ecrire(requete, reponse, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "Votre compte n'a pas acces a cette ressource.");
    }

    /**
     * Meme format d'erreur que {@code GestionnaireErreurs} : le front n'a
     * qu'un seul cas a traiter, quelle que soit la couche qui refuse.
     */
    private static void ecrire(HttpServletRequest requete, HttpServletResponse reponse,
                               int statut, String libelle, String message) throws IOException {
        reponse.setStatus(statut);
        reponse.setContentType("application/json;charset=UTF-8");
        reponse.getWriter().write(
                "{\"horodatage\":\"" + Instant.now() + "\","
                        + "\"statut\":" + statut + ","
                        + "\"erreur\":\"" + libelle + "\","
                        + "\"message\":\"" + message + "\","
                        + "\"chemin\":\"" + requete.getRequestURI() + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream(originesAutorisees.split(","))
                        .map(String::trim)
                        .filter(o -> !o.isEmpty())
                        .toList());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Permet au front de lire l'en-tete de rejeu idempotent.
        configuration.setExposedHeaders(List.of("Idempotent-Replay"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
