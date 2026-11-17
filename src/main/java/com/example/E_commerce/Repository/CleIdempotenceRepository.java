package com.example.E_commerce.Repository;

import com.example.E_commerce.Models.CleIdempotence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface CleIdempotenceRepository extends JpaRepository<CleIdempotence, Long> {

    Optional<CleIdempotence> findByCleAndEndpoint(String cle, String endpoint);

    void deleteByDateExpirationBefore(Instant limite);
}
