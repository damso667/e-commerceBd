package com.example.E_commerce.Paiement.Gateway.Simulation;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Paiement.Config.PaiementProperties;
import com.example.E_commerce.Paiement.Gateway.RecepteurNotification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * MTN Mobile Money, version simulee.
 *
 * <p>Active uniquement quand {@code app.paiement.mode=simulation}
 * (valeur par defaut). Le jour du passage en reel, la propriete bascule sur
 * {@code reel} et c'est {@code MtnMomoReelle} qui est injectee a la place,
 * sans aucune modification du service metier.</p>
 */
@Component
@ConditionalOnProperty(name = "app.paiement.mode", havingValue = "simulation", matchIfMissing = true)
public class MtnMomoSimulee extends PasserelleSimuleeBase {

    /** Prefixes MTN Cameroun. */
    private static final String[] PREFIXES = {
            "67", "680", "681", "682", "683", "684", "650", "651", "652", "653", "654"
    };

    public MtnMomoSimulee(PaiementProperties properties,
                          TaskScheduler ordonnanceur,
                          ObjectProvider<RecepteurNotification> recepteur) {
        super(properties, ordonnanceur, recepteur);
    }

    @Override
    public FournisseurPaiement fournisseur() {
        return FournisseurPaiement.MTN_MOMO;
    }

    @Override
    protected String[] prefixesSupportes() {
        return PREFIXES;
    }
}
