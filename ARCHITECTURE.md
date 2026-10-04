# E-Commerce Microservices — Architecture & API Reference

See [docs/architecture-diagram.svg](docs/architecture-diagram.svg) for the system diagram and
[docs/request-flow-diagram.svg](docs/request-flow-diagram.svg) for the checkout saga's request
flow (open either in a browser or image viewer).

Local-only, full-stack e-commerce system. Java 21 + Spring Boot 3.4.x microservices, a JavaScript/JSX
React (Vite) frontend, PostgreSQL, Kafka (business events), RabbitMQ (Spring Cloud Bus config refresh
only), Eureka, Spring Cloud Config, Spring Cloud Gateway, Spring Security + JWT, Resilience4j, Docker
Compose. No Keycloak, no coupons, no real payment provider, no Kubernetes, no cloud deployment.

## 1. Services

| Service | Port | Responsibility | DB |
|---|---|---|---|
| `eureka` | 8761 | Service discovery | — |
| `configserver` | 8888 | Centralized config (native file provider, `configserver/src/main/resources/config/*.yml`) | — |
| `gateway` | 8080 | Single entry point: routing, JWT validation, rate limiting, circuit breaking, CORS, correlation IDs | — |
| `user` | 8082 | Auth (register/login/refresh/change-password), profiles, multiple addresses + default address, admin user management | `userdb` |
| `product` | 8081 | Product catalog CRUD, categories/brands, search/filter/sort/pagination, reviews (incl. verified-purchase), rule-based recommendations, wishlist, recently-viewed, stock-alert subscriptions | `productdb` |
| `order` | 8083 | Cart, checkout (server-side price/stock re-validation, tax, shipping), order lifecycle, cancellation, returns, order saga orchestration | `orderdb` |
| `inventory` | 8087 | Stock reservation/release/reduction saga participant, admin stock management, audit log, low-stock & back-in-stock | `inventorydb` |
| `payment` | 8086 | Mocked payment processing, idempotent charges, refunds | `paymentdb` |
| `notification` | 8084 | In-app notifications (read/unread) + console "email" abstraction, saga event fan-out | `notificationdb` |
| `frontend` | 3000 | React (JS/JSX, Vite) storefront + admin dashboard | — |

Infra: PostgreSQL `5432`, Redis `6379` (gateway rate limiter), Kafka `9092` (+ Zookeeper `2181`), Kafka UI
`8089`, RabbitMQ `5672` (management UI `15672`), pgAdmin `5050`. Optional observability stack
(Zipkin `9411`, Prometheus `9090`, Grafana `3001`, Loki/Alloy/Minio) was pre-existing and left in place;
Grafana's host port was moved from 3000 to 3001 because the frontend now owns 3000.

Every service registers with Eureka and pulls its config from the Config Server
(`spring.config.import: optional:configserver:http://<host>:8888`). Each owns its own database —
no service reaches into another service's schema; cross-service reads go through REST
(`RestClient` HTTP interfaces, load-balanced via Eureka) or Kafka events.

## 2. Auth model

Single custom JWT scheme (HS256, `io.jsonwebtoken`/jjwt), issued by `user-service`. The signing key is
the `jwt.secret` property (env `JWT_SECRET`), shared by every service and the gateway so each can
validate tokens independently — not just the gateway. Claims: `sub`=userId, `email`, `role`
(`USER`/`ADMIN`), `roles`=[`ROLE_<role>`].

- The gateway validates the bearer token on every non-public route, enforces admin-only path rules,
  and forwards the verified identity downstream as `X-User-Id` / `X-User-Role` headers (replacing,
  never trusting, any client-supplied value with the same name).
- Each backend service also runs its own `JwtAuthenticationFilter` + `SecurityConfig`, so direct
  calls to a service (bypassing the gateway, e.g. during local testing) are still authorized —
  "backend authorization is mandatory" is enforced at every tier, not just the edge.
- Public/no-auth paths: `/api/v1/auth/**`, product browsing (`GET /api/products/**`), single-item
  inventory lookup (`GET /api/inventory/{id}`), `order-service`'s `verify-purchase` endpoint (trusted
  inter-service call), and `notification-service`'s `/internal` endpoint (trusted inter-service call).
- Known limitation: inter-service trust for a handful of endpoints (e.g. `GET /api/users/{id}`,
  `GET /api/products/{id}`) is still header/convention-based rather than mTLS/service-mesh-verified,
  consistent with this being a local, non-mesh deployment.

Default bootstrapped accounts (seeded by `user-service` on first run): `admin@ecom.com` / `Admin@123`
(ADMIN), `user@ecom.com` / `User@123` (USER).

