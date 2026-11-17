package com.example.E_commerce.Repository;

import com.example.E_commerce.Enum.FournisseurPaiement;
import com.example.E_commerce.Models.EvenementPaiement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvenementPaiementRepository extends JpaRepository<EvenementPaiement, Long> {
    boolean existsByFournisseurAndIdEvenement(FournisseurPaiement fournisseur, String idEvenement);
}
