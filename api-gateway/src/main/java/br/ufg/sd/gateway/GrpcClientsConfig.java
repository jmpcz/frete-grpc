package br.ufg.sd.gateway;

import br.ufg.sd.frete.v1.CotacaoFreteGrpc;
import br.ufg.sd.pedidos.v1.PedidosGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientsConfig {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel freteChannel(@Value("${gateway.frete.alvo}") String alvo) {
        return ManagedChannelBuilder.forTarget(alvo).usePlaintext().build();
    }

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel pedidosChannel(@Value("${gateway.pedidos.alvo}") String alvo) {
        return ManagedChannelBuilder.forTarget(alvo).usePlaintext().build();
    }

    @Bean
    CotacaoFreteGrpc.CotacaoFreteBlockingStub freteStub(@Qualifier("freteChannel") ManagedChannel canal) {
        return CotacaoFreteGrpc.newBlockingStub(canal);
    }

    @Bean
    PedidosGrpc.PedidosBlockingStub pedidosStub(@Qualifier("pedidosChannel") ManagedChannel canal) {
        return PedidosGrpc.newBlockingStub(canal);
    }
}
