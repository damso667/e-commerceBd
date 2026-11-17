package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.JournalAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Les filtres du journal sont construits par {@link Recherches#journal},
 * avec l'API Criteria : voir l'explication dans cette classe.
 */
public interface JournalAdminRepository
        extends JpaRepository<JournalAdmin, Long>, JpaSpecificationExecutor<JournalAdmin> {
}
