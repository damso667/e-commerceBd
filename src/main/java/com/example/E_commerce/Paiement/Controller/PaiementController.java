package com.example.E_commerce.Paiement.Controller;

import com.example.E_commerce.Config.UtilisateurCourant;
import com.example.E_commerce.Dto.CommandeResponseDto;
import com.example.E_commerce.Idempotence.ServiceIdempotence;
import com.example.E_commerce.Models.Commande;
import com.example.E_commerce.Paiement.Dto.CommandePayeeDto;
import com.example.E_commerce.Paiement.Dto.InitierPaiementDto;
import com.example.E_commerce.Paiement.Dto.PaiementResponseDto;
import com.example.E_commerce.Paiement.Service.PaiementService;
import com.example.E_commerce.Services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Parcours de paiement cote client.
 *
 * <pre>
 *  1. POST /api/user/commandes            (Idempotency-Key: &lt;uuid&gt;)
 *       cree la commande, reserve le stock, lance le paiement
 *       -> 201 { commande, paiement: { reference, statut: EN_COURS } }
 *
 *  2. GET  /api/user/paiements/{reference}
 *       le front interroge toutes les 3 s jusqu'a statut terminal
 *       -> { statut: PAYE | ECHOUE | EXPIRE }
 *
 *  3. POST /api/user/commandes/{id}/paiements   (en cas d'echec, nouvelle tentative)
 * </pre>
 *
 * <p>L'en-tete {@code Idempotency-Key} est facultatif mais fortement
 * recommande : il rend le double-clic, le retry reseau et le rechargement
 * de page totalement inoffensifs. Un rejeu renvoie la reponse d'origine,
 * accompagnee de l'en-tete {@code Idempotent-Replay: true}.</p>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class PaiementController {

    private final UserService userService;
    private final PaiementService paiementService;
    private final ServiceIdempotence idempotence;
    private final UtilisateurCourant utilisateurCourant;

    /** Creation de la commande + lancement du paiement, en un appel. */
    @PostMapping({"/commandes", "/passerCommande"})
    public ResponseEntity<CommandePayeeDto> commanderEtPayer(
            @RequestHeader(value = "Idempotency-Key", required = false) String cleIdempotence,
            @Valid @RequestBody InitierPaiementDto demande) {

        Long idClient = utilisateurCourant.id();

        var resultat = idempotence.executer(
                cleIdempotence,
                "POST /api/user/commandes",
                idClient,
                demande,
                CommandePayeeDto.class,
                () -> {
                    // Operateur et numero valides AVANT de reserver du stock :
                    // sinon un refus a l'initiation laisse une commande
                    // orpheline avec du stock bloque.
                    paiementService.verifierMoyenDePaiement(
                            demande.getFournisseur(), demande.getNumeroPayeur());
                    Commande commande = userService.creerCommandeDepuisPanier(idClient);
                    PaiementResponseDto paiement = paiementService.initier(
                            commande.getId(), idClient,
                            demande.getFournisseur(), demande.getNumeroPayeur());
                    return new CommandePayeeDto(CommandeResponseDto.of(commande), paiement);
                });

        return ResponseEntity.status(resultat.rejeu() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replay", String.valueOf(resultat.rejeu()))
                .body(resultat.valeur());
    }

    /** Nouvelle tentative apres un echec, sur une commande existante. */
    @PostMapping("/commandes/{idCommande}/paiements")
    public ResponseEntity<PaiementResponseDto> reessayer(
            @PathVariable Long idCommande,
            @RequestHeader(value = "Idempotency-Key", required = false) String cleIdempotence,
            @Valid @RequestBody InitierPaiementDto demande) {

        Long idClient = utilisateurCourant.id();

        var resultat = idempotence.executer(
                cleIdempotence,
                "POST /api/user/commandes/" + idCommande + "/paiements",
                idClient,
                demande,
                PaiementResponseDto.class,
                () -> paiementService.initier(idCommande, idClient,
                        demande.getFournisseur(), demande.getNumeroPayeur()));

        return ResponseEntity.ok()
                .header("Idempotent-Replay", String.valueOf(resultat.rejeu()))
                .body(resultat.valeur());
    }

    /** Consultation du statut : a appeler en boucle cote front. */
    @GetMapping("/paiements/{reference}")
    public ResponseEntity<PaiementResponseDto> statut(@PathVariable String reference) {
        return ResponseEntity.ok(paiementService.consulter(reference, utilisateurCourant.id()));
    }
}
