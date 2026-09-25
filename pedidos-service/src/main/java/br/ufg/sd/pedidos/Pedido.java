package br.ufg.sd.pedidos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String item;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "cep_origem", nullable = false)
    private String cepOrigem;

    @Column(name = "cep_destino", nullable = false)
    private String cepDestino;

    @Column(nullable = false)
    private String modalidade;

    @Column(name = "valor_frete_centavos", nullable = false)
    private long valorFreteCentavos;

    @Column(nullable = false)
    private String status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Pedido() {
    }

    public Pedido(String item, int quantidade, String cepOrigem, String cepDestino,
                  String modalidade, long valorFreteCentavos) {
        this.item = item;
        this.quantidade = quantidade;
        this.cepOrigem = cepOrigem;
        this.cepDestino = cepDestino;
        this.modalidade = modalidade;
        this.valorFreteCentavos = valorFreteCentavos;
        this.status = "CRIADO";
        this.criadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getItem() {
        return item;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public String getCepOrigem() {
        return cepOrigem;
    }

    public String getCepDestino() {
        return cepDestino;
    }

    public String getModalidade() {
        return modalidade;
    }

    public long getValorFreteCentavos() {
        return valorFreteCentavos;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
