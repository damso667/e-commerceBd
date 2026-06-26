package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Commande;
import com.example.E_commerce.Models.Personne;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommandeRepository extends JpaRepository<Commande, Long> {
    List<Commande> findByClient(Personne client);
}
