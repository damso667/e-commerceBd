package com.example.E_commerce.Donnees;

import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Models.Article;
import com.example.E_commerce.Models.Categorie;
import com.example.E_commerce.Models.Personne;
import com.example.E_commerce.Repository.ArticleRepository;
import com.example.E_commerce.Repository.CategorieRepository;
import com.example.E_commerce.Repository.PersonneRepository;
import com.example.E_commerce.Services.ParametresService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Catalogue d'amorçage.
 *
 * <p>Volontairement fourni : une boutique à quatre articles ne permet ni de
 * juger une grille, ni de tester la pagination, ni de voir un filtre servir à
 * quelque chose. Les prix sont en FCFA et restent dans des ordres de grandeur
 * plausibles pour le marché camerounais.</p>
 *
 * <p><b>Aucune URL d'image.</b> Il n'existe aucune photo produit, et les URL
 * précédentes pointaient vers le cache d'images Google : temporaires, ratios
 * irréguliers. Le front affiche une pastille monogramme à la place, qui est
 * un composant conçu et non un rectangle gris de secours. Le jour où de
 * vraies photos arrivent, l'administrateur renseigne {@code imageUrl}.</p>
 *
 * <p>Idempotent : chaque article est inséré seulement si son libellé est
 * absent, donc un redémarrage ne duplique rien et un article supprimé par
 * l'administrateur ne réapparaît pas.</p>
 */
