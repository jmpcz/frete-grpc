package br.ufg.sd.frete.servidor;

import br.ufg.sd.frete.v1.CotacaoRequest;
import br.ufg.sd.frete.v1.CotacaoResponse;
import br.ufg.sd.frete.v1.Modalidade;
import br.ufg.sd.frete.v1.Pacote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraFreteTest {

    private final CalculadoraFrete calculadora = new CalculadoraFrete();

    private CotacaoRequest.Builder base() {
        return CotacaoRequest.newBuilder()
                .setCotacaoId("teste")
                .setPacote(Pacote.newBuilder()
                        .setPesoGramas(1000)
                        .setComprimentoCm(20)
                        .setLarguraCm(15)
                        .setAlturaCm(10))
                .setCepOrigem("74000000")
                .setCepDestino("75000000")
                .setModalidade(Modalidade.ECONOMICO);
    }

    @Test
    @DisplayName("pacote compacto: cobra pelo peso real")
    void cobraPesoReal() {
        CotacaoResponse r = calculadora.cotar(base().build());
        assertFalse(r.getPesoCubadoAplicado());
        assertEquals(1000, r.getPesoTaxadoGramas());
    }

    @Test
    @DisplayName("pacote volumoso e leve: peso cubado supera o real")
    void aplicaPesoCubado() {
        CotacaoRequest req = base()
                .setPacote(Pacote.newBuilder()
                        .setPesoGramas(900)
                        .setComprimentoCm(60)
                        .setLarguraCm(50)
                        .setAlturaCm(40))
                .build();

        CotacaoResponse r = calculadora.cotar(req);

        // 60*50*40 = 120.000 cm3 -> 120.000/6000 = 20 kg
        assertTrue(r.getPesoCubadoAplicado());
        assertEquals(20_000, r.getPesoTaxadoGramas());
    }

    @Test
    @DisplayName("rota mais longa custa mais que rota curta")
    void rotaLongaCustaMais() {
        long curta = calculadora.cotar(base().build()).getValorTotalCentavos();
        long longa = calculadora.cotar(base().setCepDestino("69000000").build()).getValorTotalCentavos();
        assertTrue(longa > curta, "esperado que rota longa custe mais");
    }

    @Test
    @DisplayName("expresso custa mais e entrega antes que economico")
    void expressoCustaMaisEChegaAntes() {
        CotacaoResponse eco = calculadora.cotar(base().build());
        CotacaoResponse exp = calculadora.cotar(base().setModalidade(Modalidade.EXPRESSO).build());

        assertTrue(exp.getValorTotalCentavos() > eco.getValorTotalCentavos());
        assertTrue(exp.getPrazoDiasUteis() < eco.getPrazoDiasUteis());
    }

    @Test
    @DisplayName("valor declarado adiciona 1% de seguro")
    void aplicaSeguro() {
        long sem = calculadora.cotar(base().build()).getValorTotalCentavos();
        long com = calculadora.cotar(base()
                .setPacote(base().getPacote().toBuilder().setValorDeclaradoCentavos(100_000L))
                .build()).getValorTotalCentavos();

        assertEquals(sem + 1000L, com);
    }

    @Test
    @DisplayName("CEP fora do formato e rejeitado")
    void rejeitaCepInvalido() {
        assertThrows(CalculadoraFrete.RequisicaoInvalidaException.class,
                () -> calculadora.cotar(base().setCepDestino("ABC").build()));
    }

    @Test
    @DisplayName("peso zero e rejeitado")
    void rejeitaPesoZero() {
        assertThrows(CalculadoraFrete.RequisicaoInvalidaException.class,
                () -> calculadora.cotar(base()
                        .setPacote(base().getPacote().toBuilder().setPesoGramas(0))
                        .build()));
    }

    @Test
    @DisplayName("prefixos de CEP numericamente proximos podem ser regioes distantes")
    void prefixoProximoNaoSignificaRotaCurta() {
        // 74 (Goiania) e 69 (Manaus) diferem em 5 no prefixo, mas estao
        // em regioes opostas. Classificar por subtracao daria "local".
        assertEquals(TabelaTarifas.Regiao.CENTRO_OESTE, TabelaTarifas.regiaoDe("74000000"));
        assertEquals(TabelaTarifas.Regiao.NORTE, TabelaTarifas.regiaoDe("69000000"));
        assertEquals(TabelaTarifas.Faixa.REMOTO,
                TabelaTarifas.faixaEntre("74000000", "69000000"));
    }

    @Test
    @DisplayName("mesmo prefixo de CEP e classificado como local")
    void mesmoPrefixoEhLocal() {
        assertEquals(TabelaTarifas.Faixa.LOCAL,
                TabelaTarifas.faixaEntre("74000000", "74800000"));
    }

    @Test
    @DisplayName("MESMO_DIA e aceito quando a rota e local")
    void mesmoDiaAceitoEmRotaLocal() {
        CotacaoResponse r = calculadora.cotar(base()
                .setCepDestino("74800000")
                .setModalidade(Modalidade.MESMO_DIA)
                .build());
        assertEquals(1, r.getPrazoDiasUteis());
    }

    @Test
    @DisplayName("MESMO_DIA so vale para rota local")
    void mesmoDiaApenasLocal() {
        assertThrows(CalculadoraFrete.RequisicaoInvalidaException.class,
                () -> calculadora.cotar(base()
                        .setCepDestino("69000000")
                        .setModalidade(Modalidade.MESMO_DIA)
                        .build()));
    }
}