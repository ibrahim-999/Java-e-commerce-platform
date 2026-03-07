# ==================== E-Commerce Platform Makefile ====================
# Usage: make <command>
# Run "make help" to see all available commands.

.PHONY: help build run test clean infra-up infra-down infra-logs infra-status generate-cert

# ==================== HELP ====================
help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ==================== USER SERVICE ====================
build: ## Build user-service (compile + package JAR)
	cd user-service && mvn clean package -DskipTests

run: ## Run user-service locally
	cd user-service && mvn spring-boot:run

test: ## Run all tests for user-service
	cd user-service && mvn test

clean: ## Clean build artifacts
	cd user-service && mvn clean

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
