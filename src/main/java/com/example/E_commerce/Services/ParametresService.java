package com.example.E_commerce.Services;

import com.example.E_commerce.Dto.ParametresPublicsDto;
import com.example.E_commerce.Dto.ParametresRequestDto;
import com.example.E_commerce.Exception.RequeteInvalideException;
import com.example.E_commerce.Models.ParametresBoutique;
import com.example.E_commerce.Paiement.Config.PaiementProperties;
import com.example.E_commerce.Repository.ParametresBoutiqueRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Lecture et modification de l'identité de la boutique.
 *
 * <p>C'est la pièce qui rend la plateforme revendable : un marchand installe
 * sa marque depuis le back-office, sans fork ni redéploiement.</p>
 */
@Service
@RequiredArgsConstructor
public class ParametresService {

    private static final Logger log = LoggerFactory.getLogger(ParametresService.class);

    /**
     * Types acceptés. <b>SVG volontairement exclu</b> : un SVG peut contenir
     * du script, et il serait servi depuis l'origine de l'API. Dans une
     * balise {@code <img>} le script ne s'exécute pas, mais une navigation
     * directe vers l'URL, elle, l'exécuterait. Le gain esthétique ne vaut
     * pas ce trou.
     */
    private static final List<String> TYPES_ACCEPTES =
            List.of("image/png", "image/jpeg", "image/webp");

    /** 512 Ko : largement assez pour un logo, et la ligne reste légère. */
    private static final long TAILLE_MAX = 512L * 1024L;

    private final ParametresBoutiqueRepository repository;
    private final PaiementProperties paiementProperties;

    // ==================================================================
    //  Lecture
    // ==================================================================

    /**
     * Charge les paramètres, en créant la ligne par défaut au premier appel.
     * Aucune installation manuelle n'est donc nécessaire.
     */
    @Transactional
    public ParametresBoutique charger() {
        return repository.findById(ParametresBoutique.ID_UNIQUE).orElseGet(() -> {
            log.info("Aucun parametre de boutique en base : creation des valeurs par defaut");
            return repository.save(new ParametresBoutique());
        });
    }

    @Transactional
    public ParametresPublicsDto lirePublics() {
        return ParametresPublicsDto.of(charger(), paiementProperties.estEnSimulation());
    }

    // ==================================================================
    //  Modification
    // ==================================================================

    @Transactional
    public ParametresPublicsDto modifier(ParametresRequestDto dto) {
        ParametresBoutique p = charger();
        p.setNom(dto.getNom().trim());
        p.setSlogan(vide(dto.getSlogan()));
        p.setCouleurPrimaire(dto.getCouleurPrimaire().toUpperCase());
        p.setTelephoneContact(vide(dto.getTelephoneContact()));
        p.setEmailContact(vide(dto.getEmailContact()));
        p.setAdresse(vide(dto.getAdresse()));
        p.setMessageAccueil(vide(dto.getMessageAccueil()));
        return ParametresPublicsDto.of(repository.save(p), paiementProperties.estEnSimulation());
    }

    @Transactional
    public ParametresPublicsDto deposerLogo(MultipartFile fichier) {
        valider(fichier);
        ParametresBoutique p = charger();
        p.setLogoDonnees(lire(fichier));
        p.setLogoType(fichier.getContentType());
        p.setLogoVersion(nouvelleVersion());
        log.info("Logo mis a jour ({} octets, {})", fichier.getSize(), fichier.getContentType());
        return ParametresPublicsDto.of(repository.save(p), paiementProperties.estEnSimulation());
    }

    @Transactional
    public ParametresPublicsDto deposerIcone(MultipartFile fichier) {
        valider(fichier);
        ParametresBoutique p = charger();
        p.setIconeDonnees(lire(fichier));
        p.setIconeType(fichier.getContentType());
        p.setIconeVersion(nouvelleVersion());
        log.info("Icone mise a jour ({} octets, {})", fichier.getSize(), fichier.getContentType());
        return ParametresPublicsDto.of(repository.save(p), paiementProperties.estEnSimulation());
    }

    @Transactional
    public ParametresPublicsDto retirerLogo() {
        ParametresBoutique p = charger();
        p.setLogoDonnees(null);
        p.setLogoType(null);
        p.setLogoVersion(null);
        return ParametresPublicsDto.of(repository.save(p), paiementProperties.estEnSimulation());
    }

    @Transactional
    public ParametresPublicsDto retirerIcone() {
        ParametresBoutique p = charger();
        p.setIconeDonnees(null);
        p.setIconeType(null);
        p.setIconeVersion(null);
        return ParametresPublicsDto.of(repository.save(p), paiementProperties.estEnSimulation());
    }

    // ==================================================================

    private void valider(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new RequeteInvalideException("Aucun fichier recu");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new RequeteInvalideException(
                    "Fichier trop lourd : " + (fichier.getSize() / 1024) + " Ko, maximum 512 Ko");
        }
        String type = fichier.getContentType();
        if (type == null || !TYPES_ACCEPTES.contains(type.toLowerCase())) {
            throw new RequeteInvalideException(
                    "Format non accepte : " + type + ". Formats acceptes : PNG, JPEG, WebP. "
                    + "Le SVG est refuse pour des raisons de securite.");
        }
    }

    private byte[] lire(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (Exception e) {
            throw new RequeteInvalideException("Fichier illisible");
        }
    }

    private static String nouvelleVersion() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
