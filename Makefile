.PHONY: build test run docker-up docker-down docker-logs clean demo

# Java & Maven settings
JAVA_CMD ?= java
MVN ?= mvn

build:
	$(MVN) clean package -DskipTests

test:
	$(MVN) clean test

verify:
	$(MVN) clean verify

run:
	$(MVN) spring-boot:run

docker-up:
	docker compose up -d postgres redis

docker-up-all:
	docker compose --profile docker up -d --build

docker-down:
	docker compose down -v

docker-logs:
	docker compose logs -f

clean:
	$(MVN) clean

demo:
	bash scripts/demo.sh
