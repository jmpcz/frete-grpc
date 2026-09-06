# frete-grpc

Serviço de cotação de frete sobre gRPC: recebe peso, volume, origem, destino e modalidade, e devolve o valor calculado com a composição linha a linha.

---

## Por que frete

Em um e-commerce, o fechamento do carrinho depende do valor do frete, que não é um dado armazenado, e sim um cálculo feito em tempo real a partir de peso, volume, distância e modalidade. Esse cálculo costuma ser isolado em um serviço próprio, consultado pelo checkout a cada visualização de produto.

Por ser uma operação de alta frequência e sensível à latência, o serviço precisa responder rápido e de forma previsível. Além do valor total, a resposta detalha a composição do preço linha a linha, tornando explícita a origem de cada parcela: tarifa base, custo por peso, adicional de modalidade e seguro. Essa transparência facilita a auditoria do cálculo e evita investigações por taxas abusivas de entrega.

## Como está montado

```
   vm-checkout                          vm-frete
┌──────────────────┐              ┌──────────────────┐
│ checkout-service │──── gRPC ───▶│  frete-service   │
│   (cliente)      │   :50051     │   (servidor)     │
└──────────────────┘              └──────────────────┘
         └──── VPC default, tráfego interno ────┘
              firewall: allow-grpc-interno
```

Dois serviços, duas VMs, uma porta aberta entre elas — e só entre elas. A regra de firewall libera a `50051` apenas para IPs internos da VPC e apenas em máquinas com a tag `grpc-server`. Nada disso está exposto para a internet.

| | |
|---|---|
| `frete-contract` | O `.proto` e os stubs gerados a partir dele |
| `frete-service` | O servidor. Recebe a cotação, calcula, responde |
| `checkout-service` | O cliente. Monta o pedido e espera |
| `infra/` | Scripts de VM, firewall e deploy |


## As regras do cálculo

**Peso cubado** A cobrança considera o espaço ocupado, não apenas o peso. A fórmula `(C × L × A) / 6000` fornece o peso equivalente em quilos, e o valor é taxado pelo maior entre esse peso e o peso real. Assim, em um mesmo trajeto, uma caixa compacta é cotada com valor menor que um volume grande e leve.

**Região** A classificação usa um mapa de prefixo → região e uma matriz explícita entre as cinco regiões.

**Modalidade.** `ECONOMICO` (×1,0), `EXPRESSO` (×1,8) e `MESMO_DIA` (×3,0). A modalidade `MESMO_DIA` só é ofertada quando origem e destino compartilham o prefixo de CEP;

**Seguro.** 1% do valor declarado, quando informado.

A resposta inclui a composição do preço linha a linha, além do valor total, permitindo verificar a origem de cada parcela.

## Rodando na sua máquina

JDK 17 e Maven.

```bash
mvn clean package
```

Isso compila o `.proto`, gera os stubs e roda os 11 testes. Depois, dois terminais:

```bash
# servidor
java -jar frete-service/target/frete-service.jar

# cliente — roda os 7 cenários da apresentação
java -jar checkout-service/target/checkout-service.jar --demo
```

Cotação avulsa:

```bash
java -jar checkout-service/target/checkout-service.jar \
  --peso 800 --dim 20x15x10 \
  --origem 74000000 --destino 69000000 \
  --modalidade EXPRESSO
```

## Rodando no GCP

O passo a passo comentado está em [`infra/gcp-setup.sh`](infra/gcp-setup.sh). Resumindo: duas `e2-micro` na mesma zona e VPC, tag `grpc-server` na do servidor, Docker em ambas, e o cliente apontando para o **IP interno** do servidor.

A regra que importa:

```bash
gcloud compute firewall-rules create allow-grpc-interno \
  --network=default \
  --allow=tcp:50051 \
  --source-ranges=10.128.0.0/9 \
  --target-tags=grpc-server
```

> Desligue as VMs quando terminar

## Demonstração

O modo `--demo` do cliente executa os cenários que exercitam o serviço:

1. Cotação de rota curta comparada a rota longa
2. Peso cubado predominando sobre o peso real
3. Cálculo de seguro sobre o valor declarado
4. Rejeição de CEP inválido
5. Recusa de `MESMO_DIA` em rota não local

O servidor registra em log o tamanho de cada mensagem em bytes, evidenciando a compactação do protobuf em relação a JSON. A remoção da regra de firewall interrompe a comunicação — o cliente recebe o status `UNAVAILABLE` — e recriá-la restabelece o serviço, confirmando que a troca de mensagens ocorre pela rede entre as duas máquinas.

## Autores