## 3. Order saga (event-driven, Kafka + Spring Cloud Stream + StreamBridge)

```
checkout                 OrderCreated (order-created)
   │                             │
   ▼                             ▼
Order [PENDING]          inventory-service reserves stock
                                 │
                 ┌───────────────┴───────────────┐
                 ▼ success                        ▼ failure
     InventoryReserved (inventory-reserved)   InventoryFailed (inventory-failed)
                 │                                 │
                 ▼                                 ▼
      Order → PROCESSING                  Order → CANCELLED (inventory failure)
      payment-service attempts a                   (order-service consumer)
      mock charge
                 │
     ┌───────────┴────────────┐
     ▼ success                 ▼ failure
PaymentCompleted         PaymentFailed (payment-failed)
(payment-completed)              │
     │                  ┌────────┴─────────┐
     ▼                  ▼                   ▼
Order → CONFIRMED   inventory releases   Order → CANCELLED
(order-service)     the reservation     (order-service consumer)
inventory PERMANENTLY (inventory-service consumer)
reduces totalStock
(inventory-service consumer)
```

Compensation / cancellation:
- **Payment failure → release inventory, cancel order**: `payment-service` publishes
  `payment-failed`; both `inventory-service` (releases the reservation) and `order-service`
  (marks the order `CANCELLED`) consume it independently.
- **Inventory failure → cancel order**: `inventory-service` publishes `inventory-failed`;
  `order-service` consumes it and marks the order `CANCELLED`.
- **User/admin cancels before payment completes**: `order-service` publishes `order-cancelled`;
  `inventory-service` releases any reservation, `payment-service` refunds if a payment had
  already completed.
