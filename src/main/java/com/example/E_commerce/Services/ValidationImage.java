package com.example.E_commerce.Services;

import com.example.E_commerce.Exception.RequeteInvalideException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Controles communs a tout depot d'image : logo, icone, photo d'article.
 *
 * <p>La regle la plus importante est le refus du SVG, et elle est ici pour
 * n'exister qu'a un seul endroit. Un SVG peut contenir du script ; servi
 * depuis l'origine de l'API, il ne s'executerait pas dans une balise
 * {@code <img>}, mais une navigation directe vers son URL, si. Le gain
 * esthetique ne vaut pas ce trou, et le faire oublier dans un seul des
 * trois points d'entree suffirait a le rouvrir.</p>
 */
public final class ValidationImage {

    private static final List<String> TYPES_ACCEPTES =
            List.of("image/png", "image/jpeg", "image/webp");

    private ValidationImage() {
    }

    /**
     * @param tailleMax plafond en octets, propre a chaque usage : un logo
     *                  n'a pas besoin du poids d'une photo de produit.
     */
    public static void valider(MultipartFile fichier, long tailleMax) {
        if (fichier == null || fichier.isEmpty()) {
            throw new RequeteInvalideException("Aucun fichier recu");
        }
        if (fichier.getSize() > tailleMax) {
            throw new RequeteInvalideException(
                    "Fichier trop lourd : " + (fichier.getSize() / 1024) + " Ko, maximum "
                    + (tailleMax / 1024) + " Ko");
        }
        String type = fichier.getContentType();
        if (type == null || !TYPES_ACCEPTES.contains(type.toLowerCase())) {
            throw new RequeteInvalideException(
                    "Format non accepte : " + type + ". Formats acceptes : PNG, JPEG, WebP. "
                    + "Le SVG est refuse pour des raisons de securite.");
        }
    }

    public static byte[] lire(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (Exception e) {
            throw new RequeteInvalideException("Fichier illisible");
        }
    }

    /** Jeton court qui change a chaque depot : ETag et cassage de cache. */
    public static String nouvelleVersion() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