@Component
@RequiredArgsConstructor
public class Donnees implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Donnees.class);

    private final CategorieRepository categorieRepository;
    private final ArticleRepository articleRepository;
    private final PersonneRepository personneRepository;
    private final PasswordEncoder passwordEncoder;
    private final ParametresService parametresService;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {

        // Crée la ligne de paramètres si elle manque : la boutique a un nom
        // et une couleur dès le premier démarrage.
        parametresService.charger();

        Categorie vetement = categorie("Vetement", "Vetements pour homme, femme et enfant");
        Categorie chaussure = categorie("Chaussure", "Chaussures de ville, de sport et sandales");
        Categorie accessoire = categorie("Accessoire", "Sacs, montres, lunettes et bijoux");
        Categorie electronique = categorie("Electronique", "Telephonie, audio, informatique");
        Categorie mobilier = categorie("Mobilier", "Mobilier et equipement de la maison");

        int avant = (int) articleRepository.count();

        // ---------------- Vetement ----------------
        article("T-shirt vintage", vetement, 26600, 12,
                "T-shirt coupe droite en coton peigne, col rond, mixte.");
        article("T-shirt uni col V", vetement, 8500, 30,
                "Coton 180 g, col V, disponible en plusieurs coloris unis.");
        article("Chemise en lin manches longues", vetement, 18000, 14,
                "Lin lave, coupe reguliere, respirante pour la saison chaude.");
        article("Chemise pagne col mao", vetement, 22500, 9,
                "Chemise en tissu pagne, col mao, finitions cousues main.");
        article("Pantalon GUCCI", vetement, 5000, 7,
                "Pantalon toile, coupe droite, taille standard.");
        article("Pantalon chino beige", vetement, 14500, 18,
                "Chino coton stretch, cinq poches, coupe ajustee.");
        article("Jean brut coupe droite", vetement, 19000, 16,
                "Denim 12 oz non delave, coupe droite, cinq poches.");
        article("Ensemble pagne femme", vetement, 32000, 6,
                "Haut et jupe assortis en tissu pagne, doublure coton.");
        article("Robe droite en wax", vetement, 27500, 8,
                "Robe midi en wax, sans manches, fermeture dos.");
        article("Veste legere coupe-vent", vetement, 24000, 10,
                "Coupe-vent deperlant, capuche rangeable, poches zippees.");
        article("Pull molleton capuche", vetement, 21000, 11,
                "Molleton gratte 320 g, capuche doublee, poche kangourou.");
        article("Short sport respirant", vetement, 9500, 25,
                "Short de sport maille respirante, cordon de serrage.");
        article("Boubou traditionnel brode", vetement, 45000, 4,
                "Boubou long en bazin, broderies au col et aux manches.");
        article("Polo pique coton", vetement, 12500, 22,
                "Polo pique deux boutons, col cote, coton peigne.");

        // ---------------- Chaussure ----------------
        article("Air Jordan Noir", chaussure, 25000, 4,
                "Basket montante, tige cuir synthetique, semelle amortie.");
        article("Basket running legere", chaussure, 29000, 12,
                "Chaussure de course, mesh respirant, semelle EVA.");
        article("Basket toile blanche", chaussure, 15500, 20,
                "Basket basse en toile, semelle gomme, lacets plats.");
        article("Mocassin cuir marron", chaussure, 34000, 7,
                "Mocassin cuir pleine fleur, doublure cuir, semelle cousue.");
        article("Derby cuir noir", chaussure, 38500, 6,
                "Derby cuir lisse, bout droit, semelle gomme antiderapante.");
        article("Sandale cuir homme", chaussure, 16000, 15,
                "Sandale deux brides, cuir tanne, semelle souple.");
        article("Sandale tressee femme", chaussure, 13500, 18,
                "Sandale plate tressee, bride cheville reglable.");
        article("Escarpin talon 7 cm", chaussure, 24500, 9,
                "Escarpin bout pointu, talon 7 cm, semelle interieure rembourree.");
        article("Botte de pluie", chaussure, 11000, 14,
                "Botte caoutchouc mi-mollet, semelle crantee.");
        article("Claquette piscine", chaussure, 4500, 40,
                "Claquette EVA, bride large, sechage rapide.");

        // ---------------- Accessoire ----------------
        article("Montre Gucci", accessoire, 250000, 3,
                "Montre analogique, boitier acier, bracelet cuir.");
        article("Montre acier classique", accessoire, 42000, 8,
                "Montre trois aiguilles, boitier 40 mm, etanche 3 ATM.");
        article("Montre sport silicone", accessoire, 18500, 16,
                "Montre numerique, bracelet silicone, chronometre et alarme.");
        article("Sac a dos toile renforcee", accessoire, 23000, 13,
                "Sac a dos 25 L, compartiment ordinateur 15 pouces.");
        article("Sac bandouliere cuir", accessoire, 31000, 7,
                "Sac bandouliere cuir, deux compartiments, sangle reglable.");
        article("Ceinture cuir boucle acier", accessoire, 9500, 24,
                "Ceinture cuir pleine fleur 35 mm, boucle acier brosse.");
        article("Lunettes de soleil polarisees", accessoire, 14000, 19,
                "Verres polarises categorie 3, monture acetate.");
        article("Portefeuille cuir compact", accessoire, 12000, 21,
                "Portefeuille six cartes, poche billets, cuir graine.");
        article("Casquette brodee", accessoire, 7500, 28,
                "Casquette six panneaux, visiere preformee, attache reglable.");
        article("Bracelet perles bois", accessoire, 5500, 35,
                "Bracelet perles bois naturel, fil elastique.");

        // ---------------- Electronique ----------------
        article("Ecouteurs sans fil", electronique, 27000, 15,
                "Ecouteurs Bluetooth 5.3, boitier de charge, 20 h d'autonomie.");
        article("Casque audio arceau", electronique, 39000, 9,
                "Casque circum-aural, reduction de bruit passive, pliable.");
        article("Enceinte Bluetooth portable", electronique, 33000, 11,
                "Enceinte 10 W, resistante aux eclaboussures, 12 h d'autonomie.");
        article("Batterie externe 20000 mAh", electronique, 18500, 20,
                "Batterie 20000 mAh, deux sorties USB, charge rapide 18 W.");
        article("Chargeur secteur charge rapide", electronique, 8500, 32,
                "Chargeur 25 W USB-C, protection surtension.");
        article("Cable USB-C tresse 2 m", electronique, 4000, 50,
                "Cable tresse 2 m, charge rapide et transfert de donnees.");
        article("Clavier sans fil compact", electronique, 21000, 10,
                "Clavier 84 touches, Bluetooth et recepteur USB.");
        article("Souris optique sans fil", electronique, 9000, 26,
                "Souris 1600 dpi, recepteur 2,4 GHz, six boutons.");
        article("Cle USB 64 Go", electronique, 7500, 30,
                "Cle USB 3.2, 64 Go, boitier metal.");
        article("Lampe torche rechargeable", electronique, 11500, 17,
                "Lampe LED 800 lumens, trois modes, batterie rechargeable.");
        article("Multiprise parafoudre", electronique, 13000, 14,
                "Multiprise cinq prises, protection parafoudre, cable 2 m.");

        // ---------------- Mobilier ----------------
        article("Chaise de bureau reglable", mobilier, 68000, 5,
                "Chaise pivotante, hauteur reglable, soutien lombaire.");
        article("Bureau bois 120 cm", mobilier, 95000, 3,
                "Bureau plateau bois 120 x 60 cm, pietement metal.");
        article("Table basse carree", mobilier, 54000, 4,
                "Table basse 70 x 70 cm, plateau bois, tablette inferieure.");
        article("Etagere cinq niveaux", mobilier, 47000, 6,
                "Etagere bois et metal, cinq niveaux, montage simple.");
        article("Lampe de chevet", mobilier, 16500, 12,
                "Lampe de chevet abat-jour tissu, interrupteur sur cordon.");
        article("Matelas mousse 140 x 190", mobilier, 125000, 2,
                "Matelas mousse haute densite, housse amovible.");
        article("Ventilateur sur pied", mobilier, 29500, 9,
                "Ventilateur 40 cm, trois vitesses, oscillation.");
        article("Tapis salon 160 x 230", mobilier, 38000, 5,
                "Tapis tisse, poils courts, envers antiderapant.");

        int ajoutes = (int) articleRepository.count() - avant;
        log.info("Catalogue d'amorcage : {} article(s) ajoute(s), {} au total",
                ajoutes, articleRepository.count());

        if (environment.matchesProfiles("prod")) {
            log.info("Profil prod : aucun compte de demonstration cree");
            return;
        }
        compte("adrien@gmail.com", "noubam", "Doe", Role.USER);
        compte("ralph@gmail.com", "ndoumbe", "John", Role.ADMIN);
    }

    private Categorie categorie(String nom, String description) {
        return categorieRepository.findByNomCategorie(nom).orElseGet(() -> {
            Categorie categorie = new Categorie();
            categorie.setNomCategorie(nom);
            categorie.setDescription(description);
            return categorieRepository.save(categorie);
        });
    }

    private void article(String libelle, Categorie categorie, long prix, int stock,
                         String description) {
        if (articleRepository.findByLibelle(libelle).isPresent()) {
            return;
        }
        Article article = new Article();
        article.setLibelle(libelle);
        article.setCategorie(categorie);
        article.setStock(stock);
        article.setPrix(BigDecimal.valueOf(prix));
        article.setDescription(description);
        // Aucune image : le front affiche une pastille monogramme.
        article.setImageUrl(null);
        articleRepository.save(article);
    }

    private void compte(String email, String nom, String prenom, Role role) {
        if (personneRepository.findByEmail(email).isPresent()) {
            return;
        }
        Personne personne = new Personne();
        personne.setEmail(email);
        personne.setNom(nom);
        personne.setPrenom(prenom);
        personne.setAdresse("Nkolbisson");
        personne.setNumeroTelephone(role == Role.ADMIN ? "677000001" : "677123456");
        personne.setMotDePasse(passwordEncoder.encode("password"));
        personne.setRole(role);
        personne.setActif(true);
        personneRepository.save(personne);
        log.info("Compte de demonstration cree : {} ({})", email, role);
    }
}
