package br.ufg.sd.frete.cliente;

import br.ufg.sd.frete.v1.ComponenteCusto;
import br.ufg.sd.frete.v1.CotacaoFreteGrpc;
import br.ufg.sd.frete.v1.CotacaoRequest;
import br.ufg.sd.frete.v1.CotacaoResponse;
import br.ufg.sd.frete.v1.Modalidade;
import br.ufg.sd.frete.v1.Pacote;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Microsservico A - cliente gRPC.
 *
 * Uso:
 *   java -jar checkout-service.jar --demo
 *   java -jar checkout-service.jar --peso 800 --dim 20x15x10 \
 *        --origem 74000000 --destino 69000000 --modalidade EXPRESSO
 *
 * Servidor padrao: localhost:50051. Sobrescreva com --servidor IP:PORTA
 * ou com a variavel de ambiente SERVIDOR_FRETE.
 */
public class ClienteCheckout {

    private static final String AZUL     = "\u001B[36m";
    private static final String VERDE    = "\u001B[32m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String CINZA    = "\u001B[90m";
    private static final String RESET    = "\u001B[0m";

    public static void main(String[] args) throws Exception {
        String servidor = valorDe(args, "--servidor",
                System.getenv().getOrDefault("SERVIDOR_FRETE", "localhost:50051"));
        long deadlineMs = Long.parseLong(valorDe(args, "--deadline-ms", "2000"));

        ManagedChannel canal = ManagedChannelBuilder.forTarget(servidor)
                .usePlaintext()   // sem TLS: trafego interno da VPC
                .build();

        CotacaoFreteGrpc.CotacaoFreteBlockingStub stub = CotacaoFreteGrpc.newBlockingStub(canal);

        System.out.println(CINZA + "checkout-service -> " + servidor
                + "  (deadline " + deadlineMs + "ms)" + RESET);

        try {
            if (temFlag(args, "--demo")) {
                rodarDemo(stub, deadlineMs);
            } else {
                cotar(stub, montarDoArgs(args), deadlineMs);
            }
        } finally {
            canal.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** Roteiro dos cenarios da apresentacao, na ordem. */
    private static void rodarDemo(CotacaoFreteGrpc.CotacaoFreteBlockingStub stub, long deadlineMs) {
        titulo("1. Pacote pequeno, rota curta (Goiania -> Anapolis), economico");
        cotar(stub, requisicao(800, 20, 15, 10, 0, "74000000", "75000000", Modalidade.ECONOMICO), deadlineMs);

        titulo("2. Mesma cidade em MESMO_DIA -> permitido porque a faixa e Local");
        cotar(stub, requisicao(800, 20, 15, 10, 0, "74000000", "74800000", Modalidade.MESMO_DIA), deadlineMs);

        titulo("3. Mesmo pacote, rota longa (Goiania -> Manaus), expresso");
        cotar(stub, requisicao(800, 20, 15, 10, 0, "74000000", "69000000", Modalidade.EXPRESSO), deadlineMs);

        titulo("4. Travesseiro: leve mas volumoso -> o peso cubado passa a valer");
        cotar(stub, requisicao(900, 60, 50, 40, 0, "74000000", "75000000", Modalidade.ECONOMICO), deadlineMs);

        titulo("5. Notebook com valor declarado -> entra o seguro");
        cotar(stub, requisicao(2200, 40, 30, 8, 750000L, "74000000", "01000000", Modalidade.EXPRESSO), deadlineMs);

        titulo("6. CEP invalido -> erro tratado com status gRPC");
        cotar(stub, requisicao(800, 20, 15, 10, 0, "74000000", "ABC", Modalidade.ECONOMICO), deadlineMs);

        titulo("7. MESMO_DIA em rota longa -> regra de negocio recusa");
        cotar(stub, requisicao(800, 20, 15, 10, 0, "74000000", "69000000", Modalidade.MESMO_DIA), deadlineMs);
    }

    private static void cotar(CotacaoFreteGrpc.CotacaoFreteBlockingStub stub,
                              CotacaoRequest req, long deadlineMs) {
        long inicio = System.nanoTime();
        try {
            CotacaoResponse resp = stub
                    .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .cotar(req);

            long latenciaMs = (System.nanoTime() - inicio) / 1_000_000;

            System.out.printf("  %sR$ %.2f%s em %s%d dia(s) util(eis)%s%n",
                    VERDE, resp.getValorTotalCentavos() / 100.0, RESET,
                    AZUL, resp.getPrazoDiasUteis(), RESET);

            for (ComponenteCusto c : resp.getComposicaoList()) {
                System.out.printf("    %-46s R$ %8.2f%n",
                        c.getDescricao(), c.getValorCentavos() / 100.0);
            }

            System.out.printf("    %speso taxado: %d g%s | tabela %s | %d ms%s%n",
                    CINZA, resp.getPesoTaxadoGramas(),
                    resp.getPesoCubadoAplicado() ? " (cubado)" : " (real)",
                    resp.getVersaoTabela(), latenciaMs, RESET);

            imprimirComparacaoSerializacao(req, resp);

        } catch (StatusRuntimeException e) {
            System.out.printf("  %s%s%s: %s%n",
                    VERMELHO, e.getStatus().getCode(), RESET,
                    e.getStatus().getDescription());
        }
    }

    /**
     * Compara o tamanho do payload em protobuf com o equivalente em JSON.
     * E a evidencia concreta da serializacao pedida no enunciado.
     */
    private static void imprimirComparacaoSerializacao(CotacaoRequest req, CotacaoResponse resp) {
        try {
            JsonFormat.Printer printer = JsonFormat.printer().omittingInsignificantWhitespace();
            int protoBytes = req.getSerializedSize() + resp.getSerializedSize();
            int jsonBytes = printer.print(req).getBytes(StandardCharsets.UTF_8).length
                    + printer.print(resp).getBytes(StandardCharsets.UTF_8).length;

            System.out.printf("    %sserializacao: protobuf %d B  vs  JSON %d B  (%.1fx menor)%s%n",
                    CINZA, protoBytes, jsonBytes, (double) jsonBytes / protoBytes, RESET);
        } catch (Exception ignored) {
            // comparacao e informativa; nunca deve quebrar a cotacao
        }
    }

    // ---------- construcao de requisicoes ----------

    private static CotacaoRequest requisicao(int pesoG, int c, int l, int a, long valorDeclarado,
                                             String origem, String destino, Modalidade mod) {
        return CotacaoRequest.newBuilder()
                .setCotacaoId(UUID.randomUUID().toString().substring(0, 8))
                .setPacote(Pacote.newBuilder()
                        .setPesoGramas(pesoG)
                        .setComprimentoCm(c)
                        .setLarguraCm(l)
                        .setAlturaCm(a)
                        .setValorDeclaradoCentavos(valorDeclarado)
                        .build())
                .setCepOrigem(origem)
                .setCepDestino(destino)
                .setModalidade(mod)
                .build();
    }

    private static CotacaoRequest montarDoArgs(String[] args) {
        String[] dim = valorDe(args, "--dim", "20x15x10").split("x");
        return requisicao(
                Integer.parseInt(valorDe(args, "--peso", "1000")),
                Integer.parseInt(dim[0]), Integer.parseInt(dim[1]), Integer.parseInt(dim[2]),
                Long.parseLong(valorDe(args, "--valor-declarado", "0")),
                valorDe(args, "--origem", "74000000"),
                valorDe(args, "--destino", "01000000"),
                Modalidade.valueOf(valorDe(args, "--modalidade", "ECONOMICO")));
    }

    // ---------- utilitarios ----------

    private static void titulo(String texto) {
        System.out.println("\n" + AZUL + "== " + texto + RESET);
    }

    private static boolean temFlag(String[] args, String flag) {
        for (String a : args) {
            if (a.equals(flag)) return true;
        }
        return false;
    }

    private static String valorDe(String[] args, String chave, String padrao) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(chave)) return args[i + 1];
        }
        return padrao;
    }
}
