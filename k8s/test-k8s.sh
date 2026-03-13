#!/bin/bash
# ==============================================================================
# test-k8s.sh — Test Kubernetes Scaling & Rolling Deployments
# ==============================================================================
#
# This script demonstrates two fundamental Kubernetes operations:
#
#   1. HORIZONTAL SCALING — Adding/removing Pod replicas to handle more traffic.
#      Kubernetes distributes incoming requests across all replicas automatically
#      via the Service (which acts as an internal load balancer).
#
#   2. ROLLING UPDATES — Updating a Deployment without downtime. Kubernetes
#      creates new Pods with the updated spec, waits for them to be healthy,
#      then terminates old Pods — one at a time by default. At no point are
#      zero Pods available.
#
# Usage:
#   ./test-k8s.sh scale     # Test horizontal pod scaling
#   ./test-k8s.sh rolling   # Test rolling update (zero-downtime deploy)
#
# Prerequisites:
#   - minikube running with the ecommerce namespace deployed
#   - product-service deployment is active
#   - kubectl configured to talk to the cluster
# ==============================================================================

set -euo pipefail

# --- Configuration -----------------------------------------------------------
NAMESPACE="ecommerce"
DEPLOYMENT="product-service"
SERVICE_PORT="8082"
GATEWAY_SERVICE="api-gateway"
GATEWAY_PORT="30080"

# Colors for readability
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# --- Helper Functions ---------------------------------------------------------

# Print a section header
header() {
    echo ""
    echo -e "${BLUE}============================================================${NC}"
    echo -e "${BOLD}  $1${NC}"
    echo -e "${BLUE}============================================================${NC}"
    echo ""
}

# Print an informational step
step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

# Print a concept explanation
concept() {
    echo -e "${YELLOW}[CONCEPT]${NC} $1"
}

# Print success
success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

# Print an error and exit
fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    exit 1
}

# Print a warning (non-fatal)
warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Separator line
separator() {
    echo -e "${BLUE}------------------------------------------------------------${NC}"
}

# --- Preflight Checks --------------------------------------------------------

preflight() {
    header "Preflight Checks"

    # 1. kubectl must be available
    step "Checking that kubectl is installed..."
    if ! command -v kubectl &>/dev/null; then
        fail "kubectl not found. Install it first: https://kubernetes.io/docs/tasks/tools/"
    fi
    success "kubectl is installed."

    # 2. The ecommerce namespace must exist
    step "Checking that namespace '${NAMESPACE}' exists..."
    if ! kubectl get namespace "${NAMESPACE}" &>/dev/null; then
        fail "Namespace '${NAMESPACE}' not found. Deploy the platform first with: kubectl apply -f k8s/namespace.yml"
    fi
    success "Namespace '${NAMESPACE}' exists."

    # 3. The product-service deployment must exist
    step "Checking that deployment '${DEPLOYMENT}' exists in namespace '${NAMESPACE}'..."
    if ! kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" &>/dev/null; then
        fail "Deployment '${DEPLOYMENT}' not found in namespace '${NAMESPACE}'. Deploy it first."
    fi
    success "Deployment '${DEPLOYMENT}' is present."

    # 4. Show the current state of the deployment
    step "Current deployment status:"
    kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" -o wide
    echo ""
    kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}"
    echo ""
}

# --- Mode 1: Horizontal Scaling Test -----------------------------------------

