package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.ParametresBoutique;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identite de la boutique, telle que le front la lit au demarrage.
 *
 * <p>Aucun binaire ici : seulement des URL versionnees. Le navigateur peut
 * ainsi mettre le logo en cache longtemps, et le changement de version
 * suffit a le faire recharger.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParametresPublicsDto {

    private String nom;
    private String slogan;
    private String couleurPrimaire;
    private String monogramme;

    /** null si aucun logo n'a ete depose : le front affiche le monogramme. */
    private String logoUrl;
    private String iconeUrl;

    private String telephoneContact;
    private String emailContact;
    private String adresse;
    private String messageAccueil;

    /** Rappelle au front que les paiements ne sont pas reels. */
    private boolean paiementSimule;

    public static ParametresPublicsDto of(ParametresBoutique p, boolean paiementSimule) {
        ParametresPublicsDto dto = new ParametresPublicsDto();
        dto.setNom(p.getNom());
        dto.setSlogan(p.getSlogan());
        dto.setCouleurPrimaire(p.getCouleurPrimaire());
        dto.setMonogramme(p.monogramme());
        dto.setLogoUrl(p.aUnLogo()
                ? "/api/parametres/logo?v=" + p.getLogoVersion() : null);
        dto.setIconeUrl(p.aUneIcone()
                ? "/api/parametres/icone?v=" + p.getIconeVersion() : null);
        dto.setTelephoneContact(p.getTelephoneContact());
        dto.setEmailContact(p.getEmailContact());
        dto.setAdresse(p.getAdresse());
        dto.setMessageAccueil(p.getMessageAccueil());
        dto.setPaiementSimule(paiementSimule);
        return dto;
    }
}
