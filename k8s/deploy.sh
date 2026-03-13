#!/usr/bin/env bash
# =============================================================================
# E-Commerce Platform — Kubernetes Deploy Script
# =============================================================================
#
# Deploys the entire e-commerce microservices platform to Kubernetes (minikube)
# in the correct dependency order. Safe to run multiple times (idempotent via
# kubectl apply).
#
# DEPLOYMENT ORDER RATIONALE:
#   1. Namespace          — everything lives here; must exist first
#   2. Secrets & ConfigMap — referenced by every Deployment; must exist before Pods
#   3. Discovery Server   — Eureka; all Spring services register here on startup
#   4. Config Server      — serves centralized config; depends on Eureka
#   5. Databases          — PostgreSQL instances; services fail without their DB
#   6. Kafka              — async messaging bus; producers/consumers need it
#   7. Redis              — caching layer; user-service & product-service use it
#   8. Zipkin             — distributed tracing; services send traces here
#   9. Prometheus          — scrapes metrics from all services
#  10. Grafana            — dashboards; reads from Prometheus
#  11. Business services  — user, product, order, payment, notification
#  12. API Gateway        — routes external traffic; needs services registered in Eureka
#  13. Ingress            — external hostname routing; needs the gateway Service
#
# USAGE:
#   ./deploy.sh              Deploy everything
#   ./deploy.sh --teardown   Delete the entire namespace (removes everything)
#   ./deploy.sh --delete     Same as --teardown
#   ./deploy.sh --status     Show current status of all resources
#
# =============================================================================

set -euo pipefail

# Resolve the directory where this script lives, so paths work regardless of cwd
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

NAMESPACE="ecommerce"
WAIT_TIMEOUT="120s"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }
header()  { echo -e "\n${GREEN}========== $* ==========${NC}"; }

# Wait for a deployment to have all replicas ready.
# Usage: wait_for_deployment <deployment-name>
wait_for_deployment() {
    local deploy_name="$1"
    info "Waiting for deployment/${deploy_name} to be ready (timeout: ${WAIT_TIMEOUT})..."
    if kubectl wait --for=condition=available deployment/"${deploy_name}" \
        -n "${NAMESPACE}" --timeout="${WAIT_TIMEOUT}" 2>/dev/null; then
        success "deployment/${deploy_name} is ready"
    else
        warn "deployment/${deploy_name} did not become ready within ${WAIT_TIMEOUT} — continuing anyway"
    fi
}

# ---------------------------------------------------------------------------
# Teardown mode — delete the entire namespace
# ---------------------------------------------------------------------------

if [[ "${1:-}" == "--teardown" || "${1:-}" == "--delete" ]]; then
    header "TEARING DOWN E-COMMERCE PLATFORM"
    warn "This will delete the entire '${NAMESPACE}' namespace and ALL resources in it."
    echo ""

    # Check if the namespace exists before attempting deletion
    if kubectl get namespace "${NAMESPACE}" &>/dev/null; then
        info "Deleting namespace '${NAMESPACE}'..."
        kubectl delete namespace "${NAMESPACE}" --timeout=120s
        success "Namespace '${NAMESPACE}' and all its resources have been deleted."
    else
        warn "Namespace '${NAMESPACE}' does not exist — nothing to delete."
    fi
    exit 0
fi

# ---------------------------------------------------------------------------
# Status mode — show current state
# ---------------------------------------------------------------------------

if [[ "${1:-}" == "--status" ]]; then
    header "E-COMMERCE PLATFORM STATUS"
    echo ""
    info "Pods:"
    kubectl get pods -n "${NAMESPACE}" -o wide 2>/dev/null || warn "Namespace '${NAMESPACE}' not found"
    echo ""
    info "Services:"
    kubectl get svc -n "${NAMESPACE}" 2>/dev/null || true
    echo ""
    info "Deployments:"
    kubectl get deployments -n "${NAMESPACE}" 2>/dev/null || true
    echo ""
    info "PersistentVolumeClaims:"
    kubectl get pvc -n "${NAMESPACE}" 2>/dev/null || true
    echo ""
    info "Ingress:"
    kubectl get ingress -n "${NAMESPACE}" 2>/dev/null || true
    exit 0
fi

# ---------------------------------------------------------------------------
# Deploy mode — apply everything in dependency order
# ---------------------------------------------------------------------------

header "DEPLOYING E-COMMERCE PLATFORM TO KUBERNETES"
echo ""
info "Script directory: ${SCRIPT_DIR}"
info "Namespace:        ${NAMESPACE}"
info "Wait timeout:     ${WAIT_TIMEOUT}"

# ---- Step 1: Namespace ---------------------------------------------------
# The namespace must exist before any namespaced resource can be created.
header "Step 1/8: Creating namespace"
kubectl apply -f "${SCRIPT_DIR}/namespace.yml"
success "Namespace '${NAMESPACE}' is ready"

# ---- Step 2: Secrets & ConfigMap -----------------------------------------
# Secrets and ConfigMaps are referenced by Deployments via secretKeyRef and
# configMapKeyRef. If they don't exist when a Pod starts, the Pod will crash
# with a "CreateContainerConfigError". Apply them early.
header "Step 2/8: Applying Secrets & ConfigMaps"
kubectl apply -f "${SCRIPT_DIR}/infrastructure/secrets.yml"
kubectl apply -f "${SCRIPT_DIR}/infrastructure/configmap.yml"
success "Secrets and ConfigMaps applied"

