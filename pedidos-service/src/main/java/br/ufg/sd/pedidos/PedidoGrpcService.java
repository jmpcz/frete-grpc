package br.ufg.sd.pedidos;

import br.ufg.sd.pedidos.v1.ListaPedidos;
import br.ufg.sd.pedidos.v1.ListarPedidosRequest;
import br.ufg.sd.pedidos.v1.PedidoResponse;
import br.ufg.sd.pedidos.v1.PedidosGrpc;
import br.ufg.sd.pedidos.v1.SalvarPedidoRequest;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
public class PedidoGrpcService extends PedidosGrpc.PedidosImplBase {

    private static final Logger log = Logger.getLogger(PedidoGrpcService.class.getName());

    private final PedidoRepository repositorio;

    public PedidoGrpcService(PedidoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void salvar(SalvarPedidoRequest req, StreamObserver<PedidoResponse> observer) {
        Pedido salvo = repositorio.save(new Pedido(
                req.getItem(),
                req.getQuantidade(),
                req.getCepOrigem(),
                req.getCepDestino(),
                req.getModalidade(),
                req.getValorFreteCentavos()));

        log.info(String.format("Pedido salvo: id=%s, item=%s, quantidade=%d",
                salvo.getId(), salvo.getItem(), salvo.getQuantidade()));

        observer.onNext(paraResposta(salvo));
        observer.onCompleted();
    }

    @Override
    public void listar(ListarPedidosRequest req, StreamObserver<ListaPedidos> observer) {
        ListaPedidos.Builder lista = ListaPedidos.newBuilder();
        repositorio.findAll().forEach(p -> lista.addPedidos(paraResposta(p)));
        observer.onNext(lista.build());
        observer.onCompleted();
    }

    private static PedidoResponse paraResposta(Pedido p) {
        return PedidoResponse.newBuilder()
                .setId(p.getId().toString())
                .setItem(p.getItem())
                .setQuantidade(p.getQuantidade())
                .setCepOrigem(p.getCepOrigem())
                .setCepDestino(p.getCepDestino())
                .setModalidade(p.getModalidade())
                .setValorFreteCentavos(p.getValorFreteCentavos())
                .setStatus(p.getStatus())
                .setCriadoEm(p.getCriadoEm().toString())
                .build();
    }
}
