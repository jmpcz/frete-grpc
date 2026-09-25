#!/usr/bin/env bash
# Deploy do Trabalho 2 no GCP: 1 VM (Compute Engine) rodando 3 containers
# (frete + pedidos + gateway) e 1 instancia Cloud SQL PostgreSQL.
# Rode os blocos um a um, nao o arquivo inteiro de uma vez.
set -euo pipefail

PROJETO="sd-2026-jm"           # ajuste para o seu projeto
ZONA="us-central1-a"
REGIAO="us-central1"
VM="vm-app"
SQL="pedidos-db"               # nome da instancia Cloud SQL
DB_SENHA="TROQUE_ESTA_SENHA"   # senha do usuario postgres

# ---------------------------------------------------------------------------
# 1. Cloud SQL PostgreSQL (IP publico). A criacao leva ~5-10 min.
#    Alternativa: criar pelo Console (Cloud SQL > Criar > PostgreSQL), como na Aula 6.
# ---------------------------------------------------------------------------
gcloud sql instances create "$SQL" \
  --project="$PROJETO" \
  --database-version=POSTGRES_16 \
  --tier=db-f1-micro \
  --region="$REGIAO" \
  --root-password="$DB_SENHA"

# IP publico do banco (usado no DB_URL):
IP_BANCO=$(gcloud sql instances describe "$SQL" --project="$PROJETO" \
  --format='get(ipAddresses[0].ipAddress)')
echo "IP do banco: $IP_BANCO"

# ---------------------------------------------------------------------------
# 2. VM da aplicacao + firewall para a porta 8080 (entrada do gateway)
# ---------------------------------------------------------------------------
gcloud compute instances create "$VM" \
  --project="$PROJETO" --zone="$ZONA" \
  --machine-type=e2-small \
  --image-family=debian-12 --image-project=debian-cloud \
  --tags=app-server

gcloud compute firewall-rules create allow-gateway-8080 \
  --project="$PROJETO" --network=default \
  --direction=INGRESS --action=ALLOW --rules=tcp:8080 \
  --source-ranges=0.0.0.0/0 --target-tags=app-server \
  --description="Entrada HTTP do api-gateway (Trabalho 2)"

# IP externo da VM (o frontend aponta para ele):
IP_VM=$(gcloud compute instances describe "$VM" --project="$PROJETO" --zone="$ZONA" \
  --format='get(networkInterfaces[0].accessConfigs[0].natIP)')
echo "IP externo da VM: $IP_VM"

# ---------------------------------------------------------------------------
# 3. Autorizar o IP da VM no Cloud SQL (de onde sai a conexao da aplicacao)
# ---------------------------------------------------------------------------
gcloud sql instances patch "$SQL" --project="$PROJETO" \
  --authorized-networks="$IP_VM/32"

# ---------------------------------------------------------------------------
# 4. Dentro da VM: Docker, clonar o repo e construir as 3 imagens
#    gcloud compute ssh vm-app --zone="$ZONA"
# ---------------------------------------------------------------------------
# sudo apt-get update && sudo apt-get install -y docker.io git
# sudo usermod -aG docker $USER && exit        # reconecte para aplicar o grupo
# git clone https://github.com/jmpcz/frete-grpc.git && cd frete-grpc
#
# docker build -f Dockerfile.servidor -t frete-service .
# docker build -f Dockerfile.pedidos  -t pedidos-service .
# docker build -f Dockerfile.gateway  -t api-gateway .

# ---------------------------------------------------------------------------
# 5. Dentro da VM: subir os 3 containers numa rede Docker
#    (os containers se enxergam pelo nome: frete, pedidos)
#    Troque IP_DO_BANCO e a SENHA pelos valores dos passos 1.
# ---------------------------------------------------------------------------
# docker network create sdnet
#
# docker run -d --name frete --network sdnet frete-service
#
# docker run -d --name pedidos --network sdnet \
#   -e DB_URL='jdbc:postgresql://IP_DO_BANCO:5432/postgres?sslmode=require' \
#   -e DB_USER='postgres' \
#   -e DB_PASSWORD='TROQUE_ESTA_SENHA' \
#   pedidos-service
#
# docker run -d --name gateway --network sdnet -p 8080:8080 \
#   -e FRETE_ALVO='frete:50051' \
#   -e PEDIDOS_ALVO='pedidos:50052' \
#   api-gateway
#
# docker ps                 # os 3 devem aparecer como Up
# docker logs pedidos       # confirmar conexao com o Postgres

# ---------------------------------------------------------------------------
# 6. Testar (na sua maquina). Use o IP externo da VM.
# ---------------------------------------------------------------------------
# TOKEN da autenticacao:
# curl -s -X POST http://IP_DA_VM:8080/auth/login \
#   -H 'Content-Type: application/json' \
#   -d '{"usuario":"admin","senha":"admin123"}'
#
# Criar pedido (troque <TOKEN> pelo valor retornado acima):
# curl -i -X POST http://IP_DA_VM:8080/pedidos \
#   -H 'Content-Type: application/json' -H 'Authorization: Bearer <TOKEN>' \
#   -d '{"item":"notebook","quantidade":2,"cepOrigem":"74000000","cepDestino":"01000000","modalidade":"EXPRESSO","pesoGramas":2200,"comprimentoCm":40,"larguraCm":30,"alturaCm":8,"valorDeclaradoCentavos":750000}'
#
# Frontend: abra frontend/index.html e coloque no campo Gateway: http://IP_DA_VM:8080

# ---------------------------------------------------------------------------
# 7. Conferir os registros no banco (Cloud SQL Studio, banco postgres):
#    SELECT * FROM pedidos;
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# 8. ECONOMIA DE CREDITO: desligue a VM e o banco ao terminar
# ---------------------------------------------------------------------------
# gcloud compute instances stop "$VM" --project="$PROJETO" --zone="$ZONA"
# gcloud sql instances patch "$SQL" --project="$PROJETO" --activation-policy=NEVER
# Para religar o banco: --activation-policy=ALWAYS
