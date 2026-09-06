#!/usr/bin/env bash
# Provisionamento das VMs e da regra de firewall no GCP.
# Rode os blocos um a um, nao o arquivo inteiro de uma vez.
set -euo pipefail

ZONA="us-central1-a"        # ajuste para a zona da sua VM existente
REDE="default"
PORTA_GRPC="50051"

# ---------------------------------------------------------------------------
# 0. Diagnostico: o que ja existe na conta
# ---------------------------------------------------------------------------
gcloud compute instances list
gcloud compute firewall-rules list
gcloud compute networks list

# ---------------------------------------------------------------------------
# 1. VMs (crie apenas a que faltar)
# ---------------------------------------------------------------------------
gcloud compute instances create vm-frete \
  --zone="$ZONA" \
  --machine-type=e2-micro \
  --image-family=debian-12 \
  --image-project=debian-cloud \
  --tags=grpc-server

gcloud compute instances create vm-checkout \
  --zone="$ZONA" \
  --machine-type=e2-micro \
  --image-family=debian-12 \
  --image-project=debian-cloud

# Se voce ja tinha uma VM criada, apenas marque-a com a tag do servidor:
# gcloud compute instances add-tags NOME_DA_VM --zone="$ZONA" --tags=grpc-server

# ---------------------------------------------------------------------------
# 2. Regra de firewall VPC  <-- requisito explicito do trabalho
#    Libera a porta gRPC somente para trafego interno da VPC,
#    e somente nas VMs marcadas com a tag grpc-server.
# ---------------------------------------------------------------------------
gcloud compute firewall-rules create allow-grpc-interno \
  --network="$REDE" \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:"$PORTA_GRPC" \
  --source-ranges=10.128.0.0/9 \
  --target-tags=grpc-server \
  --description="Comunicacao gRPC interna entre checkout-service e frete-service"

# Conferir:
gcloud compute firewall-rules describe allow-grpc-interno

# ---------------------------------------------------------------------------
# 3. Descobrir o IP INTERNO do servidor (usado pelo cliente)
# ---------------------------------------------------------------------------
gcloud compute instances describe vm-frete --zone="$ZONA" \
  --format='get(networkInterfaces[0].networkIP)'

# ---------------------------------------------------------------------------
# 4. Acesso as VMs
# ---------------------------------------------------------------------------
# gcloud compute ssh vm-frete    --zone="$ZONA"
# gcloud compute ssh vm-checkout --zone="$ZONA"

# ---------------------------------------------------------------------------
# 5. Dentro de CADA VM: instalar Docker e clonar o repositorio
# ---------------------------------------------------------------------------
# sudo apt-get update && sudo apt-get install -y docker.io git
# sudo usermod -aG docker $USER && exit    # reconecte para aplicar o grupo
# git clone https://github.com/jmpcz/frete-grpc.git && cd frete-grpc

# Na vm-frete:
# docker build -f Dockerfile.servidor -t frete-service .
# docker run -d --name frete -p 50051:50051 frete-service

# Na vm-checkout (troque pelo IP interno obtido no passo 3):
# docker build -f Dockerfile.cliente -t checkout-service .
# docker run --rm checkout-service --servidor 10.128.0.5:50051 --demo

# ---------------------------------------------------------------------------
# 6. Demonstracao do firewall na apresentacao
# ---------------------------------------------------------------------------
# Derrubar a regra -> o cliente passa a falhar com UNAVAILABLE:
# gcloud compute firewall-rules delete allow-grpc-interno --quiet
#
# Recriar -> volta a funcionar (repita o bloco do passo 2).

# ---------------------------------------------------------------------------
# 7. ECONOMIA DE CREDITO: desligue as VMs ao terminar
# ---------------------------------------------------------------------------
# gcloud compute instances stop vm-frete vm-checkout --zone="$ZONA"
# gcloud compute instances start vm-frete vm-checkout --zone="$ZONA"
