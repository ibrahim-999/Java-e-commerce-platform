# ==================== E-Commerce Platform Makefile ====================
# Usage: make <command>
# Run "make help" to see all available commands.

.PHONY: help build build-all test test-all clean clean-all generate-cert \
       infra-up infra-down infra-logs infra-status infra-restart infra-clean \
       up down logs status restart rebuild full-clean e2e-test \
       run-discovery run-config run-gateway \
       run-user run-product run-order run-payment run-notification run-all stop-all

# ==================== HELP ====================
help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ==================== BUILD & TEST (all services) ====================
build-all: ## Build all services (compile + package JAR)
	cd discovery-server && mvn clean package -DskipTests
	cd config-server && mvn clean package -DskipTests
	cd api-gateway && mvn clean package -DskipTests
	cd user-service && mvn clean package -DskipTests
	cd product-service && mvn clean package -DskipTests
	cd order-service && mvn clean package -DskipTests
	cd payment-service && mvn clean package -DskipTests
	cd notification-service && mvn clean package -DskipTests

test-all: ## Run tests for all services (requires: make infra-up)
	set -a; source .env; set +a; \
	cd user-service && mvn test && cd .. && \
	cd product-service && mvn test && cd .. && \
	cd order-service && mvn test && cd .. && \
	cd payment-service && mvn test && cd .. && \
	cd notification-service && mvn test

clean-all: ## Clean build artifacts for all services
	cd discovery-server && mvn clean
	cd config-server && mvn clean
	cd api-gateway && mvn clean
	cd user-service && mvn clean
	cd product-service && mvn clean
	cd order-service && mvn clean
	cd payment-service && mvn clean
	cd notification-service && mvn clean

# ==================== RUN INFRASTRUCTURE SERVICES ====================
# These are the platform services that other services depend on.
# Start order: discovery-server → config-server → gateway
# Requires: make infra-up (databases + Kafka + Zipkin must be running first)
#
# Each command loads .env for secrets (EUREKA_USERNAME, EUREKA_PASSWORD, etc.)
# "set -a" exports all variables so child processes (mvn) inherit them.

ENV_LOAD = set -a; source $(CURDIR)/.env; set +a;

run-discovery: ## Run discovery-server (Eureka) in a new terminal
	gnome-terminal --title="discovery-server :8761" -- bash -c "$(ENV_LOAD) cd $(CURDIR)/discovery-server && mvn spring-boot:run; exec bash"

run-config: ## Run config-server in a new terminal
	gnome-terminal --title="config-server :8888" -- bash -c "$(ENV_LOAD) cd $(CURDIR)/config-server && mvn spring-boot:run; exec bash"

run-gateway: ## Run api-gateway in a new terminal
	gnome-terminal --title="api-gateway :8060" -- bash -c "$(ENV_LOAD) cd $(CURDIR)/api-gateway && mvn spring-boot:run; exec bash"

# ==================== RUN BUSINESS SERVICES ====================
# Each command opens the service in a NEW terminal window.
# Requires: make infra-up (databases + Kafka must be running first)
#
# Usage:  make run-user        (opens user-service in a new terminal)
#         make run-all          (opens ALL services, each in its own terminal)

run-user: ## Run user-service in a new terminal
	gnome-terminal --title="user-service :8081" -- bash -c "$(ENV_LOAD) cd $(CURDIR)/user-service && mvn spring-boot:run; exec bash"

run-product: ## Run product-service in a new terminal
	gnome-terminal --title="product-service :8082" -- bash -c "$(ENV_LOAD) cd $(CURDIR)/product-service && mvn spring-boot:run; exec bash"

run-order: ## Run order-service in a new terminal
	gnome-terminal --title="order-service :8083" -- bash -c "$(ENV_LOAD) cd $(CURDIR)/order-service && mvn spring-boot:run; exec bash"

run-payment: ## Run payment-service in a new terminal
	gnome-terminal --title="payment-service :8084" -- bash -c "$(ENV_LOAD) cd $(CURDIR)/payment-service && mvn spring-boot:run; exec bash"

