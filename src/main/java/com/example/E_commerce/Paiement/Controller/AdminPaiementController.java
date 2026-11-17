package com.example.E_commerce.Paiement.Controller;

import com.example.E_commerce.Config.UtilisateurCourant;
import com.example.E_commerce.Dto.PageReponse;
import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Enum.StatutPaiement;
import com.example.E_commerce.Idempotence.ServiceIdempotence;
import com.example.E_commerce.Paiement.Dto.PaiementAdminDto;
import com.example.E_commerce.Paiement.Dto.PaiementDetailAdminDto;
import com.example.E_commerce.Paiement.Dto.RemboursementDto;
import com.example.E_commerce.Paiement.Dto.ResultatRemboursementDto;
import com.example.E_commerce.Paiement.Service.AdminPaiementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Suivi des paiements cote back-office.
 *
 * <pre>
 *  GET  /api/admin/paiements?statut=ECHOUE&amp;fournisseur=MTN_MOMO
 *                           &amp;debut=2026-11-01&amp;fin=2026-11-17
 *                           &amp;recherche=677123456&amp;page=0&amp;size=20
 *  GET  /api/admin/paiements/{reference}
 *  GET  /api/admin/commandes/{idCommande}/paiements
 *  POST /api/admin/paiements/{reference}/remboursement
 * </pre>
 *
 * <p>Le parametre {@code recherche} couvre d'un coup la reference interne,
 * la reference operateur, le numero payeur, l'email et le nom du client :
 * au telephone avec un client, on tape ce qu'il donne.</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPaiementController {

    private final AdminPaiementService adminPaiementService;
    private final ServiceIdempotence idempotence;
    private final UtilisateurCourant utilisateurCourant;

    @GetMapping("/paiements")
    public ResponseEntity<PageReponse<PaiementAdminDto>> rechercher(
            @RequestParam(required = false) StatutPaiement statut,
            @RequestParam(required = false) FournisseurPaiement fournisseur,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) String recherche,
            @PageableDefault(size = 20, sort = "dateCreation", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(adminPaiementService.rechercher(
                statut, fournisseur, debut, fin, recherche, pageable));
    }

    @GetMapping("/paiements/{reference}")
    public ResponseEntity<PaiementDetailAdminDto> detail(@PathVariable String reference) {
        return ResponseEntity.ok(adminPaiementService.detail(reference));
    }

    @GetMapping("/commandes/{idCommande}/paiements")
    public ResponseEntity<List<PaiementAdminDto>> tentatives(@PathVariable Long idCommande) {
        return ResponseEntity.ok(adminPaiementService.tentativesDeCommande(idCommande));
    }

    /**
     * Remboursement. L'en-tete Idempotency-Key est accepte pour qu'un
     * double-clic ou un retry reseau ne declenche pas deux remboursements ;
     * l'etat terminal REMBOURSE protege de toute facon.
     */
    @PostMapping("/paiements/{reference}/remboursement")
    public ResponseEntity<ResultatRemboursementDto> rembourser(
            @PathVariable String reference,
            @RequestHeader(value = "Idempotency-Key", required = false) String cleIdempotence,
            @Valid @RequestBody RemboursementDto demande) {

        var resultat = idempotence.executer(
                cleIdempotence,
                "POST /api/admin/paiements/" + reference + "/remboursement",
                utilisateurCourant.id(),
                demande,
                ResultatRemboursementDto.class,
                () -> adminPaiementService.rembourser(reference, demande.getMotif()));

        return ResponseEntity.ok()
                .header("Idempotent-Replay", String.valueOf(resultat.rejeu()))
                .body(resultat.valeur());
    }
}
