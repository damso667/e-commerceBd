package com.example.E_commerce.Exception;

/** Donnees d'entree invalides -> HTTP 400. */
public class RequeteInvalideException extends RuntimeException {
    public RequeteInvalideException(String message) {
        super(message);
    }
}
