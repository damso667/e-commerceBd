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
     * 512 Ko : largement assez pour un logo, et la ligne reste légère. Les
     * contrôles de format sont dans {@link ValidationImage}, partagés avec
     * les photos d'articles.
     */
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
        ValidationImage.valider(fichier, TAILLE_MAX);
    }

    private byte[] lire(MultipartFile fichier) {
        return ValidationImage.lire(fichier);
    }

    private static String nouvelleVersion() {
        return ValidationImage.nouvelleVersion();
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
