package com.example.E_commerce.Exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Traduction des exceptions en reponses HTTP coherentes.
 *
 * <p>Sans cela, toute exception metier remontait en 500 avec une trace Java
 * dans le corps de la reponse : illisible pour le front, et bavard sur la
 * structure interne de l'application.</p>
 *
 * <p>Format de reponse unique, pour que le front n'ait qu'un seul cas a
 * traiter :</p>
 * <pre>
 * { "horodatage": "...", "statut": 409, "erreur": "Conflit",
 *   "message": "...", "chemin": "/api/user/commandes" }
 * </pre>
 */
@RestControllerAdvice
public class GestionnaireErreurs {

    private static final Logger log = LoggerFactory.getLogger(GestionnaireErreurs.class);

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> introuvable(
            RessourceIntrouvableException e, HttpServletRequest requete) {
        return reponse(HttpStatus.NOT_FOUND, e.getMessage(), requete);
    }

    @ExceptionHandler({RequeteInvalideException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> requeteInvalide(
            RuntimeException e, HttpServletRequest requete) {
        return reponse(HttpStatus.BAD_REQUEST, e.getMessage(), requete);
    }

    /**
     * Fichier au-dela de la limite du conteneur. Sans ce traitement, Spring
     * laisse remonter une erreur serveur : l'administrateur verrait un 500
     * la ou il a simplement choisi une photo trop lourde.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> fichierTropLourd(
            MaxUploadSizeExceededException e, HttpServletRequest requete) {
        return reponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "Fichier trop lourd. Maximum 1 Mo pour une photo d'article, "
                + "512 Ko pour le logo ou l'icone.", requete);
    }

    @ExceptionHandler(RegleMetierException.class)
    public ResponseEntity<Map<String, Object>> regleMetier(
            RegleMetierException e, HttpServletRequest requete) {
        return reponse(HttpStatus.CONFLICT, e.getMessage(), requete);
    }

    @ExceptionHandler(ConflitIdempotenceException.class)
    public ResponseEntity<Map<String, Object>> conflitIdempotence(
            ConflitIdempotenceException e, HttpServletRequest requete) {
        return reponse(HttpStatus.CONFLICT, e.getMessage(), requete);
    }

    @ExceptionHandler({AccesRefuseException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, Object>> accesRefuse(
            Exception e, HttpServletRequest requete) {
        return reponse(HttpStatus.FORBIDDEN, e.getMessage(), requete);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> identifiants(
            BadCredentialsException e, HttpServletRequest requete) {
        // Message volontairement vague : ne pas reveler si l'email existe.
        return reponse(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect", requete);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> compteDesactive(
            DisabledException e, HttpServletRequest requete) {
        return reponse(HttpStatus.FORBIDDEN, "Votre compte est desactive", requete);
    }

    /** Erreurs de validation @Valid : on liste les champs fautifs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(
            MethodArgumentNotValidException e, HttpServletRequest requete) {

        Map<String, String> champs = new TreeMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> champs.put(err.getField(), err.getDefaultMessage()));

        Map<String, Object> corps = corpsDeBase(
                HttpStatus.BAD_REQUEST, "Donnees invalides", requete);
        corps.put("champs", champs);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corps);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> corpsIllisible(
            HttpMessageNotReadableException e, HttpServletRequest requete) {
        return reponse(HttpStatus.BAD_REQUEST, "Corps de requete illisible ou mal forme", requete);
    }

    /** Dernier filet : on journalise la cause, on ne la renvoie pas au client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inattendue(
            Exception e, HttpServletRequest requete) {
        log.error("Erreur inattendue sur {} {}", requete.getMethod(), requete.getRequestURI(), e);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue. Reessayez plus tard.", requete);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> reponse(
            HttpStatus statut, String message, HttpServletRequest requete) {
        return ResponseEntity.status(statut).body(corpsDeBase(statut, message, requete));
    }

    private Map<String, Object> corpsDeBase(
            HttpStatus statut, String message, HttpServletRequest requete) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("horodatage", Instant.now().toString());
        corps.put("statut", statut.value());
        corps.put("erreur", statut.getReasonPhrase());
        corps.put("message", message == null ? statut.getReasonPhrase() : message);
        corps.put("chemin", requete.getRequestURI());
        return corps;
    }
}
