package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaiementRepository extends JpaRepository<Paiement,Long> {
}
