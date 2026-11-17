package com.example.E_commerce.Controller;

import com.example.E_commerce.Config.UtilisateurCourant;
import com.example.E_commerce.Dto.AvisDto;
import com.example.E_commerce.Dto.AvisResponseDto;
import com.example.E_commerce.Dto.CommandeResponseDto;
import com.example.E_commerce.Dto.PagnierDto;
import com.example.E_commerce.Dto.PanierResponseDto;
import com.example.E_commerce.Services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Espace client : panier, commandes, avis.
 *
 * <p>Le paiement est gere a part, dans {@code PaiementController}.</p>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UtilisateurCourant utilisateurCourant;

    // ---------------- Panier ----------------

    @PostMapping("/ajoutPanier")
    public ResponseEntity<PanierResponseDto> ajoutPanier(@Valid @RequestBody PagnierDto dto) {
        return ResponseEntity.ok(userService.ajoutPagnier(
                utilisateurCourant.id(), dto.getIdArticle(), dto.getQuantite()));
    }

    @GetMapping("/monPanier")
    public ResponseEntity<PanierResponseDto> voirMonPanier() {
        return ResponseEntity.ok(userService.voirMonPanier(utilisateurCourant.id()));
    }

    @DeleteMapping("/viderPanier")
    public ResponseEntity<Void> viderPanier() {
        userService.viderPanier(utilisateurCourant.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/supprimerArticlePanier/{idArticle}")
    public ResponseEntity<PanierResponseDto> supprimerArticlePanier(@PathVariable Long idArticle) {
        return ResponseEntity.ok(
                userService.supprimerArticlePanier(utilisateurCourant.id(), idArticle));
    }

    @PatchMapping("/diminuerQuantite/{idArticle}")
    public ResponseEntity<PanierResponseDto> diminuerQuantite(@PathVariable Long idArticle) {
        return ResponseEntity.ok(
                userService.diminuerQuantite(utilisateurCourant.id(), idArticle));
    }

    // ---------------- Commandes ----------------

    @GetMapping("/mesCommandes")
    public ResponseEntity<List<CommandeResponseDto>> voirMesCommandes() {
        return ResponseEntity.ok(userService.voirMesCommandes(utilisateurCourant.id()));
    }

    // ---------------- Avis ----------------

    @PostMapping("/laisserAvis")
    public ResponseEntity<AvisResponseDto> laisserAvis(@Valid @RequestBody AvisDto avisDto) {
        return ResponseEntity.ok(userService.laisserAvis(
                utilisateurCourant.id(),
                avisDto.getIdArticle(),
                avisDto.getNote(),
                avisDto.getCommentaire()));
    }
}
