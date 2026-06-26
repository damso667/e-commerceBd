package com.example.E_commerce.Models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Pagnier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate dateAjout;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client", nullable = false, unique = true)
    private Personne client;

    @OneToMany(mappedBy = "pagnier", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LignePagnier> lignes = new ArrayList<>();


}