- **Post-delivery return**: handled synchronously, not via Kafka — the admin's decision
  (`PUT /api/orders/returns/admin/{id}/decision`) calls `payment-service`'s
  `POST /api/v1/payments/order/{orderId}/refund` directly (forwarding the admin's bearer token),
  which publishes `refund-completed` for `notification-service`.

Idempotency: every consumer dedupes by `eventId` against a local `processed_events` table before
acting (pattern: check-exists → process → record). `payment-service`'s `Payment.orderId` is also
unique at the DB level, so a redelivered `inventory-reserved` event can never double-charge.
`inventory-service` additionally tracks its own saga state (`OrderReservation` /
`OrderReservationItem`) because the compensating events (`order-cancelled`, `payment-failed`) carry
only the `orderId`, not the line items — it looks up what to release itself.

## 4. Kafka topics / Spring Cloud Stream bindings

All business events use the `kafka` binder; `springCloudBusInput`/`springCloudBusOutput`
(Spring Cloud Bus, for `/actuator/busrefresh`) always use the `rabbit` binder. Every service that has
both binders on its classpath sets `spring.cloud.stream.default-binder: rabbit` so the Bus's
auto-created output binding never hits Spring Cloud Stream's "multiple binders, ambiguous" error.
Events are plain DTOs (never JPA entities), each carrying its own `eventId` (UUID) for idempotency.

| Topic | Producer (binding) | Consumers (binding / group) |
|---|---|---|
| `order-created` | order (`orderCreated-out-0`) | inventory (`orderCreated-in-0` / `inventory-service-group`), notification (`orderCreated-in-0` / `notification-order-created-group`) |
| `order-cancelled` | order (`orderCancelled-out-0`) | inventory (`orderCancelled-in-0` / `inventory-cancel-group`), payment (`orderCancelled-in-0` / `payment-cancel-group`), notification (`orderCancelled-in-0` / `notification-order-cancelled-group`) |
| `order-shipped` | order (`orderShipped-out-0`) | notification (`orderShipped-in-0` / `notification-order-shipped-group`) |
| `order-delivered` | order (`orderDelivered-out-0`) | notification (`orderDelivered-in-0` / `notification-order-delivered-group`) |
| `inventory-reserved` | inventory (`inventoryReserved-out-0`) | order (`inventoryReserved-in-0` / `order-inventory-reserved-group`), payment (`inventoryReserved-in-0` / `payment-service-group`) |
| `inventory-failed` | inventory (`inventoryFailed-out-0`) | order (`inventoryFailed-in-0` / `order-inventory-failed-group`) |
| `inventory-released` | inventory (`inventoryReleased-out-0`) | *(none — published for audit/future use)* |
| `product-back-in-stock` | inventory (`productBackInStock-out-0`) | notification (`productBackInStock-in-0` / `notification-backinstock-group`), product (`productBackInStock-in-0` / `product-service-stock-alert-group`, fans out per-user alerts via a direct call to notification-service) |
| `payment-completed` | payment (`paymentCompleted-out-0`) | order (`paymentCompleted-in-0` / `order-payment-completed-group`), inventory (`paymentCompleted-in-0` / `inventory-payment-completed-group`, permanently deducts stock), notification (`paymentCompleted-in-0` / `notification-payment-completed-group`) |
| `payment-failed` | payment (`paymentFailed-out-0`) | order (`paymentFailed-in-0` / `order-payment-failed-group`), inventory (`paymentFailed-in-0` / `inventory-payment-failed-group`), notification (`paymentFailed-in-0` / `notification-payment-failed-group`) |
| `refund-completed` | payment (`refundCompleted-out-0`) | notification (`refundCompleted-in-0` / `notification-refund-completed-group`) |

Kafka bootstrap servers: `localhost:9092` (host) / `kafka:9092` (containers). Topics are
auto-created (`auto-create-topics: true`) — no manual topic provisioning needed locally.

## 5. RabbitMQ usage

RabbitMQ is used **only** for Spring Cloud Bus, to support `POST /actuator/busrefresh` (broadcast a
config refresh to every instance without restarting them). It is not used for any business messaging
— that's Kafka's job. Binding name: `springCloudBusInput` (and the Bus-managed
`springCloudBusOutput`), binder `rabbit`, on every service. Host: `localhost:5672` /
`rabbitmq:5672` in containers; management UI at `http://localhost:15672` (guest/guest by default).

## 6. Databases

Single PostgreSQL instance (`postgres:14`), six databases created on first boot by
`deploy/docker/init-multi-db.sql`: `userdb`, `productdb`, `orderdb`, `inventorydb`, `paymentdb`,
`notificationdb`. Each service uses `hibernate.ddl-auto: update` — tables are created/evolved
automatically, no manual migration step for local dev.

## 7. REST API reference

Base URL for everything below (through the gateway): `http://localhost:8080`. Most resources are
exposed under both `/api/v1/<resource>` and `/api/<resource>` (kept for backward compatibility with
the original scaffold); only one form is shown per line below. `🔒` = requires a valid JWT,
`🔒ADMIN` = requires `ROLE_ADMIN`, no lock = public.

### Auth & Users — `user-service`
| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/auth/register` | `{firstName,lastName,email,password,phone,role?}` → tokens + user |
| POST | `/api/v1/auth/login` | `{email,password}` → tokens + user |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` → new token pair |
| 🔒 POST | `/api/v1/auth/change-password` | `{currentPassword,newPassword}` |
| 🔒 GET | `/api/v1/auth/me` | current user profile |
| 🔒ADMIN GET | `/api/users?search=&page=&size=` | paged list |
| GET | `/api/users/{id}` | public (trusted inter-service lookup) |
| 🔒ADMIN POST | `/api/users` | admin-create a user |
| 🔒(self/ADMIN) PUT | `/api/users/{id}` | update profile |
| 🔒ADMIN PUT | `/api/users/{id}/status?active=` | activate/deactivate |
| 🔒 GET | `/api/users/{id}/addresses` | list addresses |
| 🔒(self/ADMIN) POST/PUT/DELETE | `/api/users/{id}/addresses[/{addressId}]` | manage addresses |
| 🔒(self/ADMIN) PUT | `/api/users/{id}/addresses/{addressId}/default` | set default address |

### Products, reviews, wishlist, recently-viewed, stock alerts — `product-service`
| Method | Path | Notes |
|---|---|---|
| GET | `/api/products?keyword=&category=&brand=&minPrice=&maxPrice=&minRating=&inStock=&page=&size=&sort=` | paged/filtered/sorted catalog |
| GET | `/api/products/all` \| `/featured` \| `/categories` \| `/brands` | |
| GET | `/api/products/{id}` \| `/{id}/recommendations` | |
| GET | `/api/products/{id}/reviews` \| `/{id}/reviews/distribution` | |
| 🔒 POST | `/api/products/{id}/reviews` | `{rating,comment}` — tags `verifiedPurchase` via a live check against order-service |
| 🔒ADMIN POST/PUT/DELETE | `/api/products[/{id}]` | create/update/soft-delete |
| 🔒ADMIN PUT | `/api/products/{id}/status?active=` | |
| 🔒 GET/POST/DELETE | `/api/wishlist[/{productId}[/exists]]` | |
| 🔒 GET/POST | `/api/recently-viewed[/{productId}]` | |
| 🔒 GET/POST/DELETE | `/api/stock-alerts/{productId}[/subscribed]` | "notify me when back in stock" |

### Cart, checkout, orders, returns — `order-service`
| Method | Path | Notes |
|---|---|---|
| 🔒 GET/POST | `/api/cart` | |
| 🔒 PUT/DELETE | `/api/cart/items/{productId}` | set quantity / remove |
| 🔒 POST | `/api/orders/checkout` | `{addressId?}` — re-validates price/stock, computes tax+shipping, publishes `OrderCreated` |
| 🔒 GET | `/api/orders` \| `/api/orders/{id}` | own orders |
| 🔒 POST | `/api/orders/{id}/cancel` | only while PENDING/PROCESSING/CONFIRMED |
| 🔒 POST | `/api/orders/{id}/return` | only while DELIVERED |
| 🔒 GET | `/api/orders/returns/my` | own return requests |
| GET | `/api/orders/verify-purchase?userId=&productId=` | public, trusted inter-service call |
| 🔒ADMIN GET | `/api/orders/admin/all?status=` | |
| 🔒ADMIN PUT | `/api/orders/admin/{id}/status` | `{status: SHIPPED|DELIVERED}` |
| 🔒ADMIN GET | `/api/orders/returns/admin/all` | |
| 🔒ADMIN PUT | `/api/orders/returns/admin/{id}/decision` | `{approve,note}` — approval calls payment-service to refund |

### Inventory — `inventory-service`
| Method | Path | Notes |
|---|---|---|
| GET | `/api/inventory/{productId}` | public stock snapshot |
| 🔒ADMIN GET | `/api/inventory` \| `/low-stock` \| `/{productId}/audit-logs` | |
| 🔒ADMIN POST | `/api/inventory` | seed/adjust `{productId,productName,totalStock,lowStockThreshold}` |
| 🔒ADMIN POST | `/api/inventory/{productId}/restock` | `{quantity,reference?}` |

### Payments — `payment-service`
| Method | Path | Notes |
|---|---|---|
| 🔒(owner/ADMIN) GET | `/api/payments/order/{orderId}` | |
| 🔒ADMIN GET | `/api/payments` | paged |
| 🔒ADMIN POST | `/api/payments/order/{orderId}/refund` | `{reason?}`, idempotent |

### Notifications — `notification-service`
| Method | Path | Notes |
|---|---|---|
| POST | `/api/notifications/internal` | public, trusted inter-service call — `{userId,type,title,message,referenceId}` |
| 🔒 GET | `/api/notifications?unreadOnly=&page=&size=` \| `/unread-count` | |
| 🔒 PUT | `/api/notifications/{id}/read` \| `/read-all` | |

Every service also exposes `/actuator/**` (health/info/metrics/prometheus/busrefresh) and
Swagger UI at `/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`) directly on its own port, and the
gateway exposes `/actuator/**` + `/actuator/gateway` on 8080.

