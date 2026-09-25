package br.ufg.sd.gateway;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String usuario,
        @NotBlank String senha) {
}
