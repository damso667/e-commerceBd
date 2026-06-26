package com.example.E_commerce.Controller;

import com.example.E_commerce.Dto.*;
import com.example.E_commerce.Enum.Role;
import com.example.E_commerce.Models.*;
import com.example.E_commerce.Repository.PersonneRepository;
import com.example.E_commerce.Services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/user")
@AllArgsConstructor
public class UserController {

    private  final PersonneRepository personneRepository;
    private  final UserService userService;
    private  final PasswordEncoder passwordEncoder;


    @PostMapping("/ajoutPanier")
    public ResponseEntity<PanierResponseDto> ajoutPanier(@RequestBody PagnierDto ajoutPanierDto) {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        Personne client = personneRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));

        PanierResponseDto pagnier = userService.ajoutPagnier(
                client.getId(),
                ajoutPanierDto.getIdArticle()
        );
        return ResponseEntity.ok(pagnier);
    }

    @PostMapping("/passerCommande")
    public ResponseEntity<CommandeResponseDto> passerCommande(@RequestBody CommandeDto dto) {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        Personne client = personneRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));

        CommandeResponseDto commande = userService.passerCommande(
                client.getId(),
                dto.getModePaiement(),
                dto.getMontant()
        );
        return ResponseEntity.ok(commande);
    }

    @GetMapping("/monPanier")
    public ResponseEntity<PanierResponseDto> voirMonPanier() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        Personne client = personneRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));
        return ResponseEntity.ok(userService.voirMonPanier(client.getId()));
    }

    @GetMapping("/mesCommandes")
    public ResponseEntity<List<CommandeResponseDto>> voirMesCommandes() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        Personne client = personneRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));
        return ResponseEntity.ok(userService.voirMesCommandes(client.getId()));
    }

    @PostMapping("/laisserAvis")
    public ResponseEntity<Avis> laisserAvis(@RequestBody AvisDto avisDto) {

        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        Personne client = personneRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));

        Avis avis = userService.laisserAvis(
                client.getId(),
                avisDto.getIdArticle(),
                avisDto.getNote(),
                avisDto.getCommentaire()
        );
        return ResponseEntity.ok(avis);
    }

    @DeleteMapping("/viderPanier")
    public ResponseEntity<Void> viderPanier() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        Personne client = personneRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));
        userService.viderPanier(client.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/supprimerArticlePanier/{idArticle}")
    public ResponseEntity<PanierResponseDto> supprimerArticlePanier(@PathVariable Long idArticle) {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        Personne client = personneRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));
        return ResponseEntity.ok(userService.supprimerArticlePanier(client.getId(), idArticle));
    }

    @PatchMapping("/diminuerQuantite/{idArticle}")
    public ResponseEntity<PanierResponseDto> diminuerQuantite(@PathVariable Long idArticle) {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        Personne client = personneRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable"));
        return ResponseEntity.ok(userService.diminuerQuantite(client.getId(), idArticle));
    }


}
