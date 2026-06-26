package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Personne;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonneRepository extends JpaRepository<Personne,Long> {

     Optional<Personne> findByEmail(String email);
}
