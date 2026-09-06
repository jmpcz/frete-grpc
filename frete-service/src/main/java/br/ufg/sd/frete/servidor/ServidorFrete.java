package br.ufg.sd.frete.servidor;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;

import java.util.logging.Logger;

/**
 * Microsservico B - servidor gRPC.
 *
 * Variaveis de ambiente:
 *   PORTA          porta de escuta (padrao 50051)
 *   ATRASO_MS      atraso artificial por requisicao, para demonstrar deadline
 */
public class ServidorFrete {

    private static final Logger log = Logger.getLogger(ServidorFrete.class.getName());

    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(System.getenv().getOrDefault("PORTA", "50051"));
        long atrasoMs = Long.parseLong(System.getenv().getOrDefault("ATRASO_MS", "0"));

        Server servidor = ServerBuilder.forPort(porta)
                .addService(new CotacaoFreteImpl(atrasoMs))
                // Reflection permite testar o servico com grpcurl sem ter o .proto em maos.
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        log.info("frete-service ouvindo em 0.0.0.0:" + porta
                + (atrasoMs > 0 ? " (atraso simulado: " + atrasoMs + "ms)" : ""));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Encerrando frete-service...");
            servidor.shutdown();
        }));

        servidor.awaitTermination();
    }
}