## 8. Known issues / limitations

- **Product stock vs. inventory stock are two separate numbers.** `product-service.stockQuantity`
  is the catalog's own display/quick-check field (used by cart add/checkout for a fast sanity check);
  `inventory-service` is the saga's source of truth for reservation. They are not automatically kept
  in sync — an admin must seed `inventory-service` (`POST /api/inventory`) for a product after
  creating it in `product-service`. Unifying them would require a new `productCreated`-style event,
  which was intentionally not added (out of scope / avoids inventing new topics).
- **`inventory-released` and the cancel-before-payment race** are documented but unresolved at the
  messaging level: if `order-cancelled` is delivered before `inventory-reserved`, the cancel is a
  no-op (nothing to release yet) and a later successful charge will not be automatically refunded —
  this needs an explicit reconciliation job or ordering guarantee beyond what Kafka alone provides.
- **No cart quantity floor validation beyond stock** — `PUT /api/cart/items/{productId}` re-checks
  live stock but relies on the client to send a sane quantity.
- **Address/user read endpoints** (`GET /api/users/{id}`, `GET /api/users/{id}/addresses`) are
  authenticated but not ownership-restricted (any logged-in user can read, not write, another user's
  address list) — acceptable for a local/dev system, flagged for a real deployment.
- **Admin "Reports" page is client-side aggregation**, not a dedicated backend reporting endpoint —
  it composes existing list endpoints and sums in the browser; fine for small local datasets, not for
  scale.
- **`GET /api/products` (and `/all`, `/{id}`) only ever return active products** —
  `ProductService` filters `active = true` at the query level. The admin UI works around this by
  patching its own in-memory row instead of reloading the list after a deactivate/delete, and offers
  a manual "reactivate by ID" action, since there's no "list inactive/all products" endpoint to
  re-fetch from.
- **No automated tests were added**; the review/compile verification for this change was `mvnw
  compile` per module plus manual code review (see Testing section in the final summary).
