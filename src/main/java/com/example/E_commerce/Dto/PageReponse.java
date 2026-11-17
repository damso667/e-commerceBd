package com.example.E_commerce.Dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Enveloppe de pagination stable.
 *
 * <p>On ne renvoie pas directement un {@code Page<T>} de Spring Data : sa
 * sérialisation JSON n'est pas garantie entre versions (Spring Boot émet
 * d'ailleurs un avertissement à ce sujet). Ce record fixe le contrat une
 * fois pour toutes, ce qui permet au front de s'y appuyer sans risque.</p>
 *
 * @param contenu      éléments de la page courante
 * @param page         index de la page, à partir de 0
 * @param taille       taille de page demandée
 * @param totalElements nombre total d'éléments, toutes pages confondues
 * @param totalPages   nombre total de pages
 * @param premiere     true s'il n'y a rien avant
 * @param derniere     true s'il n'y a rien après
 */
public record PageReponse<T>(
        List<T> contenu,
        int page,
        int taille,
        long totalElements,
        int totalPages,
        boolean premiere,
        boolean derniere
) {

    public static <E, T> PageReponse<T> de(Page<E> page, Function<E, T> versDto) {
        return new PageReponse<>(
                page.getContent().stream().map(versDto).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    public static <T> PageReponse<T> de(Page<T> page) {
        return de(page, t -> t);
    }
}
