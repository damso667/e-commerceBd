package com.example.E_commerce.Paiement.Gateway.Simulation;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Paiement.Config.PaiementProperties;
import com.example.E_commerce.Paiement.Gateway.RecepteurNotification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Orange Money, version simulee.
 * Voir {@link MtnMomoSimulee} pour le mecanisme de bascule simulation / reel.
 */
@Component
@ConditionalOnProperty(name = "app.paiement.mode", havingValue = "simulation", matchIfMissing = true)
public class OrangeMoneySimulee extends PasserelleSimuleeBase {

    /** Prefixes Orange Cameroun. */
    private static final String[] PREFIXES = {
            "69", "655", "656", "657", "658", "659", "685", "686", "687", "688", "689"
    };

    public OrangeMoneySimulee(PaiementProperties properties,
                              TaskScheduler ordonnanceur,
                              ObjectProvider<RecepteurNotification> recepteur) {
        super(properties, ordonnanceur, recepteur);
    }

    @Override
    public FournisseurPaiement fournisseur() {
        return FournisseurPaiement.ORANGE_MONEY;
    }

    @Override
    protected String[] prefixesSupportes() {
        return PREFIXES;
    }
}
