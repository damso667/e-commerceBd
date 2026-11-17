package com.example.E_commerce.Exception;

/**
 * Meme cle d'idempotence reutilisee avec un corps de requete different.
 * -> HTTP 409. Le client doit changer de cle.
 */
public class ConflitIdempotenceException extends RuntimeException {
    public ConflitIdempotenceException(String message) {
        super(message);
    }
}
