package com.example.E_commerce.Exception;

/** La ressource demandee n'existe pas -> HTTP 404. */
public class RessourceIntrouvableException extends RuntimeException {
    public RessourceIntrouvableException(String message) {
        super(message);
    }

    public static RessourceIntrouvableException de(String quoi, Object id) {
        return new RessourceIntrouvableException(quoi + " introuvable (id = " + id + ")");
    }
}
