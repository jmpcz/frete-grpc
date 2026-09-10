package br.ufg.sd.frete.servidor;

import java.util.Map;

/** Tarifas do transportador, separadas da CalculadoraFrete porque a politica comercial muda com frequencia. */
public final class TabelaTarifas {

    public static final String VERSAO = "tabela-2026.08";

    /** Divisor da formula de peso cubado adotado pelo mercado logistico. */
    public static final int FATOR_CUBAGEM = 6000;

    /** Percentual do valor declarado cobrado como seguro (ad valorem). */
    public static final double PERCENTUAL_SEGURO = 0.01;

    /** Faixas de distancia, derivadas do par de regioes de origem e destino. */
    public enum Faixa {
        LOCAL    ("Local",     1200L,  450L, 2),
        REGIONAL ("Regional",  1800L,  750L, 4),
        NACIONAL ("Nacional",  2500L, 1100L, 7),
        REMOTO   ("Remoto",    3900L, 1900L, 12);

        public final String rotulo;
        public final long tarifaBaseCentavos;
        public final long porKgCentavos;
        /** Prazo de referencia em dias uteis (modalidade economica). */
        public final int prazoBaseDias;

        Faixa(String rotulo, long tarifaBaseCentavos, long porKgCentavos, int prazoBaseDias) {
            this.rotulo = rotulo;
            this.tarifaBaseCentavos = tarifaBaseCentavos;
            this.porKgCentavos = porKgCentavos;
            this.prazoBaseDias = prazoBaseDias;
        }
    }

    /**
     * Regioes logisticas, derivadas das faixas de CEP definidas pelos Correios.
     *
     * ATENCAO: prefixos de CEP NAO sao numerados por proximidade geografica.
     * Goiania e 74 e Manaus e 69 - numericamente vizinhos, a milhares de km
     * de distancia. Por isso a classificacao usa regiao, nunca subtracao.
     */
    public enum Regiao {
        SUDESTE     ("Sudeste"),      // 01-39  SP, RJ, ES, MG
        NORDESTE    ("Nordeste"),     // 40-65  BA, SE, PE, AL, PB, RN, CE, PI, MA
        NORTE       ("Norte"),        // 66-69  PA, AP, AM, AC, RR
        CENTRO_OESTE("Centro-Oeste"), // 70-79  DF, GO, TO, MT, MS, RO
        SUL         ("Sul");          // 80-99  PR, SC, RS

        public final String rotulo;

        Regiao(String rotulo) {
            this.rotulo = rotulo;
        }
    }

    /** Faixa entre cada par de regioes de origem e destino. */
    private static final Map<Regiao, Map<Regiao, Faixa>> MATRIZ = Map.of(
            Regiao.SUDESTE, Map.of(
                    Regiao.SUDESTE,      Faixa.REGIONAL,
                    Regiao.SUL,          Faixa.NACIONAL,
                    Regiao.CENTRO_OESTE, Faixa.NACIONAL,
                    Regiao.NORDESTE,     Faixa.NACIONAL,
                    Regiao.NORTE,        Faixa.REMOTO),
            Regiao.SUL, Map.of(
                    Regiao.SUDESTE,      Faixa.NACIONAL,
                    Regiao.SUL,          Faixa.REGIONAL,
                    Regiao.CENTRO_OESTE, Faixa.NACIONAL,
                    Regiao.NORDESTE,     Faixa.REMOTO,
                    Regiao.NORTE,        Faixa.REMOTO),
            Regiao.CENTRO_OESTE, Map.of(
                    Regiao.SUDESTE,      Faixa.NACIONAL,
                    Regiao.SUL,          Faixa.NACIONAL,
                    Regiao.CENTRO_OESTE, Faixa.REGIONAL,
                    Regiao.NORDESTE,     Faixa.NACIONAL,
                    Regiao.NORTE,        Faixa.REMOTO),
            Regiao.NORDESTE, Map.of(
                    Regiao.SUDESTE,      Faixa.NACIONAL,
                    Regiao.SUL,          Faixa.REMOTO,
                    Regiao.CENTRO_OESTE, Faixa.NACIONAL,
                    Regiao.NORDESTE,     Faixa.REGIONAL,
                    Regiao.NORTE,        Faixa.REMOTO),
            Regiao.NORTE, Map.of(
                    Regiao.SUDESTE,      Faixa.REMOTO,
                    Regiao.SUL,          Faixa.REMOTO,
                    Regiao.CENTRO_OESTE, Faixa.REMOTO,
                    Regiao.NORDESTE,     Faixa.REMOTO,
                    Regiao.NORTE,        Faixa.REGIONAL)
    );

    public static Regiao regiaoDe(String cep) {
        int prefixo = Integer.parseInt(cep.substring(0, 2));
        if (prefixo <= 39) return Regiao.SUDESTE;
        if (prefixo <= 65) return Regiao.NORDESTE;
        if (prefixo <= 69) return Regiao.NORTE;
        if (prefixo <= 79) return Regiao.CENTRO_OESTE;
        return Regiao.SUL;
    }

    /** Mesmo prefixo de 2 digitos = LOCAL (unica faixa que oferta MESMO_DIA); senao, consulta a matriz. */
    public static Faixa faixaEntre(String cepOrigem, String cepDestino) {
        if (cepOrigem.substring(0, 2).equals(cepDestino.substring(0, 2))) {
            return Faixa.LOCAL;
        }
        return MATRIZ.get(regiaoDe(cepOrigem)).get(regiaoDe(cepDestino));
    }

    private TabelaTarifas() {
    }
}
