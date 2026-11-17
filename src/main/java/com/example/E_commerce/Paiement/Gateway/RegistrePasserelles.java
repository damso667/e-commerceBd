package com.example.E_commerce.Paiement.Gateway;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Exception.RegleMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Annuaire des passerelles disponibles.
 *
 * <p>Spring injecte ici toutes les implementations actives de
 * {@link PasserellePaiement}. Selon la valeur de {@code app.paiement.mode},
 * ce sont les versions simulees OU les versions reelles qui sont presentes :
 * le reste de l'application ne voit aucune difference.</p>
 */
@Component
public class RegistrePasserelles {

    private static final Logger log = LoggerFactory.getLogger(RegistrePasserelles.class);

    private final Map<FournisseurPaiement, PasserellePaiement> passerelles =
            new EnumMap<>(FournisseurPaiement.class);

    public RegistrePasserelles(List<PasserellePaiement> implementations) {
        for (PasserellePaiement p : implementations) {
            PasserellePaiement precedente = passerelles.put(p.fournisseur(), p);
            if (precedente != null) {
                throw new IllegalStateException(
                        "Deux passerelles actives pour " + p.fournisseur()
                        + " : " + precedente.getClass().getName()
                        + " et " + p.getClass().getName());
            }
        }
        log.info("Passerelles de paiement actives : {}", passerelles.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().getClass().getSimpleName())
                .toList());
    }

    public PasserellePaiement pour(FournisseurPaiement fournisseur) {
        PasserellePaiement passerelle = passerelles.get(fournisseur);
        if (passerelle == null) {
            throw new RegleMetierException(
                    "Moyen de paiement non disponible : " + fournisseur);
        }
        return passerelle;
    }

    /** Operateur capable de servir ce numero, ou null si aucun. */
    public FournisseurPaiement deduireDepuisNumero(String numero) {
        return passerelles.entrySet().stream()
                .filter(e -> e.getValue().accepteNumero(numero))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
