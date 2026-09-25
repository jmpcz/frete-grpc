package br.ufg.sd.pedidos;

import br.ufg.sd.pedidos.v1.ListaPedidos;
import br.ufg.sd.pedidos.v1.ListarPedidosRequest;
import br.ufg.sd.pedidos.v1.PedidoResponse;
import br.ufg.sd.pedidos.v1.SalvarPedidoRequest;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
class PedidoPersistenciaTest {

    @Autowired
    private PedidoGrpcService servico;

    @Autowired
    private PedidoRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    void salvarGravaNoBancoEDevolveIdComStatusCriado() {
        Captura<PedidoResponse> captura = new Captura<>();
        servico.salvar(pedidoValido(), captura);

        assertEquals(1, repositorio.count());
        assertFalse(captura.valor.getId().isBlank());
        assertEquals("CRIADO", captura.valor.getStatus());
        assertEquals("notebook", captura.valor.getItem());
    }

    @Test
    void listarDevolveOsPedidosGravados() {
        servico.salvar(pedidoValido(), new Captura<>());
        servico.salvar(pedidoValido(), new Captura<>());

        Captura<ListaPedidos> captura = new Captura<>();
        servico.listar(ListarPedidosRequest.getDefaultInstance(), captura);

        assertEquals(2, captura.valor.getPedidosCount());
    }

    private static SalvarPedidoRequest pedidoValido() {
        return SalvarPedidoRequest.newBuilder()
                .setItem("notebook")
                .setQuantidade(2)
                .setCepOrigem("74000000")
                .setCepDestino("01000000")
                .setModalidade("EXPRESSO")
                .setValorFreteCentavos(4599)
                .build();
    }

    private static final class Captura<T> implements StreamObserver<T> {
        private T valor;
        private final List<Throwable> erros = new ArrayList<>();

        @Override
        public void onNext(T value) {
            this.valor = value;
        }

        @Override
        public void onError(Throwable t) {
            erros.add(t);
        }

        @Override
        public void onCompleted() {
        }
    }
}
