package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Personne;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonneRepository extends JpaRepository<Personne,Long> {

     Optional<Personne> findByEmail(String email);

     java.util.List<Personne> findByRole(com.example.E_commerce.Enum.Role role);

     long countByRole(com.example.E_commerce.Enum.Role role);
}
