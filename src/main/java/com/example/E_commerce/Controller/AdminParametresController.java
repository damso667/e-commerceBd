package com.example.E_commerce.Controller;

import com.example.E_commerce.Dto.ParametresPublicsDto;
import com.example.E_commerce.Dto.ParametresRequestDto;
import com.example.E_commerce.Services.JournalAdminService;
import com.example.E_commerce.Services.ParametresService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Identité de la boutique, en écriture : l'écran qui rend la plateforme
 * revendable.
 *
 * <p>Un marchand installe son nom, sa couleur, son logo et l'icône de
 * l'application installée depuis ici. Rien de tout cela n'est codé en dur
 * côté front, donc aucune revente ne demande un fork ni un redéploiement.</p>
 */
@RestController
@RequestMapping("/api/admin/parametres")
@RequiredArgsConstructor
public class AdminParametresController {

    private final ParametresService parametresService;
    private final JournalAdminService journal;

    @GetMapping
    public ResponseEntity<ParametresPublicsDto> lire() {
        return ResponseEntity.ok(parametresService.lirePublics());
    }

    @PutMapping
    public ResponseEntity<ParametresPublicsDto> modifier(
            @Valid @RequestBody ParametresRequestDto dto) {
        ParametresPublicsDto resultat = parametresService.modifier(dto);
        journal.tracer("PARAMETRES_MODIFIES", "BOUTIQUE", 1,
                "nom : " + dto.getNom() + " | couleur : " + dto.getCouleurPrimaire());
        return ResponseEntity.ok(resultat);
    }

    @PostMapping("/logo")
    public ResponseEntity<ParametresPublicsDto> deposerLogo(
            @RequestParam("fichier") MultipartFile fichier) {
        ParametresPublicsDto resultat = parametresService.deposerLogo(fichier);
        journal.tracer("LOGO_MODIFIE", "BOUTIQUE", 1,
                fichier.getOriginalFilename() + " (" + fichier.getSize() / 1024 + " Ko)");
        return ResponseEntity.ok(resultat);
    }

    @PostMapping("/icone")
    public ResponseEntity<ParametresPublicsDto> deposerIcone(
            @RequestParam("fichier") MultipartFile fichier) {
        ParametresPublicsDto resultat = parametresService.deposerIcone(fichier);
        journal.tracer("ICONE_MODIFIEE", "BOUTIQUE", 1,
                fichier.getOriginalFilename() + " (" + fichier.getSize() / 1024 + " Ko)");
        return ResponseEntity.ok(resultat);
    }

    @DeleteMapping("/logo")
    public ResponseEntity<ParametresPublicsDto> retirerLogo() {
        ParametresPublicsDto resultat = parametresService.retirerLogo();
        journal.tracer("LOGO_MODIFIE", "BOUTIQUE", 1, "logo retire");
        return ResponseEntity.ok(resultat);
    }

    @DeleteMapping("/icone")
    public ResponseEntity<ParametresPublicsDto> retirerIcone() {
        ParametresPublicsDto resultat = parametresService.retirerIcone();
        journal.tracer("ICONE_MODIFIEE", "BOUTIQUE", 1, "icone retiree");
        return ResponseEntity.ok(resultat);
    }
}