run-notification: ## Run notification-service in a new terminal
	gnome-terminal --title="notification-service :8085" -- bash -c "$(ENV_LOAD) cd $(CURDIR)/notification-service && mvn spring-boot:run; exec bash"

run-all: ## Run ALL services, each in its own terminal
	@echo "Starting all services in separate terminals..."
	$(MAKE) run-discovery
	@sleep 10
	$(MAKE) run-config
	@sleep 5
	$(MAKE) run-gateway
	$(MAKE) run-user
	$(MAKE) run-product
	$(MAKE) run-payment
	@sleep 2
	$(MAKE) run-order
	$(MAKE) run-notification
	@echo "All 8 services launched! Check the terminal windows."

stop-all: ## Stop all locally-running services
	@echo "Stopping all Spring Boot services..."
	-pkill -f "spring-boot:run" 2>/dev/null || true
	@echo "All services stopped."

# ==================== SSL ====================
generate-cert: ## Generate a self-signed SSL certificate for HTTPS
	keytool -genkeypair -alias userservice -keyalg RSA -keysize 2048 \
		-storetype PKCS12 \
		-keystore user-service/src/main/resources/keystore.p12 \
		-validity 3650 -storepass changeit \
		-dname "CN=localhost, OU=Dev, O=ECommerce, L=City, ST=State, C=US"

# ==================== DOCKER INFRASTRUCTURE ====================
infra-up: ## Start infrastructure (PostgreSQL, Kafka, Redis, Zipkin, pgAdmin)
	docker compose -f docker-compose.infra.yml up -d

infra-down: ## Stop infrastructure
	docker compose -f docker-compose.infra.yml down

infra-logs: ## Show infrastructure logs (follow mode)
	docker compose -f docker-compose.infra.yml logs -f

infra-status: ## Show status of infrastructure containers
	docker compose -f docker-compose.infra.yml ps

infra-restart: ## Restart infrastructure
	docker compose -f docker-compose.infra.yml down && docker compose -f docker-compose.infra.yml up -d

infra-clean: ## Stop infrastructure and DELETE all data (volumes)
	docker compose -f docker-compose.infra.yml down -v

# ==================== FULL STACK (docker-compose.yml) ====================
up: ## Start the full platform (all services + infrastructure)
	docker compose up -d --build

down: ## Stop the full platform
	docker compose down

logs: ## Show logs for all services (follow mode)
	docker compose logs -f

status: ## Show status of all containers
	docker compose ps

restart: ## Restart the full platform
	docker compose down && docker compose up -d --build

rebuild: ## Rebuild and restart a single service (usage: make rebuild s=user-service)
	docker compose up -d --build $(s)

full-clean: ## Stop everything and DELETE all data (volumes, images)
	docker compose down -v --rmi local

# ==================== END-TO-END TEST ====================
e2e-test: ## Run end-to-end test against running stack (requires: make up)
	@echo "=== E2E Test: Full User Journey ==="
	@echo ""
	@echo "1. Registering user..."
	@curl -sk -X POST http://localhost:8060/api/auth/register \
	  -H "Content-Type: application/json" \
	  -d '{"firstName":"E2E","lastName":"Test","email":"e2e@test.com","password":"password123"}' \
	  | python3 -m json.tool
	@echo ""
	@echo "2. Creating product..."
	@curl -s -X POST http://localhost:8060/api/products \
	  -H "Content-Type: application/json" \
	  -d '{"name":"E2E Widget","description":"Test product","price":19.99,"sku":"E2E-001","stockQuantity":50,"categoryId":1}' \
	  | python3 -m json.tool
	@echo ""
	@echo "3. Placing order..."
	@curl -s -X POST http://localhost:8060/api/orders \
	  -H "Content-Type: application/json" \
	  -d '{"userId":1,"items":[{"productId":1,"quantity":1}],"paymentMethod":"CREDIT_CARD"}' \
	  | python3 -m json.tool
	@echo ""
	@echo "=== E2E Test Complete ==="