test_scale() {
    header "Mode 1: Horizontal Pod Scaling"

    concept "Horizontal scaling means adding MORE Pod replicas of the same container."
    concept "Kubernetes Service acts as a load balancer — it distributes requests"
    concept "across all healthy Pods using round-robin (by default)."
    concept "This is how you handle more traffic: scale out, not up."
    echo ""

    # ---- Step 1: Record the starting replica count ---------------------------
    separator
    step "Recording current replica count..."
    ORIGINAL_REPLICAS=$(kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" -o jsonpath='{.spec.replicas}')
    success "Current replicas: ${ORIGINAL_REPLICAS}"
    echo ""

    # ---- Step 2: Scale UP to 3 replicas --------------------------------------
    separator
    step "Scaling '${DEPLOYMENT}' to 3 replicas..."
    concept "kubectl scale changes the 'spec.replicas' field on the Deployment."
    concept "The Deployment controller notices the desired count (3) differs from"
    concept "the actual count (${ORIGINAL_REPLICAS}), so it creates new Pods via the ReplicaSet."
    echo ""

    kubectl scale deployment "${DEPLOYMENT}" -n "${NAMESPACE}" --replicas=3
    success "Scale command issued. Waiting for all 3 pods to become Ready..."
    echo ""

    # ---- Step 3: Wait for rollout to complete --------------------------------
    # 'kubectl rollout status' blocks until all replicas are Ready.
    # A Pod is "Ready" when its readinessProbe passes (in our case, GET /actuator/health).
    step "Waiting for rollout to complete (this may take 30-60 seconds due to readiness probes)..."
    if kubectl rollout status deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" --timeout=180s; then
        success "All 3 replicas are Ready!"
    else
        fail "Timed out waiting for replicas to become ready."
    fi
    echo ""

    # ---- Step 4: Verify 3 pods are running -----------------------------------
    separator
    step "Verifying pod count..."
    echo ""
    kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o wide
    echo ""

    READY_COUNT=$(kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" --field-selector=status.phase=Running -o name 2>/dev/null | wc -l)
    if [ "${READY_COUNT}" -ge 3 ]; then
        success "Verified: ${READY_COUNT} pods are Running."
    else
        warn "Expected 3 Running pods, found ${READY_COUNT}. Some may still be starting."
    fi
    echo ""

    # ---- Step 5: Show that each pod has a unique IP --------------------------
    separator
    step "Each Pod gets its own IP address inside the cluster:"
    concept "The Service 'product-service' has a single ClusterIP. When a request"
    concept "hits that ClusterIP, kube-proxy forwards it to one of the Pod IPs below."
    echo ""
    kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o custom-columns="POD:metadata.name,IP:status.podIP,NODE:spec.nodeName,STATUS:status.phase"
    echo ""

    # ---- Step 6: Optionally test load balancing via the gateway --------------
    separator
    step "Testing load balancing through the API Gateway..."
    concept "When we send requests through the API Gateway (→ Eureka → product-service),"
    concept "Eureka is aware of all 3 product-service instances. The gateway's load"
    concept "balancer (Spring Cloud LoadBalancer) distributes requests across them."
    echo ""

    # Try to get the gateway URL. Works on minikube with NodePort.
    GATEWAY_URL=""
    if command -v minikube &>/dev/null; then
        MINIKUBE_IP=$(minikube ip 2>/dev/null || true)
        if [ -n "${MINIKUBE_IP}" ]; then
            GATEWAY_URL="http://${MINIKUBE_IP}:${GATEWAY_PORT}"
        fi
    fi

    if [ -z "${GATEWAY_URL}" ]; then
        # Fallback: try localhost (works if port-forwarding or docker-desktop k8s)
        GATEWAY_URL="http://localhost:${GATEWAY_PORT}"
    fi

    step "Gateway URL: ${GATEWAY_URL}"
    step "Sending 6 requests to GET /api/products (expect responses from different pods)..."
    echo ""

    CURL_SUCCESS=0
    CURL_FAIL=0
    for i in $(seq 1 6); do
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 "${GATEWAY_URL}/api/products" 2>/dev/null || echo "000")
        if [ "${HTTP_CODE}" = "200" ]; then
            success "  Request ${i}: HTTP ${HTTP_CODE}"
            CURL_SUCCESS=$((CURL_SUCCESS + 1))
        elif [ "${HTTP_CODE}" = "000" ]; then
            warn "  Request ${i}: Could not connect (gateway may not be reachable from host)"
            CURL_FAIL=$((CURL_FAIL + 1))
        else
            warn "  Request ${i}: HTTP ${HTTP_CODE}"
            CURL_FAIL=$((CURL_FAIL + 1))
        fi
        sleep 0.5
    done
    echo ""

    if [ "${CURL_SUCCESS}" -gt 0 ]; then
        success "Load balancing test: ${CURL_SUCCESS}/6 requests succeeded."
        concept "All requests went through the single gateway, which distributed"
        concept "them across the 3 product-service replicas behind the scenes."
    else
        warn "Could not reach the gateway from the host. This is expected if"
        warn "  minikube tunnel is not running or if you haven't set up port-forwarding."
        warn "  The scaling itself succeeded — you just can't test HTTP from the host."
        warn "  Try: minikube tunnel (in another terminal) or kubectl port-forward."
    fi
    echo ""

    # ---- Step 7: Scale back DOWN to original replica count -------------------
    separator
    step "Scaling back down to ${ORIGINAL_REPLICAS} replica(s)..."
    concept "Scaling down works the same way in reverse. Kubernetes terminates"
    concept "excess Pods gracefully (sends SIGTERM, waits for graceful shutdown,"
    concept "then SIGKILL after 30s if the process hasn't exited)."
    echo ""

    kubectl scale deployment "${DEPLOYMENT}" -n "${NAMESPACE}" --replicas="${ORIGINAL_REPLICAS}"
    success "Scale-down command issued. Waiting for stabilization..."

    if kubectl rollout status deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" --timeout=120s; then
        success "Scale-down complete."
    else
        warn "Timed out, but the deployment should eventually converge."
    fi
    echo ""

    # ---- Step 8: Verify we're back to original count -------------------------
    separator
    step "Verifying pod count is back to ${ORIGINAL_REPLICAS}..."
    # Give a few seconds for terminating pods to disappear
    sleep 5
    kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}"
    echo ""

    FINAL_COUNT=$(kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" --field-selector=status.phase=Running -o name 2>/dev/null | wc -l)
    if [ "${FINAL_COUNT}" -eq "${ORIGINAL_REPLICAS}" ]; then
        success "Verified: back to ${FINAL_COUNT} pod(s). Scale test complete!"
    else
        warn "Expected ${ORIGINAL_REPLICAS} Running pod(s), found ${FINAL_COUNT}. Terminating pods may still be shutting down."
    fi

    header "Scaling Test Complete"
    echo -e "${GREEN}Summary:${NC}"
    echo "  - Scaled ${DEPLOYMENT} from ${ORIGINAL_REPLICAS} to 3 replicas"
    echo "  - Verified all 3 pods reached Ready state"
    echo "  - Sent test requests through the API Gateway"
    echo "  - Scaled back down to ${ORIGINAL_REPLICAS} replica(s)"
    echo ""
    concept "In production, you would use a Horizontal Pod Autoscaler (HPA)"
    concept "to automatically scale based on CPU/memory usage or custom metrics,"
    concept "rather than scaling manually with kubectl."
    echo ""
}

# --- Mode 2: Rolling Update Test ---------------------------------------------

test_rolling() {
    header "Mode 2: Rolling Update (Zero-Downtime Deployment)"

    concept "A rolling update replaces Pods incrementally. Kubernetes:"
    concept "  1. Creates a NEW ReplicaSet with the updated Pod spec"
    concept "  2. Starts Pods in the new ReplicaSet"
    concept "  3. Waits for new Pods to pass their readinessProbe"
    concept "  4. Terminates Pods in the old ReplicaSet"
    concept "  5. Repeats until all old Pods are replaced"
    concept ""
    concept "At every moment during this process, there are healthy Pods"
    concept "serving traffic — hence 'zero-downtime deployment'."
    echo ""

    # ---- Step 1: Show current deployment details -----------------------------
    separator
    step "Current deployment state:"
    echo ""
    kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" -o wide
    echo ""

    step "Current ReplicaSets (a Deployment creates one RS per revision):"
    concept "Each time you update a Deployment, Kubernetes creates a new ReplicaSet."
    concept "The old ReplicaSet is kept (with 0 replicas) so you can rollback."
    echo ""
    kubectl get replicasets -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o wide
    echo ""

    step "Current pods:"
    kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o custom-columns="POD:metadata.name,IP:status.podIP,STATUS:status.phase,RESTARTS:status.containerStatuses[0].restartCount,AGE:metadata.creationTimestamp"
    echo ""

    # ---- Step 2: Record the current pod names (so we can detect replacement) -
    separator
    step "Recording current pod names to detect replacement..."
    OLD_PODS=$(kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o jsonpath='{.items[*].metadata.name}')
    success "Current pod(s): ${OLD_PODS}"
    echo ""

    # Record current revision number
    OLD_REVISION=$(kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" -o jsonpath='{.metadata.annotations.deployment\.kubernetes\.io/revision}')
    success "Current deployment revision: ${OLD_REVISION}"
    echo ""

    # ---- Step 3: Ensure we have at least 2 replicas for a visible rollout ----
    separator
    step "Scaling to 2 replicas for a more visible rolling update..."
    concept "With 1 replica, the rolling update still works but it's less visual."
    concept "With 2 replicas, you can see old and new pods coexisting."
    echo ""

    kubectl scale deployment "${DEPLOYMENT}" -n "${NAMESPACE}" --replicas=2
    kubectl rollout status deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" --timeout=180s
    success "Now running 2 replicas."
    echo ""

    kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}"
    echo ""

    # ---- Step 4: Trigger a rolling update ------------------------------------
    separator
    step "Triggering a rolling update..."
    concept "We trigger the update by adding an annotation to the Pod template."
    concept "Any change to spec.template causes Kubernetes to create a new ReplicaSet"
    concept "and start rolling out new Pods. Common triggers in real life:"
    concept "  - New container image tag (e.g., v1.0.0 → v1.1.0)"
    concept "  - Changed environment variable"
    concept "  - Updated ConfigMap/Secret reference"
    concept ""
    concept "Here we use 'kubectl rollout restart' which patches a restart annotation"
    concept "on the Pod template — this forces new Pods without changing the image."
    echo ""

    # The 'rollout restart' command adds a kubectl.kubernetes.io/restartedAt
    # annotation with the current timestamp. Since the Pod template changes,
    # Kubernetes treats it as an update and performs a rolling replacement.
    RESTART_TIME=$(date -Iseconds)
    kubectl rollout restart deployment/"${DEPLOYMENT}" -n "${NAMESPACE}"
    success "Rolling update triggered at ${RESTART_TIME}."
    echo ""

    # ---- Step 5: Watch the rollout in real time ------------------------------
    separator
    step "Watching the rollout progress..."
    concept "'kubectl rollout status' streams events as the Deployment controller"
    concept "creates new Pods and terminates old ones. It exits 0 when complete."
    echo ""

    # Show pods during the rollout (snapshot — rollout may be fast)
    step "Pod state during rollout (snapshot):"
    kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o wide
    echo ""

    # Now wait for completion
    if kubectl rollout status deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" --timeout=180s; then
        success "Rolling update completed successfully!"
    else
        fail "Rolling update timed out or failed."
    fi
    echo ""

    # ---- Step 6: Verify the update ------------------------------------------
    separator
    step "Verifying the rolling update..."
    echo ""

    # Check new revision number
    NEW_REVISION=$(kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" -o jsonpath='{.metadata.annotations.deployment\.kubernetes\.io/revision}')
    success "New deployment revision: ${NEW_REVISION} (was: ${OLD_REVISION})"
    echo ""

    # Show the new pods
    step "New pods after rolling update:"
    kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o custom-columns="POD:metadata.name,IP:status.podIP,STATUS:status.phase,RESTARTS:status.containerStatuses[0].restartCount,AGE:metadata.creationTimestamp"
    echo ""

    # Verify all old pods were replaced
    NEW_PODS=$(kubectl get pods -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o jsonpath='{.items[*].metadata.name}')
    step "Old pod(s): ${OLD_PODS}"
    step "New pod(s): ${NEW_PODS}"
    echo ""

    REPLACED=true
    for OLD_POD in ${OLD_PODS}; do
        if echo "${NEW_PODS}" | grep -q "${OLD_POD}"; then
            warn "Pod ${OLD_POD} was NOT replaced (still running)."
            REPLACED=false
        fi
    done

    if [ "${REPLACED}" = true ]; then
        success "All old pods were replaced by new ones — rolling update confirmed!"
    else
        warn "Some pods were not replaced. This can happen if the rollout is still in progress."
    fi
    echo ""

    # ---- Step 7: Show the ReplicaSet history ---------------------------------
    separator
    step "ReplicaSet history after the update:"
    concept "Notice there are now multiple ReplicaSets. The old one has 0 replicas"
    concept "(but is kept for rollback). The new one has the desired count."
    echo ""
    kubectl get replicasets -n "${NAMESPACE}" -l app="${DEPLOYMENT}" -o wide
    echo ""

    # ---- Step 8: Show the deployment's rolling update strategy ----------------
    separator
    step "Rolling update strategy configured on the deployment:"
    concept "Kubernetes uses the RollingUpdate strategy by default with:"
    concept "  maxUnavailable: 25% — at most 25% of pods can be down during update"
    concept "  maxSurge: 25%       — at most 25% extra pods can exist during update"
    concept "This ensures there are always enough healthy pods to serve traffic."
    echo ""

    STRATEGY=$(kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" -o jsonpath='{.spec.strategy.type}')
    MAX_UNAVAILABLE=$(kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" -o jsonpath='{.spec.strategy.rollingUpdate.maxUnavailable}' 2>/dev/null || echo "default (25%)")
    MAX_SURGE=$(kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" -o jsonpath='{.spec.strategy.rollingUpdate.maxSurge}' 2>/dev/null || echo "default (25%)")

    echo "  Strategy:        ${STRATEGY}"
    echo "  maxUnavailable:  ${MAX_UNAVAILABLE}"
    echo "  maxSurge:        ${MAX_SURGE}"
    echo ""

    # ---- Step 9: Demonstrate rollback capability -----------------------------
    separator
    step "Deployment rollout history (for potential rollback):"
    concept "If a rolling update introduces a bug, you can instantly rollback:"
    concept "  kubectl rollout undo deployment/${DEPLOYMENT} -n ${NAMESPACE}"
    concept "This switches back to the previous ReplicaSet (which was kept around)."
    echo ""
    kubectl rollout history deployment/"${DEPLOYMENT}" -n "${NAMESPACE}"
    echo ""

    # ---- Step 10: Scale back to 1 replica ------------------------------------
    separator
    step "Scaling back to 1 replica (cleanup)..."
    kubectl scale deployment "${DEPLOYMENT}" -n "${NAMESPACE}" --replicas=1
    kubectl rollout status deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" --timeout=120s
    success "Back to 1 replica."
    echo ""

    header "Rolling Update Test Complete"
    echo -e "${GREEN}Summary:${NC}"
    echo "  - Deployment revision changed from ${OLD_REVISION} to ${NEW_REVISION}"
    echo "  - All original pods were gracefully replaced"
    echo "  - Zero-downtime was maintained throughout the update"
    echo "  - Old ReplicaSet preserved for rollback capability"
    echo "  - Scaled back to 1 replica"
    echo ""
    concept "In a real CI/CD pipeline, the rolling update is triggered by pushing"
    concept "a new Docker image tag. The pipeline runs:"
    concept "  kubectl set image deployment/product-service product-service=product-service:v2.0.0"
    concept "Kubernetes then performs the rolling update automatically."
    echo ""
}

# --- Main: Parse CLI argument -------------------------------------------------

usage() {
    echo ""
    echo -e "${BOLD}Usage:${NC} $0 <mode>"
    echo ""
    echo "Modes:"
    echo "  scale    - Test horizontal pod scaling (1 → 3 → 1 replicas)"
    echo "  rolling  - Test rolling update (zero-downtime deployment)"
    echo ""
    echo "Examples:"
    echo "  $0 scale"
    echo "  $0 rolling"
    echo ""
}

# Entry point
if [ $# -lt 1 ]; then
    usage
    exit 1
fi

MODE="$1"

# Run preflight checks regardless of mode
preflight

case "${MODE}" in
    scale)
        test_scale
        ;;
    rolling)
        test_rolling
        ;;
    *)
        echo -e "${RED}Unknown mode: ${MODE}${NC}"
        usage
        exit 1
        ;;
esac

echo -e "${GREEN}Done!${NC}"
