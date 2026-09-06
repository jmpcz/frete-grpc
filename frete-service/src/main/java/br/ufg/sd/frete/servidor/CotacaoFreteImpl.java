package br.ufg.sd.frete.servidor;

import br.ufg.sd.frete.v1.CotacaoFreteGrpc;
import br.ufg.sd.frete.v1.CotacaoRequest;
import br.ufg.sd.frete.v1.CotacaoResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.logging.Logger;

/**
 * Adaptador entre o transporte gRPC e a regra de negocio.
 *
 * Responsabilidades: logar, medir, traduzir erro de dominio em status gRPC.
 * O calculo em si fica na CalculadoraFrete.
 */
public class CotacaoFreteImpl extends CotacaoFreteGrpc.CotacaoFreteImplBase {

    private static final Logger log = Logger.getLogger(CotacaoFreteImpl.class.getName());

    private final CalculadoraFrete calculadora = new CalculadoraFrete();

    /** Atraso artificial, em ms, para demonstrar deadline na apresentacao. */
    private final long atrasoSimuladoMs;

    public CotacaoFreteImpl(long atrasoSimuladoMs) {
        this.atrasoSimuladoMs = atrasoSimuladoMs;
    }

    @Override
    public void cotar(CotacaoRequest req, StreamObserver<CotacaoResponse> observer) {
        long inicio = System.nanoTime();

        log.info(String.format(
                ">> RECEBIDO  cotacao=%s  %s -> %s  peso=%dg  dims=%dx%dx%d  modalidade=%s  [%d bytes protobuf]",
                req.getCotacaoId(),
                req.getCepOrigem(), req.getCepDestino(),
                req.getPacote().getPesoGramas(),
                req.getPacote().getComprimentoCm(),
                req.getPacote().getLarguraCm(),
                req.getPacote().getAlturaCm(),
                req.getModalidade().name(),
                req.getSerializedSize()));

        try {
            if (atrasoSimuladoMs > 0) {
                log.warning("   atraso simulado de " + atrasoSimuladoMs + "ms ativo");
                Thread.sleep(atrasoSimuladoMs);
            }

            CotacaoResponse resposta = calculadora.cotar(req);

            long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
            log.info(String.format(
                    "<< RESPONDIDO cotacao=%s  total=R$ %.2f  prazo=%dd  %dms  [%d bytes protobuf]",
                    resposta.getCotacaoId(),
                    resposta.getValorTotalCentavos() / 100.0,
                    resposta.getPrazoDiasUteis(),
                    duracaoMs,
                    resposta.getSerializedSize()));

            observer.onNext(resposta);
            observer.onCompleted();

        } catch (CalculadoraFrete.RequisicaoInvalidaException e) {
            log.warning("!! REJEITADO cotacao=" + req.getCotacaoId() + ": " + e.getMessage());
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            observer.onError(Status.CANCELLED
                    .withDescription("Processamento interrompido")
                    .asRuntimeException());

        } catch (Exception e) {
            log.severe("!! ERRO INTERNO cotacao=" + req.getCotacaoId() + ": " + e);
            observer.onError(Status.INTERNAL
                    .withDescription("Falha ao calcular a cotacao")
                    .asRuntimeException());
        }
    }
}
