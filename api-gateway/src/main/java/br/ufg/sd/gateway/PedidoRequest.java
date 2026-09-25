package br.ufg.sd.gateway;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PedidoRequest(
        @NotBlank String item,
        @NotNull @Positive Integer quantidade,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "deve ter 8 digitos") String cepOrigem,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "deve ter 8 digitos") String cepDestino,
        @NotBlank @Pattern(regexp = "ECONOMICO|EXPRESSO|MESMO_DIA",
                message = "deve ser ECONOMICO, EXPRESSO ou MESMO_DIA") String modalidade,
        @NotNull @Positive Integer pesoGramas,
        @NotNull @Positive Integer comprimentoCm,
        @NotNull @Positive Integer larguraCm,
        @NotNull @Positive Integer alturaCm,
        @PositiveOrZero Long valorDeclaradoCentavos) {
}
