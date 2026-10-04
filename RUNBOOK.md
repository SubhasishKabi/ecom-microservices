# Running the project locally

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full service/port/API/Kafka reference. This file is
just "how do I start it".

## Prerequisites

- Docker + Docker Compose (recommended path — builds and runs everything)
- OR, for running services individually without Docker: JDK 21, and PostgreSQL / Kafka+Zookeeper /
  RabbitMQ / Redis reachable at `localhost` on their default ports, and Node.js 18+ for the frontend.

## 1. Configure environment

```bash
cp .env.example .env
```

The defaults in `.env.example` work out of the box for local Docker Compose — you only need to edit
it if you want different credentials/ports.

## 2. Start everything with Docker Compose

```bash
cd deploy/docker
docker compose --env-file ../../.env up -d --build
```

Startup order that actually matters (Docker Compose's `depends_on` here only waits for the container
to start, not for the app inside it to be ready — Eureka/Postgres/Kafka/RabbitMQ need a few seconds
after their container reports "started" before they're actually usable):

1. `postgres`, `redis`, `zookeeper`, `rabbitmq` (infra)
2. `kafka` (needs zookeeper)
3. `config-server` (needs rabbitmq, for Spring Cloud Bus)
4. `eureka`
5. `gateway-service`, `user-service`, `product-service`, `order-service`, `inventory-service`,
   `payment-service`, `notification-service` (all need config-server + eureka + their DB +
   kafka/rabbitmq)
6. `frontend` (needs gateway-service)

If a service fails to start on the first `up`, it's almost always because it started before
Postgres/Kafka/config-server/Eureka were actually ready — `docker compose restart <service>` once
the infra containers are healthy (`docker compose ps`) resolves it. Services are configured with
`restart: on-failure` for this reason.

Check everything registered: open `http://localhost:8761` (Eureka dashboard) — you should see
`GATEWAY-SERVICE`, `USER-SERVICE`, `PRODUCT-SERVICE`, `ORDER-SERVICE`, `INVENTORY-SERVICE`,
`PAYMENT-SERVICE`, `NOTIFICATION-SERVICE` all `UP`.

## 3. URLs

| What | URL |
|---|---|
| Frontend | http://localhost:3000 |
| API Gateway | http://localhost:8080 |
| Eureka dashboard | http://localhost:8761 |
| Config Server | http://localhost:8888 |
| Swagger UI (per service, direct) | http://localhost:8081/swagger-ui.html (product), 8082 (user), 8083 (order), 8086 (payment), 8087 (inventory) — notification has no browsable UI endpoints beyond `/internal`, still documented at `/v3/api-docs` |
| Kafka UI | http://localhost:8089 |
| RabbitMQ management | http://localhost:15672 (guest/guest) |
| pgAdmin | http://localhost:5050 |
| Grafana | http://localhost:3001 |

## 4. Smoke test

```bash
# Register (or just log in with the seeded demo accounts below)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com","password":"Password1","phone":"555-0100"}'

# Log in as the seeded admin
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ecom.com","password":"Admin@123"}'
```

Seeded accounts (created by `user-service` on first boot, when its DB is empty):
`admin@ecom.com` / `Admin@123` (ADMIN), `user@ecom.com` / `User@123` (USER).

`product-service` similarly seeds a demo catalog (8 products across Electronics/Fashion/Home &
Kitchen/Sports) on first boot when `productdb` is empty.

**Important**: `inventory-service` does NOT get seeded automatically. Stock reservation at checkout
will always fail ("Unknown product") until an admin seeds inventory rows for the demo products:

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ecom.com","password":"Admin@123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

for id in 1 2 3 4 5 6 7 8; do
  curl -s -X POST http://localhost:8080/api/v1/inventory \
    -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"productId\": $id, \"productName\": \"seed\", \"totalStock\": 100, \"lowStockThreshold\": 10}"
done
```

(Product IDs 1-8 assume a fresh `productdb` where the seed data was inserted in order — check
`GET /api/products/all` if in doubt.)

Then exercise the full saga: add something to the cart, `POST /api/orders/checkout`, and watch
`order-service`/`inventory-service`/`payment-service`/`notification-service` logs — you should see
the order go `PENDING → PROCESSING → CONFIRMED` (or `CANCELLED` ~10% of the time, since the mock
payment gateway randomly declines ~10% of charges by design — see `payment.mock.success-rate` in
`payment/src/main/resources/application.yml`).

## 5. Running without Docker (manual)

Each service is an independent Maven project with its own wrapper. From the repo root:

```powershell
cd configserver; .\mvnw.cmd spring-boot:run   # wait for it to report started, then in new terminals:
cd eureka;       .\mvnw.cmd spring-boot:run
cd user;         .\mvnw.cmd spring-boot:run
cd product;      .\mvnw.cmd spring-boot:run
cd order;        .\mvnw.cmd spring-boot:run
cd inventory;    .\mvnw.cmd spring-boot:run
cd payment;      .\mvnw.cmd spring-boot:run
cd notification; .\mvnw.cmd spring-boot:run
cd gateway;      .\mvnw.cmd spring-boot:run
```

You need Postgres/Kafka+Zookeeper/RabbitMQ/Redis already running locally (e.g. `docker compose up
postgres kafka zookeeper rabbitmq redis -d` from `deploy/docker/`, or your own install) — only the
Java services are run manually here, using each service's `application.yml` defaults
(`localhost` everywhere).

Frontend:
```bash
cd frontend
npm install
npm run dev   # http://localhost:3000, proxies API calls to http://localhost:8080
```

## 6. Verification performed while building this

- All 9 backend Maven modules (`configserver`, `eureka`, `gateway`, `user`, `product`, `order`,
  `inventory`, `payment`, `notification`) compile cleanly (`mvnw compile`) in this environment.
- This environment has no Docker and no Node.js/npm installed, so `docker compose up` and the
  frontend build/dev-server were **not** actually executed or observed running here — only
  compiled (backend) or carefully hand-reviewed against the real controller/DTO contracts
  (frontend). Treat the saga/runtime behavior as code-reviewed and internally consistent, not as
  integration-tested. Run the smoke test above in an environment with Docker to get real runtime
  verification.
