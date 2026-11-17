package com.example.E_commerce.Dto;

import com.example.E_commerce.Models.JournalAdmin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalAdminDto {
    private Long id;
    private String acteur;
    private String action;
    private String cibleType;
    private String cibleId;
    private String details;
    private Instant dateAction;

    public static JournalAdminDto of(JournalAdmin j) {
        return new JournalAdminDto(j.getId(), j.getActeur(), j.getAction(),
                j.getCibleType(), j.getCibleId(), j.getDetails(), j.getDateAction());
    }
}
