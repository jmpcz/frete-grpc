package br.ufg.sd.pedidos;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
@Profile("!test")
public class ServidorGrpc implements ApplicationRunner {

    private static final Logger log = Logger.getLogger(ServidorGrpc.class.getName());

    private final PedidoGrpcService servico;
    private final int porta;

    public ServidorGrpc(PedidoGrpcService servico,
                        @Value("${pedidos.grpc.porta:50052}") int porta) {
        this.servico = servico;
        this.porta = porta;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Server servidor = ServerBuilder.forPort(porta)
                .addService(servico)
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        log.info("pedidos-service ouvindo em 0.0.0.0:" + porta);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Encerrando pedidos-service...");
            servidor.shutdown();
        }));

        servidor.awaitTermination();
    }
}
