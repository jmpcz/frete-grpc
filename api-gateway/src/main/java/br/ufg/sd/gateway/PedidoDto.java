package br.ufg.sd.gateway;

import br.ufg.sd.pedidos.v1.PedidoResponse;

public record PedidoDto(
        String id,
        String item,
        int quantidade,
        String cepOrigem,
        String cepDestino,
        String modalidade,
        long valorFreteCentavos,
        String status,
        String criadoEm) {

    static PedidoDto de(PedidoResponse p) {
        return new PedidoDto(
                p.getId(), p.getItem(), p.getQuantidade(),
                p.getCepOrigem(), p.getCepDestino(), p.getModalidade(),
                p.getValorFreteCentavos(), p.getStatus(), p.getCriadoEm());
    }
}
