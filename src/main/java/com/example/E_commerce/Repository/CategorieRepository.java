package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.Categorie;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategorieRepository extends JpaRepository<Categorie,Long> {


    Optional<Categorie> findByNomCategorie(String nomCategorie);
    
}
