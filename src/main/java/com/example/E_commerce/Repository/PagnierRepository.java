package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Pagnier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagnierRepository extends JpaRepository<Pagnier,Long> {
  Optional<Pagnier> findByClientId(Long id);
}
