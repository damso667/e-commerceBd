package com.example.E_commerce.Dto;

import com.example.E_commerce.Enum.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Promotion ou retrogradation d'un compte. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangementRoleDto {

    @NotNull(message = "Le role est obligatoire (USER ou ADMIN)")
    private Role role;
}
