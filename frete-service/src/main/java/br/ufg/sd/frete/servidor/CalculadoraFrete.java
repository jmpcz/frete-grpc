package br.ufg.sd.frete.servidor;

import br.ufg.sd.frete.v1.ComponenteCusto;
import br.ufg.sd.frete.v1.CotacaoRequest;
import br.ufg.sd.frete.v1.CotacaoResponse;
import br.ufg.sd.frete.v1.Modalidade;
import br.ufg.sd.frete.v1.Pacote;

import java.util.regex.Pattern;

/**
 * Regra de negocio do frete. Nao conhece gRPC nem rede: recebe uma requisicao,
 * devolve uma resposta. Isso mantem a logica testavel isoladamente.
 */
public class CalculadoraFrete {

    private static final Pattern CEP_VALIDO = Pattern.compile("\\d{8}");
    private static final int PESO_MAXIMO_GRAMAS = 30_000;

    /** Erro de negocio; o adaptador gRPC traduz isso em Status.INVALID_ARGUMENT. */
    public static class RequisicaoInvalidaException extends RuntimeException {
        public RequisicaoInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    public CotacaoResponse cotar(CotacaoRequest req) {
        validar(req);

        Pacote pacote = req.getPacote();
        TabelaTarifas.Faixa faixa = TabelaTarifas.faixaEntre(req.getCepOrigem(), req.getCepDestino());

        if (req.getModalidade() == Modalidade.MESMO_DIA && faixa != TabelaTarifas.Faixa.LOCAL) {
            throw new RequisicaoInvalidaException(
                    "Modalidade MESMO_DIA disponivel apenas para entregas locais; "
                            + "a rota informada foi classificada como " + faixa.rotulo);
        }

        int pesoCubadoGramas = pesoCubadoGramas(pacote);
        int pesoTaxadoGramas = Math.max(pacote.getPesoGramas(), pesoCubadoGramas);
        boolean cubadoAplicado = pesoCubadoGramas > pacote.getPesoGramas();

        // Peso taxado arredondado para cima ate o quilo seguinte, como no mercado.
        long quilosTaxados = (long) Math.ceil(pesoTaxadoGramas / 1000.0);

        long custoPeso = quilosTaxados * faixa.porKgCentavos;
        long subtotal = faixa.tarifaBaseCentavos + custoPeso;

        double multiplicador = multiplicadorDe(req.getModalidade());
        long adicionalModalidade = Math.round(subtotal * (multiplicador - 1.0));

        long seguro = Math.round(pacote.getValorDeclaradoCentavos() * TabelaTarifas.PERCENTUAL_SEGURO);

        long total = subtotal + adicionalModalidade + seguro;

        CotacaoResponse.Builder resposta = CotacaoResponse.newBuilder()
                .setCotacaoId(req.getCotacaoId())
                .setValorTotalCentavos(total)
                .setPrazoDiasUteis(prazoDe(faixa, req.getModalidade()))
                .setPesoTaxadoGramas(pesoTaxadoGramas)
                .setPesoCubadoAplicado(cubadoAplicado)
                .setVersaoTabela(TabelaTarifas.VERSAO);

        resposta.addComposicao(componente(
                String.format("Tarifa base (faixa %s)", faixa.rotulo),
                faixa.tarifaBaseCentavos));

        resposta.addComposicao(componente(
                String.format("Peso taxado %d kg x R$ %.2f/kg%s",
                        quilosTaxados,
                        faixa.porKgCentavos / 100.0,
                        cubadoAplicado ? " (peso cubado)" : ""),
                custoPeso));

        if (adicionalModalidade != 0) {
            resposta.addComposicao(componente(
                    String.format("Adicional %s (x%.1f)", req.getModalidade().name(), multiplicador),
                    adicionalModalidade));
        }

        if (seguro > 0) {
            resposta.addComposicao(componente(
                    String.format("Seguro 1%% sobre R$ %.2f",
                            pacote.getValorDeclaradoCentavos() / 100.0),
                    seguro));
        }

        return resposta.build();
    }

    /** Peso cubado: volume em cm3 dividido pelo fator 6000, convertido para gramas. */
    static int pesoCubadoGramas(Pacote p) {
        long volumeCm3 = (long) p.getComprimentoCm() * p.getLarguraCm() * p.getAlturaCm();
        return (int) (volumeCm3 * 1000 / TabelaTarifas.FATOR_CUBAGEM);
    }

    private static double multiplicadorDe(Modalidade modalidade) {
        return switch (modalidade) {
            case ECONOMICO -> 1.0;
            case EXPRESSO  -> 1.8;
            case MESMO_DIA -> 3.0;
            default -> throw new RequisicaoInvalidaException("Modalidade nao especificada");
        };
    }

    private static int prazoDe(TabelaTarifas.Faixa faixa, Modalidade modalidade) {
        return switch (modalidade) {
            case ECONOMICO -> faixa.prazoBaseDias;
            case EXPRESSO  -> Math.max(1, (int) Math.ceil(faixa.prazoBaseDias / 2.0));
            case MESMO_DIA -> 1;
            default -> throw new RequisicaoInvalidaException("Modalidade nao especificada");
        };
    }

    private static ComponenteCusto componente(String descricao, long centavos) {
        return ComponenteCusto.newBuilder()
                .setDescricao(descricao)
                .setValorCentavos(centavos)
                .build();
    }

    private void validar(CotacaoRequest req) {
        if (!req.hasPacote()) {
            throw new RequisicaoInvalidaException("Pacote nao informado");
        }
        Pacote p = req.getPacote();

        if (p.getPesoGramas() <= 0) {
            throw new RequisicaoInvalidaException("Peso deve ser maior que zero");
        }
        if (p.getPesoGramas() > PESO_MAXIMO_GRAMAS) {
            throw new RequisicaoInvalidaException(
                    "Peso acima do limite de " + (PESO_MAXIMO_GRAMAS / 1000) + " kg");
        }
        if (p.getComprimentoCm() <= 0 || p.getLarguraCm() <= 0 || p.getAlturaCm() <= 0) {
            throw new RequisicaoInvalidaException("Dimensoes devem ser maiores que zero");
        }
        if (p.getValorDeclaradoCentavos() < 0) {
            throw new RequisicaoInvalidaException("Valor declarado nao pode ser negativo");
        }
        if (!CEP_VALIDO.matcher(req.getCepOrigem()).matches()) {
            throw new RequisicaoInvalidaException(
                    "CEP de origem invalido: esperado 8 digitos, recebido '" + req.getCepOrigem() + "'");
        }
        if (!CEP_VALIDO.matcher(req.getCepDestino()).matches()) {
            throw new RequisicaoInvalidaException(
                    "CEP de destino invalido: esperado 8 digitos, recebido '" + req.getCepDestino() + "'");
        }
        if (req.getModalidade() == Modalidade.MODALIDADE_NAO_ESPECIFICADA
                || req.getModalidade() == Modalidade.UNRECOGNIZED) {
            throw new RequisicaoInvalidaException("Modalidade nao especificada");
        }
    }
}
