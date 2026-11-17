package com.example.E_commerce.Paiement.Config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active la configuration typee et l'ordonnanceur.
 *
 * <p>L'ordonnanceur sert a deux choses : la notification differee de la
 * passerelle simulee, et le job de reconciliation / expiration.</p>
 */
@Configuration
@EnableConfigurationProperties(PaiementProperties.class)
@EnableScheduling
public class PaiementConfiguration {
}
