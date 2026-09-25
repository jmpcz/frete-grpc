package br.ufg.sd.gateway;

public record PedidoCriado(
        String id,
        String status,
        long valorFreteCentavos,
        int prazoDiasUteis,
        String mensagem) {
}
