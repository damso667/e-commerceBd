package com.example.E_commerce.Exception;

/** Requete comprehensible mais refusee par une regle metier -> HTTP 409. */
public class RegleMetierException extends RuntimeException {
    public RegleMetierException(String message) {
        super(message);
    }
}
