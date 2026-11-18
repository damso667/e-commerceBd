package com.example.E_commerce.Controller;

import com.example.E_commerce.Dto.ParametresPublicsDto;
import com.example.E_commerce.Models.ParametresBoutique;
import com.example.E_commerce.Services.ParametresService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identité de la boutique, en lecture publique.
 *
 * <p>Le front appelle {@code GET /api/parametres} avant tout affichage : le
 * nom, la couleur et le logo viennent de là, jamais du code. C'est ce qui
 * permet de revendre la plateforme sans la forker.</p>
 */
@RestController
@RequestMapping("/api/parametres")
@RequiredArgsConstructor
public class ParametresController {

    private final ParametresService parametresService;

    @GetMapping
    public ResponseEntity<ParametresPublicsDto> lire() {
        // Pas de cache : un changement de marque doit être visible au
        // rechargement suivant, sans attendre l'expiration d'un cache.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(parametresService.lirePublics());
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        return binaire(parametresService.charger(), true);
    }

    @GetMapping("/icone")
    public ResponseEntity<byte[]> icone() {
        return binaire(parametresService.charger(), false);
    }

    /**
     * Manifeste PWA engendré depuis la base.
     *
     * <p>C'est ce qui rend « jusqu'aux images natives de l'app » tenable : le
     * nom de l'application installée, sa couleur de thème et son icône
     * suivent les paramètres, alors qu'un manifeste statique figerait la
     * marque au moment du build.</p>
     */
    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Map<String, Object>> manifeste() {
        ParametresBoutique p = parametresService.charger();

        List<Map<String, Object>> icones;
        if (p.aUneIcone()) {
            // Une seule entree : on ne connait ni la taille reelle du fichier
            // depose ni sa marge de securite. "any maskable" laisse le
            // systeme decider plutot que de mentir sur les dimensions.
            Map<String, Object> deposee = new LinkedHashMap<>();
            deposee.put("src", "/api/parametres/icone?v=" + p.getIconeVersion());
            deposee.put("type", p.getIconeType());
            deposee.put("sizes", "512x512");
            deposee.put("purpose", "any maskable");
            icones = List.of(deposee);
        } else {
            // Icones livrees avec le front : l'application est installable
            // des le premier demarrage, sans aucun depot.
            icones = List.of(
                    Map.of("src", "/icons/icone-192.png", "type", "image/png",
                            "sizes", "192x192", "purpose", "any"),
                    Map.of("src", "/icons/icone-512.png", "type", "image/png",
                            "sizes", "512x512", "purpose", "any"),
                    // La version maskable a une marge plus large : sans elle,
                    // Android rogne le symbole en cercle et coupe la queue du Q.
                    Map.of("src", "/icons/icone-maskable-512.png", "type", "image/png",
                            "sizes", "512x512", "purpose", "maskable"));
        }

        Map<String, Object> manifeste = new LinkedHashMap<>();
        manifeste.put("name", p.getNom());
        manifeste.put("short_name", p.getNom());
        manifeste.put("description", p.getSlogan() == null ? "Boutique en ligne" : p.getSlogan());
        manifeste.put("start_url", "/");
        manifeste.put("scope", "/");
        manifeste.put("display", "standalone");
        manifeste.put("orientation", "portrait");
        manifeste.put("background_color", "#F4F2ED");
        manifeste.put("theme_color", p.getCouleurPrimaire());
        manifeste.put("lang", "fr");
        manifeste.put("icons", icones);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(manifeste);
    }

    // ==================================================================

    /**
     * Sert un binaire avec un ETag issu de sa version : le navigateur le garde
     * un an et ne le redemande que lorsque l'administrateur l'a remplacé.
     */
    private ResponseEntity<byte[]> binaire(ParametresBoutique p, boolean logo) {
        byte[] donnees = logo ? p.getLogoDonnees() : p.getIconeDonnees();
        String type = logo ? p.getLogoType() : p.getIconeType();
        String version = logo ? p.getLogoVersion() : p.getIconeVersion();

        if (donnees == null || donnees.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        type == null ? MediaType.IMAGE_PNG_VALUE : type))
                .eTag("\"" + version + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(donnees);
    }
}
