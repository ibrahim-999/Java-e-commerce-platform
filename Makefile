# ==================== E-Commerce Platform Makefile ====================
# Usage: make <command>
# Run "make help" to see all available commands.

.PHONY: help build build-all test test-all clean clean-all generate-cert \
       infra-up infra-down infra-logs infra-status infra-restart infra-clean \
       up down logs status restart rebuild \
       run-user run-product run-order run-payment run-notification run-all stop-all

# ==================== HELP ====================
help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ==================== BUILD & TEST (all services) ====================
build-all: ## Build all services (compile + package JAR)
	cd user-service && mvn clean package -DskipTests
	cd product-service && mvn clean package -DskipTests
	cd order-service && mvn clean package -DskipTests
	cd payment-service && mvn clean package -DskipTests
	cd notification-service && mvn clean package -DskipTests

test-all: ## Run tests for all services (requires: make infra-up)
	cd user-service && mvn test
	cd product-service && mvn test
	cd order-service && mvn test
	cd payment-service && mvn test
	cd notification-service && mvn test

clean-all: ## Clean build artifacts for all services
	cd user-service && mvn clean
	cd product-service && mvn clean
	cd order-service && mvn clean
	cd payment-service && mvn clean
	cd notification-service && mvn clean

# ==================== RUN INDIVIDUAL SERVICES ====================
# Each command opens the service in a NEW terminal window.
# Requires: make infra-up (databases + Kafka must be running first)
#
# Usage:  make run-user        (opens user-service in a new terminal)
#         make run-all          (opens ALL services, each in its own terminal)

run-user: ## Run user-service in a new terminal
	gnome-terminal --title="user-service :8081" -- bash -c "cd $(CURDIR)/user-service && mvn spring-boot:run; exec bash"

run-product: ## Run product-service in a new terminal
	gnome-terminal --title="product-service :8082" -- bash -c "cd $(CURDIR)/product-service && mvn spring-boot:run; exec bash"

run-order: ## Run order-service in a new terminal
	gnome-terminal --title="order-service :8083" -- bash -c "cd $(CURDIR)/order-service && mvn spring-boot:run; exec bash"

run-payment: ## Run payment-service in a new terminal
	gnome-terminal --title="payment-service :8084" -- bash -c "cd $(CURDIR)/payment-service && mvn spring-boot:run; exec bash"

run-notification: ## Run notification-service in a new terminal
	gnome-terminal --title="notification-service :8085" -- bash -c "cd $(CURDIR)/notification-service && mvn spring-boot:run; exec bash"

run-all: ## Run ALL services, each in its own terminal
	@echo "Starting all services in separate terminals..."
	$(MAKE) run-user
	$(MAKE) run-product
	$(MAKE) run-payment
	@sleep 2
	$(MAKE) run-order
	$(MAKE) run-notification
	@echo "All services launched! Check the 5 terminal windows."

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
infra-up: ## Start infrastructure (PostgreSQL, pgAdmin)
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
