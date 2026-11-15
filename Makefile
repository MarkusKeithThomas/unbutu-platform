# Lệnh tắt cho máy dev. Gõ `make` (hoặc `make help`) để xem danh sách.
# s=<service> (1 hoặc nhiều, cách nhau dấu cách) để chỉ làm việc với các service đó.

COMPOSE := docker compose -f deploy/compose.local.yml
s ?=

# Container hạ tầng (không phải service Java → không build jar).
INFRA := postgres

# Port cố định trên máy Mac – giống nhau dù service chạy trong container hay IntelliJ.
PORT_api-gateway          := 8080
PORT_identity-service     := 8081
PORT_catalog-service      := 8082
PORT_inventory-service    := 8083
PORT_booking-service      := 8084
PORT_payment-service      := 8085
PORT_notification-service := 8086

ALL  := $(shell $(COMPOSE) config --services 2>/dev/null)
APPS := $(filter-out $(INFRA),$(ALL))

# Task Gradle build jar cho danh sách service Java ($1); rỗng nếu không có service Java nào.
jar_tasks = $(foreach a,$(filter-out $(INFRA),$(1)),:services:$(a):bootJar)
# catalog-service → CATALOG_SERVICE_URL=http://host.docker.internal:8082
ide_env   = $(foreach a,$(1),$(shell echo $(a) | sed 's/-service$$//' | tr 'a-z-' 'A-Z_')_SERVICE_URL=http://host.docker.internal:$(PORT_$(a)))

.DEFAULT_GOAL := help
.PHONY: help build test jar up down restart ide logs ps clean

help: ## Liệt kê các lệnh
	@grep -E '^[a-z]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  make %-8s %s\n", $$1, $$2}'

build: ## Build + chạy toàn bộ test (như CI)
	./gradlew build

test: ## Chỉ chạy test
	./gradlew test

jar: ## Build jar, bỏ qua test (tất cả, hoặc s=<service>)
	$(eval TASKS := $(if $(s),$(call jar_tasks,$(s)),$(call jar_tasks,$(APPS))))
	$(if $(TASKS),./gradlew $(TASKS),@echo "Không có service Java cần build")

up: jar ## Chạy tất cả trong container (hoặc s=<service>, kèm hạ tầng nó cần)
	$(COMPOSE) up --build -d --wait $(s)
	@$(COMPOSE) ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'

ide: ## Chạy mọi thứ trong container TRỪ s=<service> – bạn chạy service đó trong IntelliJ (profile local)
	@test -n "$(s)" || { echo "Cần s=<service>, vd: make ide s=catalog-service"; exit 1; }
	$(if $(call jar_tasks,$(filter-out $(s),$(APPS))),./gradlew $(call jar_tasks,$(filter-out $(s),$(APPS))))
	$(COMPOSE) rm -sf $(s)
	$(call ide_env,$(s)) $(COMPOSE) up --build -d --wait $(filter-out $(s),$(ALL))
	@$(COMPOSE) ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'
	@echo ""
	@echo "→ Mở IntelliJ, chạy (Debug) $(s) với biến môi trường SPRING_PROFILES_ACTIVE=local"
	@$(foreach a,$(s),echo "   $(a): http://localhost:$(PORT_$(a))";)
	@echo "→ Xong thì 'make up' để đưa service về lại container."

down: ## Tắt toàn bộ container (giữ dữ liệu DB)
	$(COMPOSE) down

restart: up ## Build lại và khởi động lại (sau khi sửa code)

logs: ## Xem log (tất cả, hoặc s=<service>)
	$(COMPOSE) logs -f --tail=100 $(s)

ps: ## Trạng thái container
	$(COMPOSE) ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'

clean: ## Tắt container VÀ xoá dữ liệu DB (volume) – Postgres sẽ khởi tạo lại từ đầu
	$(COMPOSE) down -v
