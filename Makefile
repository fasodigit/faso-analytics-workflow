SHELL := /usr/bin/env bash
COMPOSE := podman-compose -f containers/podman-compose.yaml

.PHONY: help bootstrap up down logs ps rebuild migrate test test-api test-engine \
        proto fmt lint audit security-scan keto-init clean

help:
	@echo "FASO-ANALYTICS-WORKFLOW — make targets"
	@echo "  bootstrap   Génère .env + clés SPIFFE locales + init Vault"
	@echo "  up          Démarre la stack (yugabyte, dragonfly, redpanda, vault, keto + 6 services)"
	@echo "  down        Arrête la stack"
	@echo "  logs        Suit les logs (tail -f)"
	@echo "  ps          État des containers"
	@echo "  rebuild     podman build --no-cache des 6 services"
	@echo "  migrate     Applique migrations Flyway sur YugabyteDB"
	@echo "  proto       Génère les stubs Java/Rust/TS depuis proto/v1/*.proto"
	@echo "  test        Tous les tests unitaires + intégration Testcontainers"
	@echo "  test-api    Tests analytics-api (mvn test)"
	@echo "  test-engine Tests analytics-engine (cargo test --workspace)"
	@echo "  fmt         Formatage : mvn spotless:apply + cargo fmt + ng prettier"
	@echo "  lint        Linters : spotbugs + cargo clippy + ng lint"
	@echo "  audit       cargo audit + mvn org.owasp:dependency-check + npm audit"
	@echo "  security-scan  semgrep + zap baseline + gitleaks"
	@echo "  keto-init   Charge les namespaces analytics dans Keto"
	@echo "  clean       Stop + supprime volumes locaux"

bootstrap:
	@test -f .env || cp .env.example .env
	@bash scripts/bootstrap.sh

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

rebuild:
	$(COMPOSE) build --no-cache

migrate:
	$(COMPOSE) exec analytics-api ./mvnw -pl analytics-api flyway:migrate

proto:
	@bash scripts/proto-gen.sh

test: test-api test-engine

test-api:
	cd services/analytics-api && ./mvnw test

test-engine:
	cd services/analytics-engine && cargo test --workspace

fmt:
	cd services/analytics-api && ./mvnw spotless:apply
	cd services/analytics-engine && cargo fmt
	cd services/analytics-frontend && npm run format

lint:
	cd services/analytics-api && ./mvnw spotbugs:check
	cd services/analytics-engine && cargo clippy --workspace -- -D warnings
	cd services/analytics-frontend && npm run lint

audit:
	cd services/analytics-engine && cargo audit
	cd services/analytics-api && ./mvnw org.owasp:dependency-check-maven:check
	cd services/analytics-frontend && npm audit --audit-level=high

security-scan:
	@semgrep --config p/owasp-top-ten --config p/security-audit \
	         --config p/rust --config p/typescript --config p/java \
	         --error --severity ERROR --metrics=off . || true
	@gitleaks detect --redact --no-banner 2>/dev/null || echo "(gitleaks non installé)"

keto-init:
	@bash scripts/keto-init.sh

clean:
	$(COMPOSE) down -v