# ---- Step 3: Discovery Server (Eureka) ----------------------------------
# Eureka must be up first because every Spring Boot service registers with it
# on startup. Without Eureka, services log connection errors and can't discover
# each other (though they do retry, having Eureka ready avoids noise).
header "Step 3/8: Deploying Discovery Server (Eureka)"
kubectl apply -f "${SCRIPT_DIR}/services/discovery-server.yml"
wait_for_deployment "discovery-server"

# ---- Step 4: Config Server -----------------------------------------------
# Config Server fetches centralized configuration and registers with Eureka.
# Business services call it on startup via spring.config.import. Deploy it
# after Eureka so it can register, but before business services so they can
# pull their config.
header "Step 4/8: Deploying Config Server"
kubectl apply -f "${SCRIPT_DIR}/services/config-server.yml"
wait_for_deployment "config-server"

# ---- Step 5: Data stores & messaging ------------------------------------
# Databases, Kafka, Redis, and Zipkin are infrastructure that business services
# connect to on startup. Deploy them all in parallel (no dependencies between
# them), then wait for each to be ready before moving to business services.
header "Step 5/8: Deploying data stores & messaging infrastructure"

info "Applying PostgreSQL databases (user-db, product-db, order-db, payment-db)..."
kubectl apply -f "${SCRIPT_DIR}/infrastructure/postgres.yml"

info "Applying Kafka (KRaft mode)..."
kubectl apply -f "${SCRIPT_DIR}/infrastructure/kafka.yml"

info "Applying Redis..."
kubectl apply -f "${SCRIPT_DIR}/infrastructure/redis.yml"

info "Applying Zipkin..."
kubectl apply -f "${SCRIPT_DIR}/infrastructure/zipkin.yml"

# Wait for the critical data stores — services will fail their health checks
# if the database or Kafka is unreachable.
info "Waiting for data stores to become ready..."
wait_for_deployment "user-db"
wait_for_deployment "product-db"
wait_for_deployment "order-db"
wait_for_deployment "payment-db"
wait_for_deployment "kafka"
wait_for_deployment "redis"
wait_for_deployment "zipkin"

# ---- Step 6: Monitoring (Prometheus & Grafana) ---------------------------
# Prometheus and Grafana are non-blocking — no service depends on them.
# We deploy them here so they're scraping metrics by the time services start.
header "Step 6/8: Deploying monitoring stack (Prometheus & Grafana)"

info "Applying Prometheus..."
kubectl apply -f "${SCRIPT_DIR}/infrastructure/prometheus.yml"

info "Applying Grafana..."
kubectl apply -f "${SCRIPT_DIR}/infrastructure/grafana.yml"

# Don't block on monitoring — services don't depend on them.
# They'll come up in the background while business services deploy.
success "Monitoring manifests applied (coming up in background)"

# ---- Step 7: Business services ------------------------------------------
# These are the core application microservices. They depend on:
#   - Eureka (service discovery) — already ready
#   - Config Server (centralized config) — already ready
#   - PostgreSQL (their database) — already ready
#   - Kafka (event bus) — already ready
#   - Redis (caching, user & product services) — already ready
#
# They can all start in parallel since they're independent of each other.
header "Step 7/8: Deploying business services"

info "Applying user-service..."
kubectl apply -f "${SCRIPT_DIR}/services/user-service.yml"

info "Applying product-service..."
kubectl apply -f "${SCRIPT_DIR}/services/product-service.yml"

info "Applying order-service..."
kubectl apply -f "${SCRIPT_DIR}/services/order-service.yml"

info "Applying payment-service..."
kubectl apply -f "${SCRIPT_DIR}/services/payment-service.yml"

info "Applying notification-service..."
kubectl apply -f "${SCRIPT_DIR}/services/notification-service.yml"

# Wait for all business services so the API Gateway can route to them
wait_for_deployment "user-service"
wait_for_deployment "product-service"
wait_for_deployment "order-service"
wait_for_deployment "payment-service"
wait_for_deployment "notification-service"

# ---- Step 8: API Gateway & Ingress --------------------------------------
# The API Gateway is the single entry point. It discovers services via Eureka,
# so all services should be registered before the gateway starts routing.
# The Ingress resource routes external hostnames to the gateway (and to
# Eureka/Zipkin/Prometheus/Grafana dashboards for convenience).
header "Step 8/8: Deploying API Gateway & Ingress"

info "Applying API Gateway..."
kubectl apply -f "${SCRIPT_DIR}/services/api-gateway.yml"
wait_for_deployment "api-gateway"

info "Applying Ingress rules..."
kubectl apply -f "${SCRIPT_DIR}/ingress.yml"
success "Ingress applied"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

header "DEPLOYMENT COMPLETE"
echo ""
info "All manifests have been applied. Here is the current state:"
echo ""
kubectl get pods -n "${NAMESPACE}"
echo ""

# Print access information
info "Access points:"
echo "  API Gateway (NodePort): http://<minikube-ip>:30080"
echo "  API Gateway (Ingress):  http://ecommerce.local"
echo "  Eureka Dashboard:       http://eureka.local"
echo "  Zipkin Dashboard:       http://zipkin.local"
echo "  Prometheus:             http://prometheus.local"
echo "  Grafana:                http://grafana.local  (admin/admin)"
echo ""
info "To get minikube IP:  minikube ip"
info "To enable Ingress:   minikube addons enable ingress"
info "Add to /etc/hosts:   \$(minikube ip)  ecommerce.local eureka.local zipkin.local prometheus.local grafana.local"
echo ""
success "Done!"
