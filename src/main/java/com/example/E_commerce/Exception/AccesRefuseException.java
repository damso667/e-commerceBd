package com.example.E_commerce.Exception;

/** La ressource existe mais n'appartient pas a l'appelant -> HTTP 403. */
public class AccesRefuseException extends RuntimeException {
    public AccesRefuseException(String message) {
        super(message);
    }
}
