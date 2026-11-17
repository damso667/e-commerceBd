package com.example.E_commerce.Models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Identité de la boutique, modifiable par l'administrateur.
 *
 * <p>Raison d'être : la plateforme est revendue à plusieurs marchands, qui
 * doivent pouvoir l'exploiter sous leur propre marque <b>sans toucher au code
 * ni redéployer</b>. Tant que le nom, le logo ou l'icône vivent dans un
 * composant du front ou dans un fichier de build, chaque revente demande un
 * fork. Ici ils vivent en base, et le front les lit au démarrage.</p>
 *
 * <p>Ligne unique : l'identifiant est figé à {@link #ID_UNIQUE}. Ce n'est pas
 * une table de configuration multi-locataire — une instance, une boutique.</p>
 *
 * <p>Les binaires (logo, icône) sont stockés <b>en base</b> et non sur le
 * disque : le système de fichiers de Render est éphémère, un logo déposé
 * disparaîtrait au redéploiement suivant.</p>
 */
@Entity
@Table(name = "parametres_boutique")
@Getter
@Setter
@NoArgsConstructor
public class ParametresBoutique {

    public static final Long ID_UNIQUE = 1L;

    /** Valeurs par défaut d'une installation neuve. */
    public static final String NOM_DEFAUT = "QALF SHOP";
    public static final String COULEUR_DEFAUT = "#0B6B5E";

    @Id
    private Long id = ID_UNIQUE;

    @Column(nullable = false, length = 60)
    private String nom = NOM_DEFAUT;

    @Column(length = 120)
    private String slogan;

    /**
     * Couleur de marque, en hexadécimal. Le front la pose comme variable CSS :
     * c'est le seul endroit d'où elle vient.
     */
    @Column(nullable = false, length = 7)
    private String couleurPrimaire = COULEUR_DEFAUT;

    // ---------- Logo affiché dans l'interface ----------

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "logo_donnees")
    private byte[] logoDonnees;

    @Column(name = "logo_type", length = 40)
    private String logoType;

    /** Change à chaque dépôt : sert d'ETag et casse le cache du navigateur. */
    @Column(name = "logo_version", length = 32)
    private String logoVersion;

    // ---------- Icône de l'application installée (PWA) ----------

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "icone_donnees")
    private byte[] iconeDonnees;

    @Column(name = "icone_type", length = 40)
    private String iconeType;

    @Column(name = "icone_version", length = 32)
    private String iconeVersion;

    // ---------- Contact, affiché en pied de page ----------

    @Column(length = 40)
    private String telephoneContact;

    @Column(length = 150)
    private String emailContact;

    @Column(length = 255)
    private String adresse;

    /** Phrase d'accueil de la page d'entrée. Vide = aucune phrase affichée. */
    @Column(length = 300)
    private String messageAccueil;

    @Column(nullable = false)
    private Instant dateMiseAJour = Instant.now();

    @PreUpdate
    void avantMiseAJour() {
        dateMiseAJour = Instant.now();
    }

    public boolean aUnLogo() {
        return logoDonnees != null && logoDonnees.length > 0;
    }

    public boolean aUneIcone() {
        return iconeDonnees != null && iconeDonnees.length > 0;
    }

    /** Monogramme de repli quand aucun logo n'a été déposé. */
    public String monogramme() {
        if (nom == null || nom.isBlank()) {
            return "?";
        }
        String[] mots = nom.trim().split("\\s+");
        if (mots.length >= 2 && !mots[0].isEmpty() && !mots[1].isEmpty()) {
            return ("" + mots[0].charAt(0) + mots[1].charAt(0)).toUpperCase();
        }
        return nom.trim().substring(0, 1).toUpperCase();
    }
}
