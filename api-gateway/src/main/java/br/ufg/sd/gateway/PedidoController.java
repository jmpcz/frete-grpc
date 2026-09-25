package br.ufg.sd.gateway;

import br.ufg.sd.frete.v1.CotacaoFreteGrpc;
import br.ufg.sd.frete.v1.CotacaoRequest;
import br.ufg.sd.frete.v1.CotacaoResponse;
import br.ufg.sd.frete.v1.Modalidade;
import br.ufg.sd.frete.v1.Pacote;
import br.ufg.sd.pedidos.v1.ListarPedidosRequest;
import br.ufg.sd.pedidos.v1.PedidoResponse;
import br.ufg.sd.pedidos.v1.PedidosGrpc;
import br.ufg.sd.pedidos.v1.SalvarPedidoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private static final long DEADLINE_MS = 5000;

    private final CotacaoFreteGrpc.CotacaoFreteBlockingStub freteStub;
    private final PedidosGrpc.PedidosBlockingStub pedidosStub;

    public PedidoController(CotacaoFreteGrpc.CotacaoFreteBlockingStub freteStub,
                            PedidosGrpc.PedidosBlockingStub pedidosStub) {
        this.freteStub = freteStub;
        this.pedidosStub = pedidosStub;
    }

    @PostMapping
    public ResponseEntity<PedidoCriado> criar(@Valid @RequestBody PedidoRequest req) {
        CotacaoResponse cotacao = freteStub
                .withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)
                .cotar(montarCotacao(req));

        PedidoResponse salvo = pedidosStub
                .withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)
                .salvar(SalvarPedidoRequest.newBuilder()
                        .setItem(req.item())
                        .setQuantidade(req.quantidade())
                        .setCepOrigem(req.cepOrigem())
                        .setCepDestino(req.cepDestino())
                        .setModalidade(req.modalidade())
                        .setValorFreteCentavos(cotacao.getValorTotalCentavos())
                        .build());

        return ResponseEntity.status(HttpStatus.CREATED).body(new PedidoCriado(
                salvo.getId(), salvo.getStatus(),
                cotacao.getValorTotalCentavos(), cotacao.getPrazoDiasUteis(),
                "Pedido criado com sucesso"));
    }

    @GetMapping
    public List<PedidoDto> listar() {
        return pedidosStub
                .withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)
                .listar(ListarPedidosRequest.getDefaultInstance())
                .getPedidosList().stream()
                .map(PedidoDto::de)
                .toList();
    }

    private static CotacaoRequest montarCotacao(PedidoRequest req) {
        long valorDeclarado = req.valorDeclaradoCentavos() == null ? 0 : req.valorDeclaradoCentavos();
        return CotacaoRequest.newBuilder()
                .setCotacaoId(UUID.randomUUID().toString().substring(0, 8))
                .setPacote(Pacote.newBuilder()
                        .setPesoGramas(req.pesoGramas())
                        .setComprimentoCm(req.comprimentoCm())
                        .setLarguraCm(req.larguraCm())
                        .setAlturaCm(req.alturaCm())
                        .setValorDeclaradoCentavos(valorDeclarado)
                        .build())
                .setCepOrigem(req.cepOrigem())
                .setCepDestino(req.cepDestino())
                .setModalidade(Modalidade.valueOf(req.modalidade()))
                .build();
    }
}
