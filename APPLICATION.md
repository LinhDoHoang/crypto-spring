# Hướng dẫn hiện thực backend Crypto Trading Platform bằng Spring Boot

Tài liệu này là đặc tả kỹ thuật để viết lại toàn bộ backend Node.js hiện tại bằng Spring Boot nhưng vẫn giữ frontend React/Vite đang có.

Mục tiêu chính:

- Giữ tương thích với các REST API mà frontend đang gọi.
- Giữ WebSocket realtime tại đường dẫn `/ws/`.
- Sử dụng PostgreSQL/TimescaleDB làm nguồn dữ liệu bền vững.
- Sử dụng Redis cho cache giá mới nhất, Pub/Sub và hỗ trợ chạy nhiều backend instance.
- Đảm bảo thao tác mở lệnh, đóng lệnh, chốt lời, cắt lỗ và thanh lý có transaction và tính idempotent.
- Tách code theo module nghiệp vụ để sau này có thể tách microservice nếu cần.

> Phạm vi MVP hiện tại là tài khoản giao dịch demo, market order, vị thế isolated margin, leverage từ 1x đến 100x, TP/SL, liquidation, dữ liệu giá BTCUSDT/ETHUSDT/SOLUSDT và candle history.

---

## 1. Các quyết định kiến trúc cần thống nhất trước khi code

### 1.1. Kiến trúc triển khai

Nên bắt đầu bằng một Spring Boot modular monolith:

```text
React/Vite Client
       |
       | REST /api/v1/**
       | WebSocket /ws/
       v
Spring Boot Server
       |
       +-- Auth module
       +-- Account module
       +-- Trading/Order module
       +-- Risk module
       +-- Market-data module
       +-- Realtime WebSocket module
       |
       +--> PostgreSQL + TimescaleDB
       +--> Redis
       +--> Binance Futures WebSocket
```

Một application Spring Boot có thể thay thế cả ba service Node hiện tại:

- `httpserver`: REST API, authentication, account và trading.
- `wsserver`: WebSocket broadcast.
- `poller`: kết nối Binance, lưu trade tick và publish giá.

Không cần Kafka, RabbitMQ, Spring Cloud, Eureka hay API Gateway trong giai đoạn đầu.

### 1.2. Nguồn dữ liệu chính

- PostgreSQL là nguồn dữ liệu chính cho user, account, order/position và balance ledger.
- TimescaleDB là nguồn dữ liệu lịch sử cho trade tick và candle.
- Redis không phải nguồn dữ liệu chính cho balance hoặc order.
- Redis chỉ giữ giá mới nhất, timestamp giá, distributed lock ngắn hạn và Pub/Sub.
- Server price là giá thực thi duy nhất. `clientMark` chỉ dùng để kiểm tra slippage, tuyệt đối không dùng làm giá thực thi.

### 1.3. Mô hình margin

MVP nên dùng isolated margin theo từng position:

- Mỗi lệnh mở tạo một position độc lập.
- Initial margin được cố định tại thời điểm mở.
- Lỗ tối đa của position bị giới hạn theo quy tắc liquidation của tài khoản demo.
- Balance không bị trừ khi mở lệnh; margin được xem là khoản đang bị khóa.
- Khi đóng position, realized PnL mới được cộng/trừ vào balance.

### 1.4. Quy ước tiền và số

- Trong Java luôn dùng `BigDecimal` cho tiền, giá, quantity, margin và PnL.
- Không dùng `double` hoặc `float` cho phép tính tài chính.
- Database dùng `NUMERIC` với precision đủ lớn.
- Thời gian trong database dùng `TIMESTAMPTZ`.
- REST API dùng ISO-8601 UTC cho datetime, ví dụ `2026-07-15T10:15:30.123Z`.
- Trade WebSocket dùng Unix milliseconds để frontend xử lý trực tiếp.
- Candle timestamp dùng Unix seconds để tương thích chart hiện tại.

---

## 2. Công nghệ và dependency

### 2.1. Stack

- Java 21 LTS.
- Spring Boot phiên bản ổn định tương thích Java 21.
- Maven.
- Spring MVC.
- Spring Security.
- Spring WebSocket raw protocol, không bắt buộc STOMP.
- Spring Data JPA cho user/account/order/ledger.
- `JdbcClient` hoặc `JdbcTemplate` cho trade tick và truy vấn TimescaleDB.
- PostgreSQL 16 + TimescaleDB.
- Redis 7 với Lettuce client.
- Flyway.
- Docker Compose.
- JUnit 5 và Testcontainers.

### 2.2. Dependency Maven bắt buộc

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Spring Security đã cung cấp BCrypt, `JwtEncoder` và `JwtDecoder`. Không cần thêm `jjwt` nếu sử dụng JWT support chính thức của Spring Security.

Java 21 có sẵn `java.net.http.WebSocket`, vì vậy Binance client không bắt buộc thêm thư viện WebSocket khác.

### 2.3. Dependency tùy chọn

- `springdoc-openapi-starter-webmvc-ui`: OpenAPI và Swagger UI.
- `micrometer-registry-prometheus`: xuất Prometheus metrics.
- MapStruct: mapping entity/DTO nếu số lượng DTO lớn.
- Resilience4j: retry/circuit breaker cho REST integration; Binance WebSocket vẫn cần reconnect loop riêng.

Không bắt buộc dùng Lombok. Java record phù hợp cho request/response DTO.

---

## 3. Cấu trúc source code

```text
src/main/java/com/cryptotrading/server
├── CryptoTradingApplication.java
├── auth
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── JwtService.java
│   ├── PasswordService.java
│   └── dto
├── security
│   ├── SecurityConfig.java
│   ├── CurrentUser.java
│   └── CurrentUserService.java
├── user
│   ├── UserEntity.java
│   └── UserRepository.java
├── account
│   ├── TradingAccountEntity.java
│   ├── AccountLedgerEntity.java
│   ├── AccountController.java
│   ├── AccountService.java
│   └── dto
├── order
│   ├── OrderEntity.java
│   ├── OrderRepository.java
│   ├── OrderController.java
│   ├── OrderService.java
│   ├── OrderCloseService.java
│   └── dto
├── risk
│   ├── AccountSnapshotService.java
│   ├── MarginCalculator.java
│   ├── LiquidationService.java
│   └── SlTpService.java
├── marketdata
│   ├── BinanceMarketDataClient.java
│   ├── MarketPriceService.java
│   ├── TradeTickRepository.java
│   ├── TradeBatchWriter.java
│   ├── CandleController.java
│   └── dto
├── realtime
│   ├── WebSocketConfig.java
│   ├── MarketWebSocketHandler.java
│   ├── WebSocketSessionRegistry.java
│   └── RedisMarketSubscriber.java
├── config
│   ├── RedisConfig.java
│   ├── JacksonConfig.java
│   └── TradingProperties.java
└── shared
    ├── ApiError.java
    ├── GlobalExceptionHandler.java
    ├── BusinessException.java
    └── ClockProvider.java
```

Nguyên tắc dependency giữa module:

- Controller chỉ nhận request, gọi service và trả DTO.
- Service chứa business logic và transaction.
- Repository không chứa business rule.
- Risk module không được gọi controller.
- Market-data module cung cấp giá qua `MarketPriceService`.
- Order module gọi Risk và MarketPrice, không đọc Redis trực tiếp.

---

## 4. Database entities và schema

### 4.1. Danh sách entity/table

Các bảng bắt buộc cho backend ổn định:

1. `users`: thông tin đăng nhập.
2. `trading_accounts`: balance và cấu hình tài khoản demo.
3. `orders`: market order đã khớp, đồng thời đại diện cho open/closed position trong MVP.
4. `account_ledger`: lịch sử mọi thay đổi balance.
5. `trades`: trade tick từ Binance, là TimescaleDB hypertable.

Các bảng nên bổ sung khi hoàn thiện authentication và event reliability:

6. `refresh_tokens`: nếu triển khai access token ngắn hạn.
7. `outbox_events`: đảm bảo event order/alert không bị mất sau khi transaction commit.

### 4.2. Enum dùng trong Java

```java
public enum AccountType {
    DEMO
}

public enum AccountStatus {
    ACTIVE, LOCKED, CLOSED
}

public enum OrderSide {
    BUY, SELL
}

public enum OrderType {
    MARKET
}

public enum SizingMode {
    UNITS, NOTIONAL
}

public enum OrderStatus {
    OPEN, CLOSED, LIQUIDATED
}

public enum CloseReason {
    MANUAL, TAKE_PROFIT, STOP_LOSS, LIQUIDATION
}

public enum LedgerType {
    INITIAL_DEPOSIT,
    REALIZED_PNL,
    TRADING_FEE,
    LIQUIDATION,
    MANUAL_ADJUSTMENT,
    DEMO_RESET
}
```

Lưu enum dưới dạng text trong database. Không dùng ordinal vì thay đổi thứ tự enum sẽ phá dữ liệu.

### 4.3. UserEntity

Table: `users`

| Column | PostgreSQL type | Null | Ý nghĩa |
|---|---|---:|---|
| `id` | `BIGSERIAL` | No | Primary key, trả về frontend dưới dạng number |
| `email` | `VARCHAR(320)` | No | Email đã lowercase/trim |
| `password_hash` | `VARCHAR(100)` | No | BCrypt password hash |
| `enabled` | `BOOLEAN` | No | Cho phép đăng nhập |
| `created_at` | `TIMESTAMPTZ` | No | Thời điểm tạo |
| `updated_at` | `TIMESTAMPTZ` | No | Thời điểm cập nhật |
| `version` | `BIGINT` | No | Optimistic locking nếu cần |

Constraint/index:

- Unique index trên `lower(email)`.
- Không lưu plain password.
- Không trả `passwordHash` qua DTO hoặc log.

Quan hệ:

- User có đúng một trading account demo trong MVP.
- User có nhiều orders.

### 4.4. TradingAccountEntity

Table: `trading_accounts`

| Column | PostgreSQL type | Null | Ý nghĩa |
|---|---|---:|---|
| `id` | `BIGSERIAL` | No | Primary key |
| `user_id` | `BIGINT` | No | FK đến `users.id` |
| `account_type` | `VARCHAR(20)` | No | `DEMO` |
| `currency` | `VARCHAR(20)` | No | `USDT` |
| `balance` | `NUMERIC(24,8)` | No | Wallet balance đã realize |
| `status` | `VARCHAR(20)` | No | ACTIVE/LOCKED/CLOSED |
| `default_leverage` | `SMALLINT` | No | Giá trị mặc định, ví dụ 1 |
| `created_at` | `TIMESTAMPTZ` | No | Thời điểm tạo |
| `updated_at` | `TIMESTAMPTZ` | No | Thời điểm cập nhật |
| `version` | `BIGINT` | No | `@Version` chống lost update |

Constraint/index:

- Unique `(user_id, account_type)`.
- `balance >= 0` cho tài khoản demo nếu hệ thống không cho negative balance.
- Leverage từ 1 đến 100.

Khi signup:

1. Insert user.
2. Insert trading account với balance `5000.00000000`.
3. Insert một ledger row loại `INITIAL_DEPOSIT`.
4. Ba thao tác phải nằm trong cùng transaction.

### 4.5. OrderEntity

Table: `orders`

Trong MVP, market order được khớp ngay lập tức nên một row `orders` cũng chính là một position. Khi hỗ trợ limit order thực sự, nên tách `orders`, `executions` và `positions`.

| Column | PostgreSQL type | Null | Ý nghĩa |
|---|---|---:|---|
| `id` | `UUID` | No | Primary key, JSON serialize thành string |
| `account_id` | `BIGINT` | No | FK đến trading account |
| `user_id` | `BIGINT` | No | FK hỗ trợ query/ownership nhanh |
| `client_order_id` | `VARCHAR(100)` | Yes | Idempotency do client gửi |
| `symbol` | `VARCHAR(30)` | No | BTCUSDT/ETHUSDT/SOLUSDT |
| `side` | `VARCHAR(10)` | No | BUY/SELL |
| `order_type` | `VARCHAR(20)` | No | MARKET |
| `sizing_mode` | `VARCHAR(20)` | No | UNITS/NOTIONAL |
| `quantity` | `NUMERIC(28,12)` | No | Số base units |
| `entry_price` | `NUMERIC(28,12)` | No | Server mark khi mở |
| `entry_mark_timestamp` | `TIMESTAMPTZ` | No | Timestamp của mark |
| `notional` | `NUMERIC(28,8)` | No | quantity * entry price |
| `leverage` | `SMALLINT` | No | 1..100 |
| `initial_margin` | `NUMERIC(28,8)` | No | notional / leverage |
| `maintenance_margin_rate` | `NUMERIC(10,8)` | No | Ví dụ 0.005 |
| `take_profit` | `NUMERIC(28,12)` | Yes | TP price |
| `stop_loss` | `NUMERIC(28,12)` | Yes | SL price |
| `status` | `VARCHAR(20)` | No | OPEN/CLOSED/LIQUIDATED |
| `close_reason` | `VARCHAR(30)` | Yes | MANUAL/TP/SL/LIQUIDATION |
| `close_price` | `NUMERIC(28,12)` | Yes | Giá đóng |
| `realized_pnl` | `NUMERIC(28,8)` | Yes | PnL đã realize |
| `trading_fee` | `NUMERIC(28,8)` | No | Có thể mặc định 0 |
| `client_mark` | `NUMERIC(28,12)` | Yes | Chỉ dùng audit/slippage |
| `client_timestamp` | `TIMESTAMPTZ` | Yes | Timestamp client click |
| `max_slippage_bps` | `INTEGER` | Yes | Slippage limit |
| `opened_at` | `TIMESTAMPTZ` | No | Thời điểm mở |
| `closed_at` | `TIMESTAMPTZ` | Yes | Thời điểm đóng |
| `created_at` | `TIMESTAMPTZ` | No | Audit |
| `updated_at` | `TIMESTAMPTZ` | No | Audit |
| `version` | `BIGINT` | No | Optimistic locking |

Constraint/index:

- Check `quantity > 0`.
- Check `entry_price > 0`.
- Check `leverage BETWEEN 1 AND 100`.
- Unique `(account_id, client_order_id)` khi `client_order_id` không null.
- Index `(account_id, status, opened_at DESC)`.
- Index `(symbol, status)` để watcher tìm open position.
- Index có điều kiện cho `status = 'OPEN'` nếu PostgreSQL migration cho phép.

### 4.6. AccountLedgerEntity

Table: `account_ledger`

Ledger giúp giải thích vì sao balance thay đổi và tránh sửa balance mà không có audit trail.

| Column | PostgreSQL type | Null | Ý nghĩa |
|---|---|---:|---|
| `id` | `BIGSERIAL` | No | Primary key |
| `account_id` | `BIGINT` | No | FK |
| `order_id` | `UUID` | Yes | Order liên quan |
| `type` | `VARCHAR(30)` | No | LedgerType |
| `amount` | `NUMERIC(28,8)` | No | Số dương cộng balance, số âm trừ |
| `balance_before` | `NUMERIC(28,8)` | No | Balance trước |
| `balance_after` | `NUMERIC(28,8)` | No | Balance sau |
| `description` | `VARCHAR(500)` | Yes | Mô tả |
| `created_at` | `TIMESTAMPTZ` | No | Thời điểm |

Mỗi lần đóng lệnh phải:

1. Lock account.
2. Cập nhật order từ OPEN sang CLOSED/LIQUIDATED.
3. Cập nhật account balance.
4. Insert ledger.
5. Commit tất cả trong một transaction.

### 4.7. Trade tick

Table: `trades`, không nhất thiết map thành JPA entity.

| Column | PostgreSQL type | Null | Ý nghĩa |
|---|---|---:|---|
| `time` | `TIMESTAMPTZ` | No | Binance trade time |
| `asset` | `TEXT` | No | Canonical symbol |
| `price` | `NUMERIC(28,12)` | No | Trade price |
| `quantity` | `NUMERIC(28,12)` | No | Trade quantity |
| `source` | `VARCHAR(20)` | No | BINANCE |
| `source_trade_id` | `BIGINT` | Yes | Binance aggregate trade id |

Khuyến nghị:

- Tạo hypertable theo `time`.
- Unique hoặc deduplication theo `(asset, source_trade_id)`.
- Index `(asset, time DESC)`.
- Batch insert 100 records hoặc flush mỗi 1 giây.
- Không gọi JPA `save()` cho từng tick.

### 4.8. RefreshTokenEntity, tùy chọn

Chỉ cần khi frontend hỗ trợ refresh token.

Các field: id, user_id, token_hash, expires_at, revoked_at, created_at, device_info.

Không lưu raw refresh token; lưu hash.

### 4.9. OutboxEventEntity, khuyến nghị

Dùng khi cần đảm bảo event không mất:

- Transaction cập nhật order đồng thời insert outbox event.
- Worker đọc outbox và publish Redis/WebSocket.
- Sau publish thành công đánh dấu `published_at`.

Các field: id UUID, aggregate_type, aggregate_id, event_type, payload JSONB, created_at, published_at, retry_count.

### 4.10. Flyway migrations

```text
src/main/resources/db/migration
├── V1__enable_timescaledb.sql
├── V2__create_users_and_accounts.sql
├── V3__create_orders.sql
├── V4__create_account_ledger.sql
├── V5__create_trades_hypertable.sql
├── V6__create_candle_aggregates.sql
└── V7__create_outbox_events.sql
```

Không dùng đồng thời Hibernate auto-create và Flyway:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

---

### 4.11. Entity Relationship Diagram (ERD)

```
┌──────────────────────┐
│       users           │
│──────────────────────│
│ id (PK, BIGSERIAL)    │
│ email (VARCHAR 320)   │
│ password_hash (V100)  │
│ enabled (BOOLEAN)     │
│ version (BIGINT)      │
│ created_at / updated_at│
│ deleted_at (soft del) │
└──────┬───────────────┘
       │
       │ 1 ───────── 1
       │ (user_id UNIQUE)
       ▼
┌──────────────────────┐
│  trading_accounts     │
│──────────────────────│
│ id (PK, BIGSERIAL)    │
│ user_id (FK→users)   │
│ account_type (ENUM)   │
│ currency (VARCHAR)    │
│ balance (NUMERIC)     │
│ status (ENUM)         │
│ default_leverage      │
│ version (BIGINT)      │
│ created_at / updated_at│
└──────┬───────────────┘
       │
       │ 1 ───────── N
       │ (account_id)
       ▼
┌──────────────────────┐         ┌──────────────────────┐
│       orders          │         │   account_ledgers    │
│──────────────────────│         │──────────────────────│
│ id (PK, BIGSERIAL)    │ 1──N   │ id (PK, BIGSERIAL)    │
│ account_id (FK)       │◄───────│ account_id (FK)       │
│ user_id (FK→users)   │         │ order_id (nullable)   │
│ client_order_id       │         │ type (ENUM)           │
│ symbol (ENUM)         │         │ amount (NUMERIC)      │
│ side (ENUM)           │         │ balance_before        │
│ order_type (ENUM)     │         │ balance_after         │
│ sizing_mode (ENUM)    │         │ description           │
│ quantity              │         │ created_at / updated_at│
│ entry_price           │         └──────────────────────┘
│ entry_mark_timestamp  │
│ notional              │
│ leverage              │
│ initial_margin        │
│ maintenance_margin_rate│
│ take_profit / stop_loss│
│ status (OPEN/CLOSED/  │
│        LIQUIDATED)    │
│ close_reason          │
│ close_price           │
│ realized_pnl          │
│ trading_fee           │
│ client_mark           │
│ opened_at / closed_at │
│ version (BIGINT)      │
└──────────────────────┘

       ┌──────────────────────┐
       │   refresh_tokens      │
       │──────────────────────│
       │ id (PK, UUID)         │
       │ user_id (FK→users)   │
       │ token_hash (UNIQUE)   │
       │ expires_at            │
       │ revoked_at            │
       │ device_name / ip / ua │
       └──────────────────────┘

       ┌──────────────────────┐
       │       trades          │
       │  (standalone table,   │
       │   no FK to entities)  │
       │──────────────────────│
       │ id (PK, BIGSERIAL)    │
       │ time (TIMESTAMPTZ)    │
       │ asset (ENUM)          │
       │ price (NUMERIC)       │
       │ quantity (NUMERIC)    │
       │ source (ENUM)         │
       │ source_trade_id       │
       └──────────────────────┘
```

### 4.12. Key Database Constraints (Current Implemented)

| Table | Constraint/Index | Purpose |
|-------|-----------------|---------|
| `users` | `PK: id` | Primary key |
| `users` | `UX: lower(email) WHERE deleted_at IS NULL` | Unique active email |
| `trading_accounts` | `PK: id` | Primary key |
| `trading_accounts` | `FK: user_id → users.id` | User ownership |
| `trading_accounts` | `UX: (user_id, account_type) WHERE deleted_at IS NULL` | One account type per user |
| `trading_accounts` | `CK: balance >= 0` | Non-negative balance |
| `trading_accounts` | `CK: default_leverage BETWEEN 1 AND 100` | Leverage range |
| `orders` | `PK: id` | Primary key |
| `orders` | `FK: user_id → users.id` | User ownership |
| `orders` | `UX: (account_id, client_order_id) WHERE client_order_id IS NOT NULL` | Idempotency |
| `orders` | `IX: (account_id, status, opened_at DESC)` | Position listing |
| `orders` | `IX: (symbol, status)` | Symbol-based queries (TP/SL/liquidation) |
| `orders` | `CK: quantity > 0` | Positive quantity |
| `orders` | `CK: entry_price > 0` | Positive entry price |
| `orders` | `CK: leverage BETWEEN 1 AND 100` | Leverage range |
| `account_ledgers` | `PK: id` | Primary key |
| `account_ledgers` | (No FK to orders/accounts in current schema) | Soft reference via `order_id`, `account_id` |
| `refresh_tokens` | `PK: id` | Primary key |
| `refresh_tokens` | `FK: user_id → users.id` | User ownership |
| `refresh_tokens` | `UX: token_hash` | Unique token hash |
| `trades` | `PK: id` | Primary key |
| `trades` | `UX: (asset, source_trade_id) WHERE source_trade_id IS NOT NULL` | Deduplication |
| `trades` | `IX: (asset, time DESC)` | Price queries |

### 4.13. JPA Entity Relationship Mapping Summary

| Parent | Child | Card. | Join Column | JPA Annotation |
|--------|-------|-------|-------------|----------------|
| `UsersEntity` | `TradingAccountsEntity` | 1:1 | `user_id` | `@OneToOne(mappedBy="user")` ↔ `@OneToOne @JoinColumn(name="user_id", insertable=false, updatable=false)` |
| `UsersEntity` | `OrdersEntity` | 1:N | `user_id` | `@OneToMany(mappedBy="user")` ↔ `@ManyToOne @JoinColumn(name="user_id", insertable=false, updatable=false)` |
| `UsersEntity` | `RefreshTokensEntity` | 1:N | `user_id` | `@OneToMany(mappedBy="user")` ↔ `@ManyToOne @JoinColumn(name="user_id", insertable=false, updatable=false)` |
| `TradingAccountsEntity` | `OrdersEntity` | 1:N | `account_id` | **No JPA relationship** — referenced via `accountId` field only |
| `OrdersEntity` | `AccountLedgersEntity` | 1:N | `order_id` | **No JPA relationship** — referenced via `orderId` field only |

> **Note:** `TradingAccountsEntity` and `OrdersEntity` both have `insertable=false, updatable=false` on their `@JoinColumn`. This means the actual FK value is managed via the `userId`/`accountId` Long field, not the entity reference. This is a common pattern to avoid double-write conflicts and keep the FK explicit.

### 4.14. `ModifiedEntity` Base Class

All entities except `RefreshTokensEntity` extend `ModifiedEntity`, which provides via `@MappedSuperclass`:

| Field | Annotation | Purpose |
|-------|-----------|---------|
| `createdAt` | `@CreatedDate` | Auto-set on insert |
| `updatedAt` | `@LastModifiedDate` | Auto-updated on every change |
| `deletedAt` | (manual) | Soft delete timestamp |
| `createdBy` | `@CreatedBy` | Audit: who created |
| `updatedBy` | (manual) | Audit: who last updated |
| `deletedBy` | (manual) | Audit: who deleted |

Plus `@SQLRestriction("deleted_at IS NULL")` — all queries automatically filter out soft-deleted rows (no need for `WHERE deleted_at IS NULL` in every query).

---

### 4.15. Business Logic Flows — Entity Interactions

#### 4.15.1. Signup Flow

```
AuthController.signup()
    │
    ▼
AuthService.signup(request, metadata)           ← @Transactional
    │
    ├─[1] Validate email + password length
    ├─[2] Check usersRepository.existsByEmailIgnoreCase(email)
    │      → 409 CONFLICT if duplicate
    ├─[3] usersRepository.save(UsersEntity)
    │      Table: INSERT INTO users (email, password_hash, enabled, version)
    │      Entity state: id generated, email set, passwordHash=BCrypt, enabled=true
    │
    ├─[4] tradingAccountRepository.save(TradingAccountsEntity)
    │      Table: INSERT INTO trading_accounts (user_id, account_type, currency, balance, status, default_leverage)
    │      Entity state: userId=user.id, accountType=DEMO, balance=5000, status=ACTIVE, defaultLeverage=1
    │
    ├─[5] accountLedgersRepository.save(AccountLedgersEntity)
    │      Table: INSERT INTO account_ledgers (account_id, type, amount, balance_before, balance_after, description)
    │      Entity state: accountId=account.id, type=INITIAL_DEPOSIT, amount=5000, balanceBefore=0, balanceAfter=5000
    │
    ├─[6] jwtService.issue(user) → access token
    ├─[7] refreshTokenService.issue(userId, metadata) → refresh token
    │      Table: INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, device_name, created_by_ip, user_agent)
    │
    └─[8] COMMIT (all 3 inserts + refresh token in one transaction)
           Return: AuthResponseDto + refresh cookie
```

**Entities involved:** `UsersEntity` → `TradingAccountsEntity` → `AccountLedgersEntity` → `RefreshTokensEntity`

---

#### 4.15.2. Signin Flow

```
AuthController.signin()
    │
    ▼
AuthService.signin(request, metadata)           ← @Transactional
    │
    ├─[1] Normalize email (trim + lowercase)
    ├─[2] authenticationManager.authenticate(UsernamePasswordAuthenticationToken)
    │      → DaoAuthenticationProvider
    │      → AuthUserDetailsService.loadUserByUsername(email)
    │      → PasswordEncoder.matches(raw, hashed)
    │      → Throws on bad credentials or disabled account
    │
    ├─[3] usersRepository.findByEmailIgnoreCase(email)
    │      → Verify user exists and is enabled
    │
    ├─[4] jwtService.issue(user) → access token
    ├─[5] refreshTokenService.issue(userId, metadata) → new refresh token
    │      → Previous refresh tokens are NOT revoked on signin (you stay logged in on other devices)
    │
    └─[6] Return: AuthResponseDto + refresh cookie
```

**Entities involved:** `UsersEntity` → `RefreshTokensEntity`

---

#### 4.15.3. Place Order Flow (Market Order)

```
OrdersController.create()
    │
    ▼
OrdersService.create(dto)                       ← @Transactional
    │
    ├─[1] Validate: symbol, side, sizing mode, quantity/notional, leverage, TP/SL
    │
    ├─[2] MarketPriceService.getPrice(symbol) — NOT in transaction
    │      → Read from Redis price:last:{SYMBOL}
    │      → Fallback: latest trade from trades table
    │      → Reject 503 if price unavailable or stale (>5s)
    │
    ├─[3] Calculate: notional = quantity * entryPrice
    │               initialMargin = notional / leverage
    │               maintenanceMarginRate (from config)
    │
    ├─[4] BEGIN TRANSACTION
    │
    ├─[5] tradingAccountRepository.findByIdForUpdate(accountId) — PESSIMISTIC_WRITE lock
    │      → Locks the trading_accounts row to prevent concurrent modifications
    │
    ├─[6] Compute account snapshot:
    │      equity = balance + sum(openOrder.unrealizedPnl)
    │      usedMargin = sum(openOrder.initialMargin)
    │      freeMargin = equity - usedMargin
    │      → Reject 400 if freeMargin < required (initialMargin + estimated fee)
    │
    ├─[7] orderRepository.save(OrdersEntity)
    │      Table: INSERT INTO orders (account_id, user_id, symbol, side, order_type, sizing_mode,
    │              quantity, entry_price, entry_mark_timestamp, notional, leverage,
    │              initial_margin, maintenance_margin_rate, take_profit, stop_loss,
    │              status=OPEN, opened_at, client_order_id, client_mark, max_slippage_bps)
    │
    ├─[8] COMMIT
    │
    └─[9] (Post-commit) Publish order:placed event
```

**Entities involved:** `UsersEntity`/`TradingAccountsEntity` (read + lock) → `OrdersEntity` (insert)

---

#### 4.15.4. Close Position Flow (Manual Close)

```
OrdersController.delete() / POST /orders/{id}/close
    │
    ▼
OrdersService.close(id)                         ← @Transactional
    │
    ├─[1] MarketPriceService.getPrice(symbol) — NOT in transaction
    │      → Reject 503 if price unavailable or stale
    │
    ├─[2] BEGIN TRANSACTION
    │
    ├─[3] tradingAccountRepository.findByIdForUpdate(accountId) — PESSIMISTIC_WRITE lock
    │
    ├─[4] orderRepository.findById(id) — read the order
    │      → Verify order.accountId matches the locked account
    │      → Verify order.status == OPEN
    │      → 404 if not found, 409 if already CLOSED/LIQUIDATED
    │
    ├─[5] Calculate PnL:
    │      BUY:  realizedPnl = (closePrice - entryPrice) * quantity - tradingFee
    │      SELL: realizedPnl = (entryPrice - closePrice) * quantity - tradingFee
    │
    ├─[6] Update order:
    │      order.status = CLOSED
    │      order.closeReason = MANUAL
    │      order.closePrice = serverMark
    │      order.realizedPnl = calculated
    │      order.closedAt = now()
    │      → JPA dirty-checking auto-updates; @Version prevents lost updates
    │
    ├─[7] Update trading account:
    │      balanceBefore = account.balance
    │      account.balance = balanceBefore + realizedPnl
    │      → JPA dirty-checking auto-updates
    │
    ├─[8] accountLedgersRepository.save(AccountLedgersEntity)
    │      Table: INSERT INTO account_ledgers (account_id, order_id, type=REALIZED_PNL,
    │              amount=realizedPnl, balance_before, balance_after)
    │
    ├─[9] COMMIT (all 3 changes in one transaction)
    │
    └─[10] (Post-commit) Publish order:closed event
```

**Entities involved:** `TradingAccountsEntity` (lock + update) → `OrdersEntity` (update status + PnL) → `AccountLedgersEntity` (insert)

---

#### 4.15.5. TP/SL Trigger Flow (Automated)

```
MarketTick received (Binance WebSocket → event)
    │
    ▼
SlTpService.checkTrigger(tick)
    │
    ├─[1] Query OPEN orders for this symbol:
    │      orderRepository.findBySymbolAndStatus(symbol, OPEN)
    │      → Uses index: ix_orders_symbol_status
    │
    ├─[2] For each OPEN order, check trigger conditions:
    │      BUY  TP: lastPrice >= order.takeProfit
    │      BUY  SL: lastPrice <= order.stopLoss
    │      SELL TP: lastPrice <= order.takeProfit
    │      SELL SL: lastPrice >= order.stopLoss
    │
    ├─[3] For each triggered order, execute close:
    │      Same flow as 4.15.4 but:
    │      - closeReason = TP or SL
    │      - Uses conditional UPDATE WHERE status='OPEN' for idempotency
    │      - Distributed lock lock:close:{orderId} to prevent race with manual close
    │
    └─[4] Publish private alert (order:closed with TP/SL reason)
```

**Entities involved:** `TradesEntity` (trigger source) → `OrdersEntity` (query + update) → `TradingAccountsEntity` (update) → `AccountLedgersEntity` (insert)

---

#### 4.15.6. Liquidation Flow (Automated)

```
MarketTick received → RiskEngine.evaluate()
    │
    ▼
LiquidationService.checkLiquidation(orders)
    │
    ├─[1] For each OPEN order, compute:
    │      positionEquity = initialMargin + unrealizedPnl
    │      currentNotional = abs(quantity * markPrice)
    │      maintenance = currentNotional * maintenanceMarginRate
    │
    │      IF positionEquity <= maintenance → LIQUIDATE
    │
    ├─[2] BEGIN TRANSACTION
    │
    ├─[3] tradingAccountRepository.findByIdForUpdate() — lock account
    ├─[4] Conditional UPDATE: SET status='LIQUIDATED', close_reason='LIQUIDATION',
    │      close_price=markPrice, realized_pnl=capped loss, closed_at=now()
    │      WHERE id = :id AND status = 'OPEN'
    │
    ├─[5] Update account balance (cap loss per demo rules)
    ├─[6] Insert ledger: type=LIQUIDATION
    ├─[7] COMMIT
    │
    └─[8] Publish private liquidation alert
```

**Entities involved:** `TradesEntity` (trigger source) → `OrdersEntity` (update) → `TradingAccountsEntity` (update) → `AccountLedgersEntity` (insert)

---

#### 4.15.7. Account Snapshot Flow (GET /account)

```
AccountController.getAccount()
    │
    ▼
AccountService.getSnapshot(userId)
    │
    ├─[1] tradingAccountRepository.findByUserId(userId)
    │      → Get balance from trading_accounts
    │
    ├─[2] orderRepository.findByAccountIdAndStatus(accountId, OPEN)
    │      → Get all open positions
    │
    ├─[3] For each open position:
    │      → MarketPriceService.getPrice(order.symbol)
    │      → Compute unrealizedPnl per position
    │
    ├─[4] Aggregate:
    │      equity = balance + sum(unrealizedPnl)
    │      usedMargin = sum(initialMargin)
    │      freeMargin = equity - usedMargin
    │      maintenanceMargin = sum(maintenance)
    │      marginLevel = equity / usedMargin * 100
    │
    └─[5] Return snapshot DTO
```

**Entities involved:** `TradingAccountsEntity` → `OrdersEntity` (open only) → `TradesEntity` (via MarketPriceService for latest price)

---

#### 4.15.8. Refresh Token Rotation Flow

```
AuthController.refresh(cookie: refresh_token)
    │
    ▼
AuthService.refresh(rawRefreshToken, metadata)
    │
    ├─[1] refreshTokenService.rotate(rawToken, metadata)
    │      → Hash incoming token
    │      → Find matching refresh_tokens row WHERE token_hash = hash AND revoked_at IS NULL AND expires_at > NOW()
    │      → REVOKE old token: SET revoked_at = NOW()
    │      → ISSUE new token: INSERT new row with new hash
    │      → Return (userId, newTokenValue)
    │
    ├─[2] usersRepository.findById(userId) — verify user still enabled
    │
    ├─[3] jwtService.issue(user) → new access token
    │
    └─[4] Return: AuthResponseDto + new refresh cookie
```

**Entities involved:** `RefreshTokensEntity` (update old → revoke, insert new) → `UsersEntity` (verify) → JWT (new access token)

---

#### 4.15.9. Summary: Entity Write Matrix

| Operation | users | trading_accounts | orders | account_ledgers | refresh_tokens | trades |
|-----------|-------|-----------------|--------|-----------------|----------------|--------|
| Signup | INSERT | INSERT | — | INSERT (INITIAL_DEPOSIT) | INSERT | — |
| Signin | READ | — | — | — | INSERT | — |
| Place Order | READ | READ (LOCK) | INSERT (OPEN) | — | — | READ (price) |
| Close Order | — | UPDATE (balance) | UPDATE (CLOSED) | INSERT (REALIZED_PNL) | — | READ (price) |
| TP/SL Trigger | — | UPDATE | UPDATE (CLOSED) | INSERT (REALIZED_PNL) | — | READ (price) |
| Liquidation | — | UPDATE (balance) | UPDATE (LIQUIDATED) | INSERT (LIQUIDATION) | — | READ (price) |
| Refresh Token | READ | — | — | — | UPDATE(revoke) + INSERT | — |
| Logout | — | — | — | — | UPDATE (revoke) | — |
| Account Snapshot | — | READ | READ (OPEN only) | — | — | READ (price) |

### 4.16. Concurrency and Locking Strategy

```
Place Order:
  Lock order: account (PESSIMISTIC_WRITE)
  
Close Order (Manual/TP/SL/Liquidation):
  Lock order: account (PESSIMISTIC_WRITE) → order (conditional UPDATE)
  Safety: conditional UPDATE WHERE status='OPEN' + @Version
  
Multiple close attempts:
  Only ONE wins the conditional update
  Losers get either "already closed" (409) or "not found" (404)
  
TP/SL vs Manual close race:
  Distributed lock: lock:close:{orderId} (Redis, TTL 3-10s)
  Database: conditional UPDATE is the final guard
```

---

### 4.17. Cách đọc và hiện thực business logic

Phần này mô tả **ý nghĩa nghiệp vụ** phía sau các entity. Khi code, không nên bắt đầu từ việc gọi `repository.save()` ngay. Hãy xác định trước:

1. Request này làm thay đổi entity nào?
2. Entity nào là nguồn sự thật (source of truth)?
3. Entity nào cần được lock?
4. Những thay đổi nào phải commit cùng nhau?
5. Event chỉ được publish sau khi database commit thành công.

#### 4.17.1. Trách nhiệm của từng layer

```text
HTTP request
    │
    ▼
Controller
    │  Parse request, validate DTO, lấy userId từ JWT
    │  Không tính PnL, không tự sửa balance, không gọi nhiều repository để điều phối
    ▼
Application Service
    │  Điều phối business flow, transaction, authorization, state transition
    │  Đây là nơi quyết định entity nào được đọc/ghi theo thứ tự nào
    ▼
Domain Calculator / Policy
    │  Tính quantity, margin, PnL, liquidation threshold, slippage
    │  Không tự commit database
    ▼
Repository
    │  Đọc/ghi entity, query theo ownership, lock row khi cần
    ▼
Database
```

| Layer | Trách nhiệm chính | Không nên làm |
|---|---|---|
| Controller | HTTP contract, DTO validation, status code | Chứa trading formula hoặc cập nhật balance |
| Auth/Order/Account Service | Use case và transaction boundary | Tin `userId`/`accountId` do client gửi |
| Calculator | Pure calculation, dễ unit test | Gọi repository hoặc Redis |
| Repository | Persistence, ownership query, pessimistic lock | Chứa business rule |
| Entity | Trạng thái và dữ liệu domain | Tự gọi service khác |
| Event publisher | Publish sau commit | Publish khi transaction chưa commit |

#### 4.17.2. Nguồn sự thật của từng loại dữ liệu

```text
trading_accounts.balance
    = wallet balance đã realize

orders.status = OPEN
    = position đang tồn tại và đang sử dụng margin

orders.entry_price + orders.quantity + orders.side
    = dữ liệu để tính PnL của position

account_ledgers
    = lịch sử giải thích vì sao balance thay đổi

trades / Redis latest price
    = dữ liệu giá thị trường, không phải balance hoặc order state
```

Ý nghĩa quan trọng:

- Không trừ `balance` khi mở position trong mô hình isolated margin hiện tại; `initial_margin` được xem là margin đang sử dụng.
- Chỉ cập nhật `balance` khi PnL được realize lúc close hoặc liquidation.
- Không dùng `clientMark` làm giá thực thi. Giá thực thi luôn là server mark.
- Không xóa order khi close. Chuyển `status` từ `OPEN` sang `CLOSED` hoặc `LIQUIDATED` để giữ lịch sử.
- Mỗi thay đổi balance phải có một dòng `account_ledgers` tương ứng.

#### 4.17.3. State transition của position

```text
                    ┌──────────────┐
                    │  POST order  │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │     OPEN     │
                    └───┬────┬─────┘
                        │    │
          manual/TP/SL  │    │  risk threshold reached
                        │    │
                        ▼    ▼
                 ┌────────┐ ┌────────────┐
                 │ CLOSED │ │ LIQUIDATED │
                 └────────┘ └────────────┘
```

Không có đường quay lại `OPEN` trong MVP. Nếu một request cố close order đã `CLOSED` hoặc `LIQUIDATED`, request đó không được tính PnL lần thứ hai.

#### 4.17.4. Signup: tạo đầy đủ aggregate ban đầu

**Ý nghĩa nghiệp vụ:** Một user mới không chỉ là một dòng trong `users`. Để user có thể trade, hệ thống phải tạo đồng thời user, demo account, initial balance ledger và session authentication.

```text
Signup request
    │
    ▼
Normalize email + validate password
    │
    ├── Email đã tồn tại? ── Yes ──> 409, dừng toàn bộ flow
    │
    ▼
INSERT users
    │  Kết quả cần có: user.id
    ▼
INSERT trading_accounts
    │  user_id = user.id
    │  balance = 5000 USDT
    │  status = ACTIVE
    ▼
INSERT account_ledgers
    │  account_id = account.id
    │  type = INITIAL_DEPOSIT
    │  before = 0, after = 5000
    ▼
Issue access JWT + persist hashed refresh token
    │
    ▼
COMMIT
    │
    ▼
Set refresh cookie + return response
```

**Transaction boundary:** Từ `INSERT users` đến khi refresh token được lưu phải nằm trong một transaction. Nếu tạo account hoặc ledger thất bại, user cũng không nên được commit riêng lẻ.

**Entity interaction:**

```text
UsersEntity.id
    └──> TradingAccountsEntity.userId
              └──> AccountLedgersEntity.accountId
UsersEntity.id
    └──> RefreshTokensEntity.userId
```

**Code cần viết:** `AuthService.signup()` là application service; `UsersRepository`, `TradingAccountRepository`, `AccountLedgersRepository` và `RefreshTokenService` chỉ hỗ trợ persistence. Controller không được tự tạo bốn entity này.

#### 4.17.5. Signin: xác thực danh tính, không tạo account mới

**Ý nghĩa nghiệp vụ:** Signin chỉ chứng minh email/password hợp lệ và tạo session mới. Không được tạo thêm trading account hoặc reset balance.

```text
Signin request
    │
    ▼
Normalize email
    │
    ▼
AuthenticationManager
    │
    ├── AuthUserDetailsService đọc UsersEntity
    ├── PasswordEncoder kiểm tra passwordHash
    └── User disabled/sai password ──> 401/403, dừng flow
    │
    ▼
Load enabled UsersEntity
    │
    ▼
Issue short-lived access JWT
    │
    ▼
Insert refresh token hash vào refresh_tokens
    │
    ▼
Return access token + HttpOnly refresh cookie
```

`TradingAccountsEntity` không bắt buộc phải đọc trong signin. Account chỉ được đọc ở các flow cần giao dịch hoặc trả account snapshot.

#### 4.17.6. Place order: biến tiền khả dụng thành một position OPEN

**Ý nghĩa nghiệp vụ:** Place order không phải chỉ là insert một order. Hệ thống phải đảm bảo server có giá đáng tin cậy, user sở hữu account, account còn đủ free margin và request không bị xử lý hai lần.

```text
POST /orders + JWT
    │
    ▼
Extract userId from JWT
    │
    ▼
Find the user's active DEMO account
    │
    ▼
Read server mark
    │
    ├── Missing/stale price ──> 503, không mở position
    ├── Client mark lệch quá giới hạn ──> 409, không mở position
    └── Giá hợp lệ
    │
    ▼
Validate symbol, side, mode, quantity, leverage, TP/SL
    │
    ▼
Calculate quantity, notional, initial margin
    │
    ▼
BEGIN TRANSACTION
    │
    ▼
Lock TradingAccountsEntity
    │  Pessimistic write lock để hai request cùng lúc không dùng chung free margin
    ▼
Check clientOrderId / Idempotency-Key
    │
    ├── Đã tồn tại ──> trả order cũ, không insert order mới
    └── Chưa tồn tại
    │
    ▼
Read OPEN OrdersEntity + calculate account snapshot
    │  equity = balance + unrealized PnL
    │  usedMargin = tổng initial margin của position OPEN
    │  freeMargin = equity - usedMargin
    │
    ├── freeMargin không đủ ──> rollback + 400
    └── đủ margin
    │
    ▼
INSERT OrdersEntity(status = OPEN)
    │
    ▼
COMMIT
    │
    ▼
Publish order:placed
```

**Không insert ledger khi chỉ mở order** nếu policy hiện tại không trừ balance lúc mở. Ledger chỉ được tạo khi balance thực sự thay đổi. Nếu sau này muốn tính opening fee và trừ ngay balance, phải thay đổi transaction flow và thêm ledger `TRADING_FEE`.

**Thông tin cần snapshot vào `OrdersEntity`:** entry price, quantity, notional, leverage, initial margin và maintenance margin rate. Không lấy lại các giá trị này từ config khi close vì policy có thể thay đổi sau thời điểm mở.

#### 4.17.7. Manual close: realize PnL đúng một lần

**Ý nghĩa nghiệp vụ:** Close là một state transition nguyên tử. Order đổi trạng thái, account nhận PnL và ledger ghi audit phải thành công hoặc thất bại cùng nhau.

```text
POST /orders/{id}/close + JWT
    │
    ▼
Extract userId from JWT
    │
    ▼
Load order by id + ownership
    │
    ├── Không tồn tại/không thuộc user ──> 404
    └── Tồn tại
    │
    ▼
Read fresh server mark BEFORE opening DB transaction
    │
    ▼
BEGIN TRANSACTION
    │
    ▼
Lock TradingAccountsEntity
    │
    ▼
Lock/check OrdersEntity
    │
    ├── status != OPEN ──> 409, rollback
    └── status = OPEN
    │
    ▼
Calculate gross PnL and fee
    │  BUY:  (close - entry) × quantity
    │  SELL: (entry - close) × quantity
    │
    ▼
Update OrdersEntity
    │  status = CLOSED
    │  closeReason = MANUAL
    │  closePrice = server mark
    │  realizedPnl = net PnL
    │  closedAt = now
    │
    ▼
Update TradingAccountsEntity.balance
    │  balanceAfter = balanceBefore + realizedPnl
    │
    ▼
INSERT AccountLedgersEntity
    │  type = REALIZED_PNL
    │  orderId = order.id
    │  before/after khớp với account balance
    │
    ▼
COMMIT
    │
    ▼
Publish order:closed
```

**Điểm phải kiểm tra:** `accountId` và `userId` của order phải được đối chiếu với JWT. Không được cho phép client gửi một `accountId` khác để đóng order của user khác.

**Lưu ý về code hiện tại:** `OrdersService` hiện mới có CRUD tổng quát. `OrdersController.delete()` đang soft-delete bằng `deletedAt`, không phải business close. Cần tạo use case riêng như `CloseOrderService.closeManual(...)` hoặc mở rộng `OrdersService` nhưng không dùng `DELETE` để thay thế logic close.

#### 4.17.8. TP/SL: market tick là trigger, close service là nơi realize

**Ý nghĩa nghiệp vụ:** TP/SL không tạo một loại transaction riêng. Nó chỉ là một nguồn kích hoạt khác gọi chung close use case, để manual close, TP và SL dùng cùng cách cập nhật order/account/ledger.

```text
Binance tick
    │
    ▼
Update latest price cache + persist trade tick
    │
    ▼
Find OPEN orders for tick.symbol
    │
    ▼
Evaluate every order
    │
    ├── BUY  TP: price >= takeProfit
    ├── BUY  SL: price <= stopLoss
    ├── SELL TP: price <= takeProfit
    └── SELL SL: price >= stopLoss
    │
    ├── Chưa trigger ──> chờ tick tiếp theo
    └── Đã trigger
          │
          ▼
    Acquire close lock (optional Redis lock)
          │
          ▼
    BEGIN TRANSACTION
          │
          ▼
    Lock account + conditional update order
          │  WHERE id = ? AND status = OPEN
          │
          ├── affected rows = 0 ──> request khác đã close, dừng
          └── affected rows = 1
          │
          ▼
    Update order + balance + ledger
          │  closeReason = TP hoặc SL
          ▼
    COMMIT
          │
          ▼
    Publish private alert
```

Không nên viết lại công thức close trong `SlTpService`. `SlTpService` chỉ tìm trigger và gọi `CloseOrderService.close(orderId, reason, mark)`. Nhờ vậy manual close, TP và SL không tạo ba cách tính PnL khác nhau.

#### 4.17.9. Liquidation: bảo vệ account khi position không còn đủ maintenance margin

**Ý nghĩa nghiệp vụ:** Liquidation là close bắt buộc do rủi ro, không phải một lệnh mới từ user. Nó phải dùng server mark và phải kiểm tra lại điều kiện sau khi lock vì giá hoặc state có thể đã thay đổi.

```text
New market tick
    │
    ▼
Find OPEN orders for symbol
    │
    ▼
Calculate current position risk
    │  positionEquity = initialMargin + unrealizedPnl
    │  maintenance = currentNotional × maintenanceMarginRate
    │
    ├── positionEquity > maintenance ──> giữ OPEN
    └── positionEquity <= maintenance
          │
          ▼
    BEGIN TRANSACTION
          │
          ▼
    Lock account, then order
          │
          ▼
    Recalculate using latest mark
          │
          ├── Không còn đủ điều kiện ──> rollback, giữ OPEN
          └── Vẫn đủ điều kiện
                │
                ▼
          Update order(status = LIQUIDATED)
                │
                ▼
          Update account balance theo loss cap policy
                │
                ▼
          Insert ledger(type = LIQUIDATION)
                │
                ▼
          COMMIT + publish private alert
```

Không được query và liquidation toàn bộ order mà không lọc `symbol`/`status`. Market tick của BTC chỉ nên xử lý các position `OPEN` của BTC.

#### 4.17.10. Account snapshot: dữ liệu đọc tổng hợp, không phải entity riêng

**Ý nghĩa nghiệp vụ:** `TradingAccountsEntity.balance` chỉ là wallet balance. Các giá trị `equity`, `freeMargin`, `usedMargin` và `uPnL` phải được tính từ account + các position OPEN + server mark hiện tại.

```text
GET /account + JWT
    │
    ▼
Resolve userId from JWT
    │
    ▼
Load active TradingAccountsEntity
    │
    ▼
Load OrdersEntity WHERE accountId = account.id AND status = OPEN
    │
    ▼
Load one current server mark for each symbol
    │
    ▼
Calculate each position uPnL
    │
    ▼
Aggregate snapshot
    │  balance = account.balance
    │  equity = balance + total uPnL
    │  used = total initial margin
    │  free = equity - used
    │  maintenance = total maintenance margin
    │
    ▼
Return AccountSnapshot DTO
```

Nếu một position OPEN không có giá mới, không được âm thầm bỏ qua position đó. Trả lỗi `PRICE_UNAVAILABLE` hoặc đánh dấu response degraded, vì bỏ qua nó sẽ làm `freeMargin` sai và có thể cho phép mở lệnh vượt giới hạn.

#### 4.17.11. Refresh token: session lifecycle độc lập với trading state

**Ý nghĩa nghiệp vụ:** Refresh token chỉ thay thế access token. Nó không thay đổi balance, order hoặc account.

```text
Refresh cookie
    │
    ▼
Hash raw token
    │
    ▼
Find active RefreshTokensEntity
    │
    ├── Không tìm thấy / expired / revoked ──> 401
    └── Hợp lệ
          │
          ▼
    Revoke old refresh token
          │
          ▼
    Insert new refresh token hash
          │
          ▼
    Load enabled UsersEntity
          │
          ▼
    Issue new access JWT
          │
          ▼
    Set new HttpOnly cookie
```

Refresh token rotation nên nằm trong transaction riêng. Không dùng raw refresh token làm database key; chỉ lưu hash.

#### 4.17.12. Checklist trước khi code một use case

```text
[ ] Xác định userId từ JWT, không lấy ownership từ request body
[ ] Xác định entity đọc và entity thay đổi
[ ] Xác định state transition hợp lệ
[ ] Validate input trước transaction nếu không cần lock
[ ] Lấy market price trước transaction, kiểm tra freshness
[ ] Lock account trước order nếu flow thay đổi balance
[ ] Re-check state sau khi lock
[ ] Tính toán bằng BigDecimal
[ ] Cập nhật order/account/ledger trong cùng transaction
[ ] Dùng conditional update hoặc @Version để chống xử lý hai lần
[ ] Commit xong mới publish event
[ ] Viết unit test cho calculator và integration test cho transaction
```

#### 4.17.13. Khoảng trống giữa tài liệu và code hiện tại

Tài liệu này mô tả business target của MVP. Code hiện tại đã có authentication và CRUD cơ bản, nhưng trading flow chưa hoàn chỉnh:

| Business capability | Code hiện tại | Việc cần làm |
|---|---|---|
| Signup + initial account + ledger | Đã có trong `AuthService.signup()` | Bổ sung repository locking/constraint tests |
| Signin + JWT + refresh token | Đã có | Kiểm tra secret, ownership và integration tests |
| Generic order CRUD | Đã có trong `OrdersService` | Không dùng làm place/close use case cuối cùng |
| Place market order | Chưa có đầy đủ | Thêm price service, margin calculator, account lock, idempotency |
| Manual close | Chưa có đúng business flow | Thêm close service, PnL, balance update, ledger |
| Account snapshot | Chưa có | Thêm snapshot service và query OPEN orders |
| TP/SL watcher | Chưa có | Thêm market-tick trigger và dùng chung close service |
| Liquidation | Chưa có | Thêm risk evaluation, recheck sau lock và liquidation ledger |
| Account/order ownership | CRUD hiện có nhận ID từ DTO | Lấy userId từ JWT và query kèm ownership |
| Account/order/ledger foreign keys | Một số quan hệ hiện chỉ là `Long` reference | Bổ sung FK migration sau khi kiểm tra dữ liệu hiện tại |

Khi hiện thực, nên ưu tiên theo thứ tự:

```text
1. MarketPriceService + fake price provider cho test
2. MarginCalculator + PnL calculator
3. AccountSnapshotService
4. PlaceOrderService
5. CloseOrderService
6. Ledger consistency tests
7. TP/SL trigger dùng CloseOrderService
8. Liquidation dùng cùng close infrastructure
```

---

### 4.18. Business Execution Specification — Flow có thể chuyển trực tiếp thành code

Phần này cố định các quy tắc MVP để giảm việc phải tự quyết định business khi luyện Java. Mỗi use case được mô tả theo cùng một khuôn:

```text
Input
  → Validate
  → Load data
  → Read market data nếu cần
  → Begin transaction
  → Lock và re-check state
  → Calculate
  → Update/insert entities
  → Commit
  → Build response và publish event
```

Không được đổi thứ tự thành `save entity trước rồi mới validate` hoặc `publish event trước commit`.

#### 4.18.1. Quy ước dữ liệu cố định của MVP

##### A. Field client được phép gửi

Với API mở market order, request nghiệp vụ nên chỉ chứa các field sau:

| Field | Required | Server xử lý |
|---|---:|---|
| `symbol` | Yes | Normalize và kiểm tra trong `BTCUSDT`, `ETHUSDT`, `SOLUSDT` |
| `side` | Yes | `BUY` hoặc `SELL` |
| `mode` | Yes | `UNITS` hoặc `NOTIONAL` |
| `qtyUnits` | Khi `UNITS` | Dùng để tính quantity |
| `notionalUsd` | Khi `NOTIONAL` | Dùng để tính quantity |
| `leverage` | Yes | Kiểm tra 1..100 và giới hạn account/symbol |
| `tp` | No | Kiểm tra hướng hợp lệ theo side và server mark |
| `sl` | No | Kiểm tra hướng hợp lệ theo side và server mark |
| `clientMark` | No | Chỉ kiểm tra slippage và lưu audit |
| `clientTs` | No | Chỉ lưu audit, không dùng làm execution time |
| `maxSlippageBps` | No | Default 5, giới hạn 0..100 |
| `clientOrderId`/`Idempotency-Key` | Recommended | Dùng chống tạo order trùng |

`accountId`, `userId`, `entryPrice`, `quantity`, `notional`, `initialMargin`, `status`, `openedAt` và `tradingFee` không nên được tin từ client. Đây là server-owned fields.

`CreateOrderDto` hiện tại đang chứa cả field input và field đã tính toán. Khi code business service, hãy coi DTO hiện tại là persistence-shaped DTO hoặc tạo một `PlaceOrderRequest` riêng. Client không được gửi giá trị đã tính để ghi thẳng vào `OrdersEntity`.

##### B. Field server tự tạo khi mở order

| Entity field | Giá trị nguồn |
|---|---|
| `userId` | JWT `sub` |
| `accountId` | Active DEMO account của `userId` |
| `entryPrice` | Fresh server mark |
| `entryMarkTimestamp` | Timestamp của server mark |
| `quantity` | Request hoặc `notionalUsd / entryPrice` |
| `notional` | `quantity.abs() * entryPrice` |
| `initialMargin` | `notional / leverage` |
| `maintenanceMarginRate` | Trading configuration tại thời điểm mở |
| `status` | `OPEN` |
| `openedAt` | Server `now` |
| `tradingFee` | `0` trong MVP hiện tại |
| `version` | Database/JPA quản lý |

##### C. Decimal và rounding

Tất cả calculation dùng `BigDecimal`.

```text
Price scale:       12 decimal places
Quantity scale:    12 decimal places
Money/PnL scale:    8 decimal places
```

Quy tắc MVP:

1. Parse input bằng `new BigDecimal(String)`, không dùng `new BigDecimal(double)`.
2. Không `round` giữa các bước calculation nếu chưa cần persist.
3. Khi persist money/PnL, set scale 8 bằng policy thống nhất, ví dụ `RoundingMode.HALF_UP`.
4. Khi persist price/quantity, set scale 12.
5. Không so sánh tiền bằng `==`; dùng `compareTo`.
6. Không dùng `double`, `float`, `Math.round` cho financial calculation.

##### D. Price freshness

`MarketPriceService` trả về:

```text
symbol
price
timestamp
source
```

Một price hợp lệ phải thỏa:

```text
price > 0
timestamp != null
now - timestamp <= maxPriceAge
```

MVP dùng `maxPriceAge = 5 seconds`. Nếu không thỏa, dừng flow với `503 PRICE_UNAVAILABLE` hoặc `503 PRICE_STALE`.

##### E. Các công thức chung

```text
notional = abs(quantity) × price

initialMargin = notional / leverage

BUY unrealizedPnl  = (markPrice - entryPrice) × quantity
SELL unrealizedPnl = (entryPrice - markPrice) × quantity

equity = balance + totalUnrealizedPnl
usedMargin = sum(initialMargin of OPEN orders)
freeMargin = equity - usedMargin

maintenanceMargin = currentNotional × maintenanceMarginRate
currentNotional = abs(quantity) × markPrice
```

MVP fee rate hiện tại là `0`, nhưng vẫn giữ các field fee để có thể bật sau.

```text
openingFee = entryNotional × takerFeeRate
closingFee = closeNotional × takerFeeRate
grossPnl = side-specific PnL
netPnl = grossPnl - openingFee - closingFee
```

Nếu bật opening fee, phải quyết định rõ fee được trừ ngay lúc mở và tạo `TRADING_FEE` ledger, hoặc cộng dồn đến lúc close. Không được chỉ đổi công thức mà bỏ qua ledger.

#### 4.18.2. Signup — đặc tả thực thi đầy đủ

##### Input

```json
{
  "email": "user@example.com",
  "password": "secret123"
}
```

##### Validation

Thực hiện theo đúng thứ tự:

1. Request body không được null.
2. `email` không được null hoặc blank.
3. `email.trim()` không vượt quá 320 characters.
4. Email phải có format hợp lệ.
5. Chuẩn hóa email bằng `trim().toLowerCase(Locale.ROOT)`.
6. `password` không được null hoặc blank.
7. Password phải có tối thiểu 6 characters để tương thích frontend MVP.
8. Password UTF-8 không vượt quá 72 bytes vì BCrypt giới hạn 72 bytes.
9. `existsByEmailIgnoreCase(normalizedEmail)` phải là false.

Nếu validation thất bại, không insert bất kỳ entity nào.

##### Thứ tự ghi entity

```text
BEGIN TRANSACTION

1. Hash password bằng PasswordEncoder

2. Tạo UsersEntity:
   email       = normalizedEmail
   passwordHash = encodedPassword
   enabled     = true

3. Save UsersEntity
   Kết quả bắt buộc: user.id != null

4. Tạo TradingAccountsEntity:
   userId          = user.id
   accountType     = DEMO
   currency        = USDT
   balance         = 5000.00
   status          = ACTIVE
   defaultLeverage = 1

5. Save TradingAccountsEntity
   Kết quả bắt buộc: account.id != null

6. Tạo AccountLedgersEntity:
   accountId      = account.id
   orderId        = null
   type           = INITIAL_DEPOSIT
   amount         = 5000.00
   balanceBefore  = 0.00
   balanceAfter   = 5000.00
   description    = Initial demo account deposit

7. Issue access JWT với subject = user.id

8. Generate raw refresh token bằng SecureRandom

9. Hash raw refresh token bằng SHA-256

10. Save RefreshTokensEntity:
    userId       = user.id
    tokenHash    = hash(rawToken)
    createdAt    = now
    expiresAt    = now + refreshTokenTtl
    device/ip/ua = metadata từ request

COMMIT
```

JWT và raw refresh token chỉ được trả sau khi transaction hoàn tất. Database chỉ lưu refresh token hash, không lưu raw token.

##### Kết quả

```text
HTTP 201
accessToken = JWT ngắn hạn
refreshToken = HttpOnly cookie
user = id, email, enabled
```

##### Invariant sau signup

```text
users.id tồn tại
trading_accounts.user_id = users.id
trading_accounts.balance = 5000.00
account_ledgers.account_id = trading_accounts.id
account_ledgers.balance_before = 0.00
account_ledgers.balance_after = 5000.00
```

Nếu một bước fail, toàn bộ user/account/ledger/refresh token phải rollback.

#### 4.18.3. Signin — đặc tả thực thi đầy đủ

##### Validation và authentication

1. Normalize email giống signup.
2. Password không vượt quá 72 UTF-8 bytes.
3. Gọi `AuthenticationManager.authenticate(...)`.
4. `AuthUserDetailsService` load user theo email.
5. `PasswordEncoder` compare raw password với password hash.
6. Nếu user không tồn tại hoặc password sai, trả cùng lỗi `401 INVALID_CREDENTIALS`.
7. Nếu user tồn tại nhưng `enabled = false`, trả `403 ACCOUNT_DISABLED`.

##### Thứ tự thực thi

```text
1. Authenticate credentials
2. Load enabled UsersEntity
3. Issue access JWT:
   sub = user.id.toString()
   iss = configured issuer
   aud = configured audience
   scope = USER
   iat = now
   exp = now + accessTokenTtl
   jti = random UUID
4. Generate and hash refresh token
5. Save RefreshTokensEntity
6. Set refresh cookie
7. Return access token and UserResponse
```

Signin không tạo user mới, không tạo trading account mới và không reset balance.

#### 4.18.4. Place market order — đặc tả thực thi đầy đủ

##### Input nghiệp vụ

```json
{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "mode": "UNITS",
  "qtyUnits": 0.01,
  "notionalUsd": null,
  "leverage": 10,
  "tp": 70000.0,
  "sl": 62000.0,
  "clientMark": 65000.0,
  "clientTs": 1784100000123,
  "maxSlippageBps": 5,
  "clientOrderId": "client-order-001"
}
```

##### Validation trước khi đọc account

1. Lấy `userId` từ JWT `sub`.
2. Không dùng `userId` hoặc `accountId` từ request để xác định ownership.
3. `symbol` phải thuộc allowlist.
4. `side` phải là `BUY` hoặc `SELL`.
5. `mode` phải là `UNITS` hoặc `NOTIONAL`.
6. Nếu `mode = UNITS`, `qtyUnits` bắt buộc và `notionalUsd` phải null hoặc bị bỏ qua.
7. Nếu `mode = NOTIONAL`, `notionalUsd` bắt buộc và lớn hơn zero.
8. `leverage` bắt buộc, là integer từ 1 đến 100.
9. Nếu account có max leverage riêng, `leverage` không được vượt max đó.
10. `maxSlippageBps` default 5, phải từ 0 đến 100.
11. Nếu `clientMark` khác null, phải lớn hơn zero.
12. Nếu `tp` khác null, phải lớn hơn zero.
13. Nếu `sl` khác null, phải lớn hơn zero.
14. `clientOrderId` nếu có thì trim, không blank, tối đa 100 characters.

##### Đọc và validate server price

```text
1. Normalize symbol
2. Read price:last:{symbol} from Redis
3. Nếu Redis miss, fallback latest trade trong database
4. Nếu không có giá → 503 PRICE_UNAVAILABLE
5. Nếu giá <= 0 → 503 PRICE_UNAVAILABLE
6. Nếu price age > 5 seconds → 503 PRICE_STALE
7. Gọi giá này là serverMark
```

Không dùng `clientMark` làm entry price.

##### Validate slippage

Nếu `clientMark` được gửi:

```text
slippageBps = abs(serverMark - clientMark)
                / clientMark
                × 10,000
```

Nếu:

```text
slippageBps > maxSlippageBps
```

thì trả `409 SLIPPAGE_EXCEEDED`. Không insert order.

##### Validate TP/SL theo side

```text
BUY:
  takeProfit phải > serverMark
  stopLoss   phải < serverMark

SELL:
  takeProfit phải < serverMark
  stopLoss   phải > serverMark
```

Ví dụ với `BUY` và `serverMark = 65000`:

```text
tp = 70000  → hợp lệ
sl = 62000  → hợp lệ
tp = 62000  → invalid
sl = 70000  → invalid
```

Nếu TP/SL không hợp lệ, trả `422 INVALID_TAKE_PROFIT` hoặc `422 INVALID_STOP_LOSS`.

##### Tính quantity và margin

Nếu `mode = UNITS`:

```text
quantity = qtyUnits
```

Nếu `mode = NOTIONAL`:

```text
quantity = notionalUsd / serverMark
```

Sau đó:

```text
notional = abs(quantity) × serverMark
initialMargin = notional / leverage
maintenanceMarginRate = config.maintenanceMarginRate
estimatedOpeningFee = notional × config.takerFeeRate
requiredMargin = initialMargin + estimatedOpeningFee
```

Kiểm tra sau calculation:

```text
quantity > 0
notional > 0
initialMargin > 0
maintenanceMarginRate >= 0
requiredMargin > 0
```

##### Transaction và account snapshot

```text
BEGIN TRANSACTION

1. Resolve account:
   user_id = JWT userId
   account_type = DEMO
   status = ACTIVE

2. Lock TradingAccountsEntity bằng PESSIMISTIC_WRITE.

3. Nếu account không tồn tại → 404 ACCOUNT_NOT_FOUND.

4. Nếu account status không ACTIVE → 403 ACCOUNT_NOT_ACTIVE.

5. Nếu clientOrderId đã tồn tại trong account:
   - Payload giống request hiện tại → trả order cũ.
   - Payload khác request hiện tại → 409 IDEMPOTENCY_KEY_REUSED.

6. Load tất cả OrdersEntity của account với status = OPEN.

7. Với mỗi order OPEN:
   - Read current mark theo order.symbol.
   - Nếu thiếu mark của bất kỳ symbol nào → rollback + 503 PRICE_UNAVAILABLE.
   - Tính unrealizedPnl.

8. Tính account snapshot:
   totalUnrealizedPnl = sum(position unrealizedPnl)
   equity = account.balance + totalUnrealizedPnl
   usedMargin = sum(position.initialMargin)
   freeMargin = equity - usedMargin

9. Nếu freeMargin < requiredMargin:
   rollback + 400 INSUFFICIENT_FREE_MARGIN.

10. Tạo OrdersEntity:
    accountId               = account.id
    userId                  = JWT userId
    clientOrderId           = request clientOrderId
    symbol                  = normalized symbol
    side                    = request side
    orderType               = MARKET
    sizingMode              = request mode
    quantity                = calculated quantity
    entryPrice              = serverMark
    entryMarkTimestamp      = serverMark.timestamp
    notional                = calculated notional
    leverage                = request leverage
    initialMargin           = calculated initialMargin
    maintenanceMarginRate   = configured rate
    takeProfit              = request tp
    stopLoss                = request sl
    status                  = OPEN
    tradingFee              = ZERO
    clientMark              = request clientMark
    clientTimestamp         = request clientTs converted to Instant
    maxSlippageBps          = resolved max slippage
    openedAt                = server now

11. Save OrdersEntity.

12. Do not update account.balance in the current isolated-margin policy.

13. Do not insert ledger because balance did not change.

COMMIT
```

##### Response sau khi mở order

Các field response phải lấy từ entity đã lưu và calculation server:

```text
order.id          = generated database ID
order.status      = OPEN
order.entry       = entryPrice/serverMark
order.volume      = quantity
order.requiredMargin = initialMargin
account.balance   = account.balance hiện tại
account.equity    = snapshot.equity sau khi thêm position
account.freeMargin = snapshot.freeMargin - requiredMargin
account.marginUsed = snapshot.usedMargin + initialMargin
```

Event `order:placed` chỉ publish sau commit. Nếu publish thất bại, order vẫn tồn tại; production nên dùng outbox.

#### 4.18.5. Manual close — đặc tả thực thi đầy đủ

##### Mục tiêu

Một close hợp lệ phải đồng thời:

```text
OPEN order
  → CLOSED order
  → account balance nhận net realized PnL
  → ledger ghi đúng before/after
```

Nếu một trong ba thay đổi fail, rollback cả ba.

##### Thứ tự xử lý

```text
1. Lấy userId từ JWT.
2. Load order theo orderId + userId/account ownership.
3. Nếu không tìm thấy hoặc không thuộc user → 404.
4. Đọc server mark mới nhất của order.symbol.
5. Nếu price missing/stale → 503.
6. BEGIN TRANSACTION.
7. Lock account trước.
8. Lock/re-read order.
9. Re-check ownership.
10. Re-check order.status = OPEN.
11. Tính PnL.
12. Update order.
13. Update account balance.
14. Insert account ledger.
15. COMMIT.
16. Publish order:closed.
```

##### Tính close PnL

```text
closeNotional = abs(quantity) × closePrice

BUY grossPnl  = (closePrice - entryPrice) × quantity
SELL grossPnl = (entryPrice - closePrice) × quantity

closingFee = closeNotional × takerFeeRate
netPnl = grossPnl - closingFee
```

MVP hiện tại `takerFeeRate = 0`, vì vậy:

```text
netPnl = grossPnl
```

##### Cập nhật entity

```text
OrdersEntity:
  status       = CLOSED
  closeReason  = MANUAL
  closePrice   = server mark
  realizedPnl  = netPnl
  tradingFee   = closingFee
  closedAt     = server now

TradingAccountsEntity:
  balanceBefore = account.balance
  balanceAfter  = balanceBefore + netPnl
  account.balance = balanceAfter

AccountLedgersEntity:
  accountId     = account.id
  orderId       = order.id
  type          = REALIZED_PNL
  amount        = netPnl
  balanceBefore = balanceBefore
  balanceAfter  = balanceAfter
```

`balanceAfter` trong ledger phải bằng đúng `TradingAccountsEntity.balance` sau update. Không làm tròn hai giá trị theo hai cách khác nhau.

##### Chống close hai lần

Request close phải có hàng rào cuối:

```sql
UPDATE orders
SET status = 'CLOSED', ...
WHERE id = :orderId
  AND user_id = :userId
  AND status = 'OPEN';
```

Nếu affected rows bằng `0`, không được update balance hoặc insert ledger. Đọc lại order để trả `409 ORDER_NOT_OPEN`.

##### Policy isolated loss cap

Để bảo đảm `trading_accounts.balance >= 0`, MVP dùng policy:

```text
maximumPositionLoss = initialMargin
appliedPnl = max(netPnl, -maximumPositionLoss)
balanceAfter = balanceBefore + appliedPnl
```

Nếu muốn dùng raw PnL thay vì cap, phải thay đổi database constraint và liquidation policy đồng thời. Không để manual close và liquidation dùng hai policy khác nhau.

#### 4.18.6. Account snapshot — đặc tả tính toán đầy đủ

##### Input

```text
JWT userId
```

##### Thứ tự đọc

```text
1. Resolve active DEMO TradingAccountsEntity theo userId.
2. Nếu không có account → 404 ACCOUNT_NOT_FOUND.
3. Load OrdersEntity với accountId = account.id và status = OPEN.
4. Group open orders theo symbol.
5. Đọc một fresh mark cho từng symbol.
6. Nếu thiếu bất kỳ mark nào → 503 PRICE_UNAVAILABLE.
```

##### Tính cho từng position

```text
currentNotional = abs(order.quantity) × markPrice

BUY uPnL  = (markPrice - order.entryPrice) × order.quantity
SELL uPnL = (order.entryPrice - markPrice) × order.quantity

positionMaintenance = currentNotional
                       × order.maintenanceMarginRate
```

##### Tính tổng account

```text
totalUpnl = sum(position.uPnL)
balance = account.balance
equity = balance + totalUpnl
usedMargin = sum(order.initialMargin)
freeMargin = equity - usedMargin
maintenance = sum(positionMaintenance)

if usedMargin > 0:
    marginLevel = equity / usedMargin × 100
else:
    marginLevel = null
```

Không lưu `equity`, `freeMargin`, `upnl` vào `trading_accounts` vì đây là dữ liệu snapshot thay đổi theo market price.

##### Trường hợp không có position

```text
totalUpnl       = 0
equity          = balance
usedMargin      = 0
freeMargin      = balance
maintenance     = 0
marginLevel     = null
```

#### 4.18.7. TP/SL — đặc tả xử lý từng market tick

##### Trigger conditions

```text
BUY:
  TP nếu lastPrice >= takeProfit
  SL nếu lastPrice <= stopLoss

SELL:
  TP nếu lastPrice <= takeProfit
  SL nếu lastPrice >= stopLoss
```

Nếu field TP/SL là null, điều kiện tương ứng luôn là false.

##### Flow

```text
1. Nhận TradesEntity/market tick.
2. Validate price > 0.
3. Update latest price cache.
4. Query orders WHERE symbol = tick.symbol AND status = OPEN.
5. Với từng order, xác định trigger reason.
6. Nếu không trigger, bỏ qua.
7. Nếu cả TP và SL cùng trigger trên một tick, MVP ưu tiên TP.
8. Gọi chung CloseOrderService với:
   closeReason = TP hoặc SL
   closePrice  = tick.price
9. CloseService lock account/order và re-check status.
10. Chỉ request thắng conditional update mới update balance/ledger.
11. Commit.
12. Publish private alert sau commit.
```

TP/SL không được tự viết lại PnL formula. Chúng chỉ quyết định `closeReason` và gọi close infrastructure dùng chung.

#### 4.18.8. Liquidation — đặc tả risk check đầy đủ

##### Tính risk của một position

```text
currentNotional = abs(quantity) × markPrice
unrealizedPnl   = side-specific uPnL
positionEquity  = initialMargin + unrealizedPnl
maintenance     = currentNotional × maintenanceMarginRate
liquidationFeeReserve = currentNotional × liquidationFeeRate
```

Trigger liquidation khi:

```text
positionEquity <= maintenance + liquidationFeeReserve
```

Nếu MVP chưa có liquidation fee, `liquidationFeeRate = 0`.

##### Flow

```text
1. Market tick tới.
2. Chỉ query OPEN orders của tick.symbol.
3. Tính risk bằng tick.price.
4. Position chưa đạt threshold → không thay đổi database.
5. Position đạt threshold → bắt đầu transaction.
6. Lock TradingAccountsEntity.
7. Lock/re-read OrdersEntity.
8. Nếu order không còn OPEN → dừng, không ghi ledger.
9. Đọc/recompute giá và risk sau lock.
10. Nếu risk không còn đạt threshold → rollback, giữ OPEN.
11. Nếu vẫn đạt threshold:
    status      = LIQUIDATED
    closeReason = LIQUIDATION
    closePrice  = latest mark
    realizedPnl = applied liquidation PnL
    closedAt    = now
12. Update account balance.
13. Insert ledger type = LIQUIDATION.
14. COMMIT.
15. Publish private liquidation alert.
```

Liquidation phải re-check sau lock vì manual close hoặc TP/SL có thể đã xử lý position trong lúc risk worker đang chạy.

#### 4.18.9. Refresh token và logout — đặc tả session

##### Refresh

```text
1. Đọc raw refresh token từ HttpOnly cookie.
2. Nếu null/blank → 401.
3. Hash raw token bằng SHA-256.
4. Tìm RefreshTokensEntity theo tokenHash.
5. Nếu không tìm thấy → 401.
6. Nếu revokedAt != null → 401.
7. Nếu expiresAt <= now → 401.
8. Set lastUsedAt = now.
9. Set revokedAt = now cho token cũ.
10. Generate raw token mới.
11. Hash và insert token mới.
12. Load UsersEntity theo userId và kiểm tra enabled.
13. Issue access JWT mới.
14. Set cookie mới.
15. Commit.
```

##### Logout

```text
1. Đọc raw token từ cookie.
2. Nếu thiếu token, vẫn clear cookie và trả thành công.
3. Hash token.
4. Tìm token active.
5. Nếu có, set revokedAt = now.
6. Clear HttpOnly cookie.
```

Logout không thay đổi `users`, `trading_accounts`, `orders` hoặc `account_ledgers`.

#### 4.18.10. Bảng lỗi theo từng bước

| Bước | Điều kiện | Error |
|---|---|---|
| Authentication | JWT thiếu/sai/hết hạn | `401 UNAUTHORIZED` |
| Ownership | User không sở hữu account/order | `404 NOT_FOUND` |
| Account | Account không tồn tại | `404 ACCOUNT_NOT_FOUND` |
| Account | Account không ACTIVE | `403 ACCOUNT_NOT_ACTIVE` |
| Price | Không có server price | `503 PRICE_UNAVAILABLE` |
| Price | Price quá cũ | `503 PRICE_STALE` |
| Order input | Symbol không hỗ trợ | `422 UNSUPPORTED_SYMBOL` |
| Order input | Sai side/mode | `422 INVALID_SIDE`/`INVALID_MODE` |
| Order input | Quantity/notional không hợp lệ | `422 INVALID_QUANTITY`/`INVALID_NOTIONAL` |
| Order input | Leverage ngoài 1..100 | `422 INVALID_LEVERAGE` |
| Order input | TP/SL sai hướng | `422 INVALID_TAKE_PROFIT`/`INVALID_STOP_LOSS` |
| Slippage | Vượt max slippage | `409 SLIPPAGE_EXCEEDED` |
| Idempotency | Key trùng payload khác | `409 IDEMPOTENCY_KEY_REUSED` |
| Margin | Free margin không đủ | `400 INSUFFICIENT_FREE_MARGIN` |
| State | Close order không phải OPEN | `409 ORDER_NOT_OPEN` |
| Refresh | Token revoked/expired/unknown | `401 INVALID_REFRESH_TOKEN` |

Mỗi error phải dừng flow ngay tại bước phát hiện. Không được tiếp tục save entity sau khi đã xác định request invalid.

#### 4.18.11. Unit test cases bắt buộc cho calculator

##### Quantity and margin

```text
UNITS: quantity = 0.01
NOTIONAL: notionalUsd = 650, mark = 65000 → quantity = 0.01
notional = quantity × mark
initialMargin = notional / leverage
```

Test thêm:

- Zero quantity.
- Negative quantity.
- Leverage 0, 1, 100, 101.
- Decimal scale và rounding.
- Quantity quá precision cho instrument.

##### PnL

Với entry `65000`, close `65100`, quantity `0.01`:

```text
BUY  PnL = (65100 - 65000) × 0.01 = 1.00
SELL PnL = (65000 - 65100) × 0.01 = -1.00
```

Test thêm:

- BUY profit/loss.
- SELL profit/loss.
- Zero fee.
- Opening/closing fee khi bật fee.
- Loss cap.

##### TP/SL

- BUY TP trigger tại đúng giá.
- BUY SL trigger tại đúng giá.
- SELL TP trigger tại đúng giá.
- SELL SL trigger tại đúng giá.
- TP/SL null.
- Cả TP và SL trigger cùng một tick.

##### Account snapshot

- Không có open position.
- Một BUY có profit.
- Một SELL có loss.
- Nhiều symbol.
- Thiếu một server mark.
- `usedMargin = 0` phải trả `marginLevel = null`.

#### 4.18.12. Integration test flow hoàn chỉnh

```text
1. Signup user.
2. Assert users row exists.
3. Assert one DEMO account with balance 5000.
4. Assert INITIAL_DEPOSIT ledger before=0, after=5000.
5. Signin và nhận JWT.
6. GET /auth/me bằng JWT.
7. Mock fresh BTCUSDT price.
8. Place BUY order.
9. Assert order OPEN.
10. Assert account.balance chưa đổi theo isolated-margin policy.
11. Assert account.usedMargin tăng.
12. Mock price tăng.
13. GET account snapshot và assert uPnL/equity/freeMargin.
14. Close order.
15. Assert order CLOSED.
16. Assert realizedPnl đúng.
17. Assert account.balance nhận PnL.
18. Assert REALIZED_PNL ledger before/after đúng.
19. Repeat close request.
20. Assert không có ledger thứ hai và không cộng PnL lần hai.
```

Mục tiêu của integration test là xác nhận **nhiều entity thay đổi đúng cùng nhau**, không chỉ kiểm tra HTTP status `200`.

---

## 5. REST API conventions

### 5.1. Base URL

```text
/api/v1
```

Frontend hiện đặt `API_BASE=/api` rồi gọi `/v1/...`.

### 5.2. Authentication

Protected API nhận:

```http
Authorization: Bearer <JWT>
```

JWT nên chứa:

```json
{
  "sub": "123",
  "userId": 123,
  "email": "user@example.com",
  "iat": 1784100000,
  "exp": 1784704800
}
```

`userId` được giữ để tương thích logic cũ; `sub` là claim chuẩn.

### 5.3. Content type

- Request JSON: `Content-Type: application/json`.
- Response JSON: `application/json`.
- Datetime: ISO-8601 UTC.

### 5.4. Error response thống nhất

```json
{
  "timestamp": "2026-07-15T10:15:30.123Z",
  "status": 409,
  "code": "SLIPPAGE_EXCEEDED",
  "message": "Market price moved beyond the accepted slippage.",
  "error": "Market price moved beyond the accepted slippage.",
  "path": "/api/v1/orders",
  "traceId": "8cb0d91c4f3a",
  "details": {
    "serverMark": 65010.25,
    "clientMark": 65000,
    "bps": 1.58,
    "maxSlippageBps": 1
  }
}
```

Field `error` được giữ trong giai đoạn chuyển đổi vì frontend hiện đọc lần lượt `error`, `message` và `code`.

### 5.5. HTTP status

- `200 OK`: đọc hoặc thao tác thành công.
- `201 Created`: signup hoặc tạo order thành công.
- `400 Bad Request`: request hợp lệ về JSON nhưng vi phạm business rule đơn giản.
- `401 Unauthorized`: thiếu/hỏng/hết hạn JWT.
- `403 Forbidden`: user hợp lệ nhưng không được phép thao tác.
- `404 Not Found`: resource không tồn tại hoặc không thuộc user.
- `409 Conflict`: state conflict, order đã đóng hoặc slippage.
- `422 Unprocessable Entity`: validation field.
- `429 Too Many Requests`: rate limit.
- `503 Service Unavailable`: không có giá hoặc giá stale.

---

## 6. Danh sách API frontend hiện tại bắt buộc cần có

| Method | Path | Auth | Chức năng |
|---|---|---:|---|
| POST | `/api/v1/signup` | No | Đăng ký và tạo demo account |
| POST | `/api/v1/signin` | No | Đăng nhập |
| GET | `/api/v1/verify` | Yes | Kiểm tra token và lấy user |
| GET | `/api/v1/account` | Yes | Account snapshot |
| GET | `/api/v1/positions` | Yes | Danh sách position |
| GET | `/api/v1/orders` | Yes | Danh sách order dạng wrapper |
| POST | `/api/v1/orders` | Yes | Mở market position |
| POST | `/api/v1/orders/{id}/close` | Yes | Đóng position |
| GET | `/api/v1/candles` | No | Candle history |
| GET | `/api/v1/last` | No | Giá cuối của các symbol |
| WS | `/ws/` | No | Public realtime trade stream |

---

## 7. Chi tiết từng API

### 7.1. POST /api/v1/signup

Auth: không yêu cầu.

Body:

```json
{
  "email": "user@example.com",
  "password": "secret123"
}
```

Validation:

- Email bắt buộc, đúng định dạng, tối đa 320 ký tự.
- Normalize email bằng `trim().toLowerCase(Locale.ROOT)`.
- Password tối thiểu 6 ký tự để tương thích frontend hiện tại; production nên nâng lên 8.
- BCrypt chỉ nhận tối đa 72 bytes; phải kiểm tra trước khi hash.
- Email chưa tồn tại.

Business flow:

1. Kiểm tra email.
2. Hash password bằng BCrypt strength 10-12.
3. Tạo user.
4. Tạo demo account balance 5000 USDT.
5. Tạo initial deposit ledger.
6. Sinh JWT.
7. Commit transaction trước khi trả response.

Success: `201 Created`

```json
{
  "token": "<jwt>",
  "user": {
    "id": 123,
    "email": "user@example.com",
    "balance": 5000.0,
    "createdAt": "2026-07-15T10:15:30.123Z"
  }
}
```

Errors:

- `400 INVALID_EMAIL`.
- `400 WEAK_PASSWORD`.
- `409 EMAIL_ALREADY_REGISTERED`.
- `500 INTERNAL_ERROR`.

Không được trả thông tin cho biết password hash hoặc database exception.

### 7.2. POST /api/v1/signin

Auth: không yêu cầu.

Body:

```json
{
  "email": "user@example.com",
  "password": "secret123"
}
```

Validation:

- Email/password bắt buộc.
- Dùng cùng một lỗi cho email không tồn tại và password sai để tránh user enumeration.
- Có thể rate limit theo IP và email.

Success: `200 OK`

```json
{
  "token": "<jwt>",
  "user": {
    "id": 123,
    "email": "user@example.com",
    "balance": 5000.0
  }
}
```

Errors:

- `401 INVALID_CREDENTIALS`.
- `403 ACCOUNT_DISABLED`.
- `429 TOO_MANY_LOGIN_ATTEMPTS`.

### 7.3. GET /api/v1/verify

Auth: bắt buộc.

Path/query/body: không có.

Business flow:

1. Spring Security xác thực Bearer token.
2. Lấy `userId` từ JWT.
3. Đọc user và demo account.
4. Kiểm tra user/account còn active.

Success: `200 OK`

```json
{
  "user": {
    "id": 123,
    "email": "user@example.com",
    "balance": 5000.0,
    "createdAt": "2026-07-15T10:15:30.123Z"
  }
}
```

Errors:

- `401 INVALID_TOKEN`.
- `401 TOKEN_EXPIRED`.
- `404 USER_NOT_FOUND`.
- `403 ACCOUNT_DISABLED`.

### 7.4. GET /api/v1/account

Auth: bắt buộc.

Path/query/body: không có.

Response được tính từ balance, tất cả open positions và server mark mới nhất.

Success: `200 OK`

```json
{
  "balance": 5000.0,
  "equity": 5012.45,
  "free": 4362.45,
  "used": 650.0,
  "upnl": 12.45,
  "level": 771.15,
  "maintenance": 32.5,
  "leverage": 100,
  "priceAsOf": "2026-07-15T10:15:30.123Z"
}
```

Ý nghĩa:

- `balance`: wallet balance đã realize.
- `upnl`: tổng unrealized PnL.
- `equity = balance + upnl`.
- `used`: tổng initial margin của open positions.
- `free = equity - used`.
- `maintenance`: tổng maintenance margin.
- `level = equity / used * 100`; nếu used bằng 0 có thể trả `null` thay vì Infinity.
- `leverage`: default/account max leverage dùng cho UI; mỗi position vẫn có leverage riêng.

Errors:

- `401 UNAUTHORIZED`.
- `404 ACCOUNT_NOT_FOUND`.
- `503 PRICE_UNAVAILABLE` nếu không thể định giá open position.

Không nên âm thầm bỏ qua position không có giá. Nếu thiếu giá, response account có thể sai và dẫn đến cho phép mở thêm lệnh ngoài ý muốn.

### 7.5. GET /api/v1/positions

Auth: bắt buộc.

Query tùy chọn:

| Param | Type | Default | Ý nghĩa |
|---|---|---|---|
| `status` | OPEN/CLOSED/LIQUIDATED/ALL | ALL | Lọc trạng thái |
| `symbol` | string | all | Lọc symbol |
| `limit` | integer | 100 | 1..200 |

Để tương thích frontend hiện tại, response root phải là JSON array.

Success: `200 OK`

```json
[
  {
    "id": "b177a8dd-3b97-43ae-a956-94c1a52bfed0",
    "symbol": "BTCUSDT",
    "side": "BUY",
    "volume": 0.01,
    "units": 0.01,
    "entry": 65000.0,
    "entryPrice": 65000.0,
    "mark": 65125.0,
    "leverage": 10,
    "requiredMargin": 65.0,
    "status": "OPEN",
    "closePrice": null,
    "closedAt": null,
    "realizedPnl": null,
    "unrealizedPnl": 1.25,
    "take_profit": 70000.0,
    "stop_loss": 62000.0,
    "createdAt": "2026-07-15T10:15:30.123Z"
  }
]
```

Frontend hiện cần tối thiểu:

- id
- symbol
- side
- volume
- entry hoặc entryPrice
- leverage
- status
- closePrice
- closedAt
- realizedPnl
- take_profit
- stop_loss

Chỉ trả position thuộc account của JWT hiện tại.

### 7.6. GET /api/v1/orders

Auth: bắt buộc.

Query:

| Param | Type | Default | Ý nghĩa |
|---|---|---|---|
| `status` | enum | ALL | Lọc trạng thái |
| `symbol` | string | all | Lọc symbol |
| `limit` | integer | 100 | Số lượng |

Success: `200 OK`

```json
{
  "orders": [
    {
      "id": "b177a8dd-3b97-43ae-a956-94c1a52bfed0",
      "symbol": "BTCUSDT",
      "side": "BUY",
      "volume": 0.01,
      "entry": 65000.0,
      "entryPrice": 65000.0,
      "leverage": 10,
      "status": "OPEN",
      "closePrice": null,
      "closedAt": null,
      "realizedPnl": null,
      "take_profit": 70000.0,
      "stop_loss": 62000.0,
      "createdAt": "2026-07-15T10:15:30.123Z"
    }
  ]
}
```

`/positions` và `/orders` có thể dùng chung service/query nhưng khác response envelope để giữ contract.

### 7.7. POST /api/v1/orders

Auth: bắt buộc.

Header khuyến nghị:

```http
Idempotency-Key: 2ddf5359-4fca-42f9-b85d-b87d09217294
```

Body FE hiện gửi:

```json
{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "mode": "UNITS",
  "qtyUnits": 0.01,
  "notionalUsd": null,
  "leverage": 10,
  "tp": 70000.0,
  "sl": 62000.0,
  "clientMark": 65000.0,
  "clientTs": 1784100000123,
  "maxSlippageBps": 5
}
```

Field:

| Field | Required | Validation |
|---|---:|---|
| `symbol` | Yes | Một trong BTCUSDT, ETHUSDT, SOLUSDT |
| `side` | Yes | BUY hoặc SELL |
| `mode` | Yes | UNITS hoặc NOTIONAL |
| `qtyUnits` | Khi UNITS | > 0, đúng quantity precision |
| `notionalUsd` | Khi NOTIONAL | > 0 |
| `leverage` | Yes | Integer 1..100 |
| `tp` | No | > 0 và đúng phía so với mark |
| `sl` | No | > 0 và đúng phía so với mark |
| `clientMark` | No | > 0, chỉ dùng kiểm tra slippage |
| `clientTs` | No | Unix milliseconds |
| `maxSlippageBps` | No | Mặc định 5, giới hạn 0..100 |

TP/SL validation:

- BUY: TP phải lớn hơn mark, SL phải nhỏ hơn mark.
- SELL: TP phải nhỏ hơn mark, SL phải lớn hơn mark.
- Nếu không muốn chặn ngay, ít nhất phải reject TP/SL không hợp lệ thay vì tạo position không bao giờ trigger.

Business flow bắt buộc:

1. Xác thực user.
2. Normalize symbol.
3. Lấy server mark và mark timestamp từ `MarketPriceService`.
4. Reject nếu không có giá.
5. Reject nếu giá cũ hơn 5 giây.
6. Nếu có clientMark, tính slippage nhưng không dùng clientMark làm execution price.
7. Tính quantity nếu mode là NOTIONAL.
8. Tính notional, initial margin và maintenance margin.
9. Bắt đầu transaction.
10. Lock account bằng `SELECT ... FOR UPDATE` hoặc repository pessimistic lock.
11. Recompute account snapshot tại cùng thời điểm.
12. Kiểm tra free margin.
13. Insert OPEN order.
14. Commit.
15. Publish `order:placed` sau commit.

Phép tính:

```text
quantity = qtyUnits

Nếu mode = NOTIONAL:
quantity = notionalUsd / serverMark

notional = abs(quantity) * serverMark
initialMargin = notional / leverage
required = initialMargin + estimatedOpeningFee
freeBefore = equity - usedMargin
```

Reject nếu `freeBefore < required`.

Slippage:

```text
bps = abs(serverMark - clientMark) / clientMark * 10,000
```

Reject `409 SLIPPAGE_EXCEEDED` nếu `bps > maxSlippageBps`.

Success: `201 Created`

```json
{
  "ok": true,
  "order": {
    "id": "b177a8dd-3b97-43ae-a956-94c1a52bfed0",
    "createdAt": "2026-07-15T10:15:30.123Z",
    "symbol": "BTCUSDT",
    "side": "BUY",
    "type": "MARKET",
    "mode": "UNITS",
    "volume": 0.01,
    "units": 0.01,
    "entry": 65000.0,
    "entryPrice": 65000.0,
    "mark": 65000.0,
    "markTs": 1784100000123,
    "requiredMargin": 65.0,
    "leverage": 10,
    "status": "OPEN",
    "tp": 70000.0,
    "sl": 62000.0,
    "take_profit": 70000.0,
    "stop_loss": 62000.0
  },
  "account": {
    "balance": 5000.0,
    "equity": 5000.0,
    "freeMargin": 4935.0,
    "marginUsed": 65.0
  }
}
```

Error quan trọng:

- `422 MISSING_SYMBOL`.
- `422 UNSUPPORTED_SYMBOL`.
- `422 BAD_SIDE`.
- `422 BAD_MODE`.
- `422 QTY_UNITS_REQUIRED`.
- `422 NOTIONAL_REQUIRED`.
- `422 LEVERAGE_REQUIRED`.
- `422 INVALID_TAKE_PROFIT`.
- `422 INVALID_STOP_LOSS`.
- `409 SLIPPAGE_EXCEEDED`.
- `409 DUPLICATE_ORDER`.
- `400 INSUFFICIENT_FREE_MARGIN`.
- `503 PRICE_UNAVAILABLE`.
- `503 PRICE_STALE`.

Ví dụ thiếu margin:

```json
{
  "status": 400,
  "code": "INSUFFICIENT_FREE_MARGIN",
  "message": "Insufficient free margin.",
  "error": "INSUFFICIENT_FREE_MARGIN",
  "details": {
    "free": 20.0,
    "required": 65.0,
    "notional": 650.0,
    "leverage": 10,
    "units": 0.01,
    "mark": 65000.0
  }
}
```

### 7.8. POST /api/v1/orders/{id}/close

Auth: bắt buộc.

Path param:

| Param | Type | Ý nghĩa |
|---|---|---|
| `id` | UUID/string | ID order/position cần đóng |

Body: không có cho MVP.

Business flow:

1. Xác thực user.
2. Lấy server mark mới nhất.
3. Reject nếu mark stale/unavailable.
4. Bắt đầu transaction.
5. Lock account.
6. Lock order bằng pessimistic lock hoặc conditional update.
7. Kiểm tra order thuộc account hiện tại.
8. Kiểm tra status đang OPEN.
9. Tính realized PnL và fee.
10. Cập nhật status CLOSED, close reason MANUAL, close price/time.
11. Cập nhật account balance.
12. Insert account ledger.
13. Commit.
14. Publish `order:closed` sau commit.

PnL:

```text
BUY:  realizedPnl = (closePrice - entryPrice) * quantity - fees
SELL: realizedPnl = (entryPrice - closePrice) * quantity - fees
```

Success: `200 OK`

```json
{
  "ok": true,
  "order": {
    "id": "b177a8dd-3b97-43ae-a956-94c1a52bfed0",
    "symbol": "BTCUSDT",
    "side": "BUY",
    "volume": 0.01,
    "entry": 65000.0,
    "leverage": 10,
    "status": "CLOSED",
    "closePrice": 65100.0,
    "realizedPnl": 1.0,
    "closedAt": "2026-07-15T10:20:30.123Z",
    "take_profit": 70000.0,
    "stop_loss": 62000.0
  },
  "account": {
    "balance": 5001.0,
    "equity": 5001.0,
    "freeMargin": 5001.0,
    "marginUsed": 0.0
  }
}
```

Errors:

- `400 MISSING_ID`.
- `404 ORDER_NOT_FOUND`.
- `409 ORDER_NOT_OPEN`.
- `503 PRICE_UNAVAILABLE`.
- `503 PRICE_STALE`.

Để chống hai request close đồng thời, update có thể dùng:

```sql
UPDATE orders
SET status = 'CLOSED', ...
WHERE id = :id
  AND account_id = :accountId
  AND status = 'OPEN';
```

Nếu affected row bằng 0, đọc lại để phân biệt not found và already closed.

### 7.9. GET /api/v1/candles

Auth: public.

Query:

| Param | Required | Default | Ý nghĩa |
|---|---:|---|---|
| `asset` | No | BTC | BTC, BTCUSD hoặc BTCUSDT đều normalize thành BTCUSDT |
| `ts` | No | 1m | 1m, 5m, 15m, 1h, 4h, 1d, 1w |
| `startTime` | No | Theo timeframe | Unix seconds hoặc milliseconds |
| `endTime` | No | now | Unix seconds hoặc milliseconds |

Validation:

- startTime < endTime.
- Khoảng thời gian không vượt giới hạn cấu hình.
- Timeframe phải nằm trong allowlist; không ghép raw query param vào SQL.
- Dùng parameterized query.

Success: `200 OK`

```json
{
  "candles": [
    {
      "timestamp": 1784100000,
      "open": 65000.0,
      "high": 65120.0,
      "low": 64980.0,
      "close": 65100.0,
      "volume": 12.345,
      "decimal": 4
    }
  ]
}
```

Errors:

- `400 INVALID_TIMEFRAME`.
- `400 INVALID_TIME_RANGE`.
- `400 RANGE_TOO_LARGE`.

Có thể dùng continuous aggregates cho 1m/5m/15m và `time_bucket` trực tiếp cho timeframe khác.

### 7.10. GET /api/v1/last

Auth: public.

Query tùy chọn:

- `symbols=BTCUSDT,ETHUSDT,SOLUSDT`.

Success: `200 OK`

```json
[
  {
    "asset": "BTCUSDT",
    "ts": "2026-07-15T10:15:30.123Z",
    "price": 65000.0,
    "quantity": 0.01
  },
  {
    "asset": "ETHUSDT",
    "ts": "2026-07-15T10:15:30.100Z",
    "price": 3500.0,
    "quantity": 0.5
  }
]
```

Ưu tiên đọc Redis latest-price cache. Nếu Redis miss, fallback PostgreSQL.

### 7.11. Demo reset API, chỉ môi trường development

Không public các endpoint reset hiện tại trong production.

Đề xuất:

```text
POST /api/v1/demo/reset
```

Auth: bắt buộc, chỉ reset account của JWT hiện tại.

Điều kiện:

- Chỉ enable khi profile `dev` hoặc `demo`.
- Close/cancel toàn bộ open position.
- Reset balance về 5000.
- Tạo ledger `DEMO_RESET`.
- Không xóa lịch sử ledger.

Success:

```json
{
  "ok": true,
  "balance": 5000.0
}
```

---

## 8. API nên bổ sung sau MVP

Các API này không bắt buộc để frontend hiện tại chạy nhưng nên có:

- `POST /api/v1/auth/refresh`: đổi refresh token lấy access token.
- `POST /api/v1/auth/logout`: revoke refresh token.
- `GET /api/v1/account/ledger`: lịch sử balance.
- `GET /api/v1/orders/{id}`: chi tiết order.
- `PATCH /api/v1/orders/{id}/protection`: cập nhật TP/SL.
- `GET /api/v1/instruments`: symbol, quantity step, price tick, max leverage.
- `GET /api/v1/health/market-data`: trạng thái Binance stream.

Nếu thêm limit order:

- Mở rộng `orderType=LIMIT`.
- Thêm `limitPrice`.
- Trạng thái PENDING/OPEN/CANCELLED/REJECTED.
- Thêm `DELETE /api/v1/orders/{id}` để cancel pending order.
- Tách order và position trong database.

---

## 9. WebSocket realtime

### 9.1. Endpoint

```text
ws://host/ws/
wss://host/ws/
```

Frontend hiện kết nối raw WebSocket, không dùng STOMP hoặc SockJS.

### 9.2. Public trade message

Nên gửi message tương thích cả hai implementation frontend đang có:

```json
{
  "type": "trade",
  "timestamp": 1784100000123,
  "ts": 1784100000123,
  "asset": "BTCUSDT",
  "symbol": "BTCUSDT",
  "price": 65000.25,
  "quantity": 0.015
}
```

Các field quan trọng với frontend:

- `asset` hoặc `symbol`.
- `price`.
- `timestamp` hoặc `ts`.

Timestamp nên là number milliseconds. Không nên gửi ISO string ở WebSocket trade vì frontend hiện ép `Number(timestamp)`.

### 9.3. Connection lifecycle

- Cho phép origin của frontend qua cấu hình.
- Ping client khoảng 30 giây nếu container hỗ trợ.
- Xóa session khi close/error.
- Không giữ reference session đã đóng.
- Không block market-data thread khi gửi cho client chậm.
- Có queue nhỏ hoặc bỏ tick cũ nếu client không theo kịp; giá mới nhất quan trọng hơn mọi tick trung gian.

### 9.4. Security cho private events

Không broadcast `order:placed`, `order:closed` hoặc alert của một user cho toàn bộ public WebSocket client.

Trong MVP:

- `/ws/` chỉ gửi public market trades.
- Sau khi frontend hỗ trợ, tạo private channel riêng, ví dụ `/ws/private`.
- Browser WebSocket không thể tự đặt Authorization header. Nên dùng one-time WebSocket ticket hoặc auth message ngay sau connect.
- Không nên đặt JWT dài hạn trong query string vì có thể bị ghi vào access log.

### 9.5. Redis channels

Có thể giữ:

- `trades`: public market trade.
- `orders:{userId}`: private user order event.
- `alerts:{userId}`: private TP/SL/liquidation alert.

Nếu chỉ chạy một Spring Boot instance, internal application event có thể broadcast trực tiếp. Redis Pub/Sub vẫn hữu ích khi scale nhiều instance.

---

## 10. Market-data ingestion từ Binance

### 10.1. Symbols

MVP:

- BTCUSDT.
- ETHUSDT.
- SOLUSDT.

Có thể giữ BNBUSDT nếu muốn nhưng frontend hiện không hiển thị BNB.

### 10.2. Binance stream

Kết nối combined futures aggregate trade stream:

```text
wss://fstream.binance.com/stream?streams=
btcusdt@aggTrade/
ethusdt@aggTrade/
solusdt@aggTrade
```

Các field Binance cần đọc:

- `a`: aggregate trade ID.
- `s`: symbol.
- `p`: price.
- `q`: quantity.
- `T`: trade timestamp.

### 10.3. Reconnect

Client phải:

- Reconnect với exponential backoff.
- Có jitter để nhiều instance không reconnect cùng lúc.
- Reset backoff sau khi connection ổn định.
- Theo dõi thời điểm tick cuối.
- Mark stream unhealthy nếu không có tick quá ngưỡng.
- Đóng kết nối graceful khi application shutdown.

Ví dụ backoff: 1s, 2s, 4s, 8s, tối đa 30s.

### 10.4. Xử lý mỗi tick

1. Parse và validate JSON.
2. Normalize symbol.
3. Parse price/quantity bằng BigDecimal.
4. Reject price <= 0 hoặc quantity <= 0.
5. Update Redis:
    - `price:last:{SYMBOL}`.
    - `price:last:{SYMBOL}:ts`.
6. Publish public trade message.
7. Thêm tick vào batch database.
8. Gọi SL/TP/liquidation processing theo event hoặc queue nội bộ.

### 10.5. Batch insert

- Flush khi đủ 100 rows hoặc mỗi 1 giây.
- Dùng `JdbcTemplate.batchUpdate`, PostgreSQL COPY hoặc multi-value insert.
- Nếu insert lỗi, không được mất batch mà phải retry có giới hạn.
- Dedupe bằng source trade ID.
- Không log từng SQL/tick ở production.

### 10.6. Price freshness

`MarketPriceService` nên trả:

```java
public record MarketPrice(
    String symbol,
    BigDecimal price,
    Instant timestamp,
    PriceSource source
) {}
```

Quy tắc:

- Order execution chỉ chấp nhận giá không quá 5 giây.
- Account snapshot có open position phải báo lỗi/đánh dấu degraded nếu thiếu giá.
- Redis miss thì fallback database.
- Không fallback sang client price để execute.

---

## 11. Trading và risk engine

### 11.1. Công thức chuẩn

Với quantity là base units:

```text
notional = quantity * markPrice
initialMargin = notional / leverage

BUY uPnL  = (markPrice - entryPrice) * quantity
SELL uPnL = (entryPrice - markPrice) * quantity

equity = balance + sum(openPosition.uPnL)
usedMargin = sum(openPosition.initialMargin)
freeMargin = equity - usedMargin

maintenanceMargin =
    sum(abs(quantity * markPrice) * maintenanceMarginRate)

marginLevelPercent =
    usedMargin > 0 ? equity / usedMargin * 100 : null
```

Phải thống nhất một công thức duy nhất. Code Node cũ có nhiều risk engine với công thức khác nhau; không mang tất cả sang Spring Boot.

### 11.2. Mở position

Kiểm tra:

- Symbol supported.
- Mark tồn tại và còn fresh.
- Quantity đúng min/step.
- Leverage trong giới hạn.
- TP/SL hợp lệ.
- Free margin đủ.
- Account active.
- Không vượt maximum open positions.
- Idempotency key chưa được xử lý.

Nên cấu hình:

- Maximum 100 open positions/user.
- Maximum notional/order.
- Maximum aggregate notional/account.
- Maximum leverage/symbol.
- Taker fee rate.
- Maintenance margin rate.

### 11.3. Đóng position thủ công

- Dùng server mark.
- Chỉ OPEN mới được close.
- Update order và balance trong cùng transaction.
- PnL chỉ được realize một lần.
- Event publish sau commit.

### 11.4. Take-profit và stop-loss

Trigger:

- BUY TP: `lastPrice >= takeProfit`.
- BUY SL: `lastPrice <= stopLoss`.
- SELL TP: `lastPrice <= takeProfit`.
- SELL SL: `lastPrice >= stopLoss`.

Nếu một tick đồng thời vượt cả TP và SL do dữ liệu/gap:

- Chọn quy tắc rõ ràng.
- Để tương thích code hiện tại có thể ưu tiên TP.
- Production thường cần mô hình execution/gap chính xác hơn; MVP có thể dùng tick hiện tại làm close price.

Idempotency:

- Query/update chỉ khi status OPEN.
- Có thể dùng distributed lock `lock:close:{orderId}`, nhưng database conditional update vẫn là hàng rào cuối cùng.
- Manual close, SL/TP và liquidation có thể chạy đồng thời; chỉ một luồng được thắng.

### 11.5. Liquidation

Khuyến nghị isolated liquidation:

```text
positionEquity = initialMargin + unrealizedPnl
maintenance = currentNotional * maintenanceMarginRate

liquidate khi:
positionEquity <= maintenance + liquidationFeeReserve
```

Khi liquidation:

1. Lock order và account.
2. Xác nhận order vẫn OPEN.
3. Recompute mark, PnL và maintenance.
4. Nếu vẫn đủ điều kiện, close với status LIQUIDATED.
5. Cap loss theo quy tắc demo đã chọn.
6. Update balance.
7. Insert ledger loại LIQUIDATION.
8. Publish private alert sau commit.

Không chạy vòng lặp query toàn bộ orders mỗi giây nếu dữ liệu lớn. Nên xử lý theo symbol khi có tick và chỉ query open positions của symbol đó.

### 11.6. Fee

Nếu chưa mô phỏng phí:

- Đặt maker/taker fee bằng 0 trong config.
- Vẫn giữ column `trading_fee` để không phải đổi schema sau.

Nếu bật:

```text
openingFee = entryNotional * takerFeeRate
closingFee = closeNotional * takerFeeRate
realizedPnlNet = grossPnl - openingFee - closingFee
```

Phải quyết định opening fee trừ balance lúc mở hay cộng dồn và trừ khi đóng; ledger phải phản ánh đúng quyết định.

---

## 12. Transaction và concurrency

Đây là phần bắt buộc, không phải tối ưu tùy chọn.

### 12.1. Lock account

Repository có thể dùng:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select a
    from TradingAccountEntity a
    where a.id = :id
""")
Optional<TradingAccountEntity> findByIdForUpdate(Long id);
```

Hoặc native `SELECT ... FOR UPDATE`.

### 12.2. Transaction boundary

Các method cần `@Transactional`:

- Signup tạo user/account/ledger.
- Place order.
- Manual close.
- TP close.
- SL close.
- Liquidation.
- Demo reset.

Không giữ database transaction mở trong lúc gọi Binance hoặc Redis.

Trình tự đúng:

1. Lấy market price trước transaction.
2. Mở transaction.
3. Lock/validate/update database.
4. Commit.
5. Publish Redis/WebSocket.

Nếu cần đảm bảo event tuyệt đối, dùng outbox thay vì publish trực tiếp.

### 12.3. Isolation và retry

- `READ_COMMITTED` cộng row lock thường đủ cho MVP.
- Retry transaction khi deadlock/lock timeout với số lần giới hạn.
- Luôn lock resource theo cùng thứ tự: account trước, order sau.

### 12.4. Idempotency

Place order:

- Frontend gửi `Idempotency-Key` hoặc `clientOrderId`.
- Unique constraint trong database.
- Request lặp lại trả cùng response/order thay vì tạo order mới.

Close:

- Conditional update status OPEN.
- Request lặp lại có thể trả 409 ORDER_NOT_OPEN hoặc trả order đã đóng theo policy đã chọn.

---

## 13. Spring Security

### 13.1. SecurityFilterChain

Public:

- `POST /api/v1/signup`.
- `POST /api/v1/signin`.
- `GET /api/v1/candles`.
- `GET /api/v1/last`.
- `/ws/**` public market data.
- `/actuator/health` tùy môi trường.

Authenticated:

- `/api/v1/account/**`.
- `/api/v1/positions/**`.
- `/api/v1/orders/**`.
- `/api/v1/verify`.

Config:

- Stateless session.
- Disable form login.
- Disable HTTP Basic.
- CSRF có thể disable cho stateless Bearer REST API.
- CORS dùng allowlist, không dùng wildcard trong production.
- Trả JSON 401/403, không redirect HTML.

### 13.2. JWT signing

Ưu tiên asymmetric RSA/EC key pair:

- Private key chỉ auth server dùng để sign.
- Public key dùng verify.
- Không commit key vào Git.

Nếu MVP dùng HMAC:

- Secret tối thiểu 256 bits.
- Lấy từ environment/secret manager.
- Không dùng default secret.

### 13.3. Password

- BCrypt strength 10-12.
- Không log password.
- Không phân biệt lỗi email/password ở signin.
- Rate limit signin/signup.

### 13.4. Ownership

Không nhận `userId` từ body/path cho các API trading.

User ID luôn lấy từ JWT. Mọi query order phải có cả `orderId` và `accountId/userId`.

---

## 14. Validation và exception handling

### 14.1. DTO validation

Ví dụ:

```java
public record PlaceOrderRequest(
    @NotBlank String symbol,
    @NotNull OrderSide side,
    @NotNull SizingMode mode,
    @DecimalMin(value = "0.0", inclusive = false) BigDecimal qtyUnits,
    @DecimalMin(value = "0.0", inclusive = false) BigDecimal notionalUsd,
    @NotNull @Min(1) @Max(100) Integer leverage,
    @DecimalMin(value = "0.0", inclusive = false) BigDecimal tp,
    @DecimalMin(value = "0.0", inclusive = false) BigDecimal sl,
    @DecimalMin(value = "0.0", inclusive = false) BigDecimal clientMark,
    Long clientTs,
    @Min(0) @Max(100) Integer maxSlippageBps
) {}
```

Cross-field validation phải làm trong custom validator hoặc service:

- UNITS cần qtyUnits.
- NOTIONAL cần notionalUsd.
- TP/SL phụ thuộc side và mark.

### 14.2. GlobalExceptionHandler

Map:

- `MethodArgumentNotValidException` -> 422 VALIDATION_ERROR.
- `BadCredentialsException` -> 401 INVALID_CREDENTIALS.
- `JwtException` -> 401 INVALID_TOKEN.
- Business exception -> status/code cụ thể.
- Optimistic lock -> 409 CONCURRENT_MODIFICATION.
- Database unexpected -> 500 INTERNAL_ERROR.

Không trả stack trace, SQL, class name hoặc raw exception message cho client.

---

## 15. Redis design

### 15.1. Keys

```text
price:last:BTCUSDT
price:last:BTCUSDT:ts
price:last:ETHUSDT
price:last:ETHUSDT:ts
price:last:SOLUSDT
price:last:SOLUSDT:ts

lock:close:{orderId}
market:binance:last-message-ts
market:binance:status
```

Không lưu canonical user balance/order JSON trong Redis.

### 15.2. TTL

- Latest price có thể TTL 30-60 giây.
- Close lock TTL 3-10 giây.
- WebSocket ticket TTL 30-60 giây.

Ứng dụng vẫn phải kiểm tra timestamp riêng, không chỉ dựa vào Redis TTL.

### 15.3. Redis unavailable

- REST auth/account/order history vẫn có thể chạy từ PostgreSQL.
- Không cho mở/đóng position nếu không có fresh server price.
- Market-data có thể fallback latest DB price nhưng phải kiểm tra độ mới.
- Health endpoint báo degraded.

---

### 15.4. Messaging architecture — phân biệt cache, channel và queue

Trong project này, Redis và queue có ba vai trò khác nhau:

| Thành phần | Mục đích | Có cần giữ message không? | Ví dụ |
|---|---|---:|---|
| Redis key/cache | Lấy nhanh state mới nhất | Không cần lịch sử | Latest BTC price |
| Redis Pub/Sub | Broadcast realtime | Không, subscriber offline sẽ mất message | Public trade, private alert |
| Queue/Redis Stream | Xử lý bất đồng bộ và retry | Có | Trade writer, risk worker |
| PostgreSQL/outbox | Nguồn sự thật cho business event | Có | Order placed/closed |
| WebSocket | Gửi dữ liệu tới browser | Không phải persistence | `/ws/` |

Không dùng Redis Pub/Sub làm queue cho các tác vụ bắt buộc phải xử lý. Pub/Sub không có acknowledgement, không replay và không giữ message cho consumer offline.

Flow tổng quát:

```text
Binance WebSocket
    │
    ▼
MarketDataClient
    │ parse/validate/normalize
    ▼
MarketTick ingress queue
    │
    ├──> Update Redis latest-price cache
    ├──> Persist trades batch vào PostgreSQL/TimescaleDB
    ├──> Publish public market event
    └──> Send tick to TP/SL/liquidation risk worker

Order transaction
    │ update order/account/ledger + outbox trong cùng transaction
    ▼
OutboxPublisher
    │
    ├──> Redis private user channel
    └──> Private WebSocket session
```

#### 15.5. Canonical channels của project

Dùng version trong tên channel để sau này thay đổi payload mà không phá consumer cũ.

| Channel | Producer | Consumer | Dữ liệu |
|---|---|---|---|
| `pubsub:market:trades:v1` | Market tick processor | Public WebSocket subscriber | Trade mới nhất cho mọi client |
| `pubsub:market:status:v1` | Binance connection manager | Health/monitoring | Connected, reconnecting, unhealthy |
| `pubsub:orders:user:{userId}:v1` | Outbox publisher | Private WebSocket gateway | Order placed/closed của một user |
| `pubsub:alerts:user:{userId}:v1` | Risk/order service via outbox | Private WebSocket gateway | TP, SL, liquidation alert |
| `pubsub:account:user:{userId}:v1` | Account/order service via outbox | Private WebSocket gateway | Account snapshot changed, nếu cần |

Mapping với tên ngắn trong phần thiết kế cũ:

```text
trades             -> pubsub:market:trades:v1
orders:{userId}    -> pubsub:orders:user:{userId}:v1
alerts:{userId}    -> pubsub:alerts:user:{userId}:v1
```

Không publish private event vào `pubsub:market:trades:v1`.

#### 15.6. Redis key contract

##### Latest price

Dùng hai String key để đọc đơn giản và tương thích với `MarketPriceService`:

```text
price:last:BTCUSDT       -> "65000.250000000000"
price:last:BTCUSDT:ts    -> "1784100000123"

price:last:ETHUSDT       -> "3500.120000000000"
price:last:ETHUSDT:ts    -> "1784100000123"

price:last:SOLUSDT       -> "150.450000000000"
price:last:SOLUSDT:ts    -> "1784100000123"
```

Write cùng một tick:

```text
SET price:last:{SYMBOL} value EX 60
SET price:last:{SYMBOL}:ts epochMillis EX 60
```

Khi đọc:

1. Đọc price và timestamp.
2. Parse price bằng `BigDecimal`.
3. Tính `now - timestamp`.
4. Chỉ chấp nhận nếu age không vượt `trading.max-price-age`.
5. Redis miss hoặc stale thì fallback query database.
6. Không dùng `clientMark` để thay thế server price.

##### Lock keys

```text
lock:close:{orderId}
lock:market:leader
lock:outbox:publisher:{shard}
```

| Key | TTL | Mục đích |
|---|---:|---|
| `lock:close:{orderId}` | 5 seconds | Tránh manual close và TP/SL cùng xử lý |
| `lock:market:leader` | 10 seconds | Một instance giữ Binance connection khi scale |
| `lock:outbox:publisher:{shard}` | 30 seconds | Tránh nhiều publisher đọc cùng shard nếu cần |

Redis lock chỉ là lớp tối ưu. Database conditional update vẫn là hàng rào cuối cùng.

##### Health keys

```text
market:binance:status       -> CONNECTED/RECONNECTING/UNHEALTHY
market:binance:last-message-ts -> epochMillis
market:binance:instance     -> instance identifier
```

Không lưu canonical `balance`, `OrdersEntity` hoặc account state trong Redis. PostgreSQL là nguồn sự thật.

#### 15.7. Event envelope dùng cho mọi channel

Mọi event nên có envelope thống nhất:

```json
{
  "eventId": "01JABC...",
  "schemaVersion": 1,
  "type": "market.trade",
  "occurredAt": "2026-08-09T10:15:30.123Z",
  "aggregateType": "market",
  "aggregateId": "BTCUSDT",
  "userId": null,
  "payload": {}
}
```

Quy tắc:

- `eventId` unique để log và deduplicate.
- `schemaVersion` là integer.
- `type` là constant, không lấy từ input client.
- `occurredAt` là server event time ISO-8601.
- `aggregateId` là symbol/order ID/user ID tùy event.
- Event public phải có `userId = null`.
- Domain money/PnL nên serialize thành string để tránh mất precision.
- Public trade payload giữ `price`, `quantity`, `timestamp` dạng number nếu frontend hiện tại yêu cầu number.

#### 15.8. Public market trade event

Channel:

```text
pubsub:market:trades:v1
```

Payload Redis/domain:

```json
{
  "eventId": "trade-event-001",
  "schemaVersion": 1,
  "type": "market.trade",
  "occurredAt": "2026-08-09T10:15:30.123Z",
  "aggregateType": "market",
  "aggregateId": "BTCUSDT",
  "userId": null,
  "payload": {
    "type": "trade",
    "timestamp": 1784100000123,
    "ts": 1784100000123,
    "asset": "BTCUSDT",
    "symbol": "BTCUSDT",
    "price": 65000.25,
    "quantity": 0.015
  }
}
```

Flow:

```text
Binance aggTrade
    -> parse p/q/T/a/s
    -> validate price and quantity
    -> normalize symbol
    -> create MarketTick
    -> update latest-price cache
    -> publish pubsub:market:trades:v1
    -> WebSocket subscriber forwards payload to public sessions
```

Public clients chỉ nhận market data. Không gửi email, balance, account ID, order ID hoặc JWT claim vào channel này.

#### 15.9. Private order event

Channel:

```text
pubsub:orders:user:{userId}:v1
```

Payload khi đặt order:

```json
{
  "eventId": "order-event-001",
  "schemaVersion": 1,
  "type": "order.placed",
  "occurredAt": "2026-08-09T10:15:30.123Z",
  "aggregateType": "order",
  "aggregateId": "101",
  "userId": 7,
  "payload": {
    "orderId": "101",
    "accountId": "11",
    "symbol": "BTCUSDT",
    "side": "BUY",
    "status": "OPEN",
    "quantity": "0.010000000000",
    "entryPrice": "65000.250000000000",
    "notional": "650.002500000000",
    "initialMargin": "65.000250000000",
    "leverage": 10,
    "takeProfit": "70000.000000000000",
    "stopLoss": "62000.000000000000",
    "openedAt": "2026-08-09T10:15:30.123Z"
  }
}
```

Payload khi close:

```json
{
  "eventId": "order-event-002",
  "schemaVersion": 1,
  "type": "order.closed",
  "occurredAt": "2026-08-09T10:20:30.123Z",
  "aggregateType": "order",
  "aggregateId": "101",
  "userId": 7,
  "payload": {
    "orderId": "101",
    "accountId": "11",
    "symbol": "BTCUSDT",
    "status": "CLOSED",
    "closeReason": "MANUAL",
    "closePrice": "65100.000000000000",
    "realizedPnl": "1.00000000",
    "tradingFee": "0.00000000",
    "closedAt": "2026-08-09T10:20:30.123Z"
  }
}
```

Chỉ publish tới channel của đúng `userId`. Không dùng một channel chung chứa private events của mọi user.

#### 15.10. Private risk alert event

Channel:

```text
pubsub:alerts:user:{userId}:v1
```

Payload TP/SL/liquidation:

```json
{
  "eventId": "alert-001",
  "schemaVersion": 1,
  "type": "risk.liquidation",
  "occurredAt": "2026-08-09T10:20:30.123Z",
  "aggregateType": "order",
  "aggregateId": "101",
  "userId": 7,
  "payload": {
    "orderId": "101",
    "symbol": "BTCUSDT",
    "reason": "LIQUIDATION",
    "triggerPrice": "60000.000000000000",
    "realizedPnl": "-65.00000000",
    "message": "Position was liquidated"
  }
}
```

Alert không tự thay thế order state. Client vẫn phải gọi REST `/orders` hoặc `/account` để đồng bộ lại state nếu reconnect sau khi mất WebSocket.

#### 15.11. Internal market tick queue

##### MVP một instance

Dùng queue bounded trong memory để tách Binance network thread khỏi database/WebSocket/risk processing:

```text
Binance WebSocket callback
    -> marketTickIngressQueue.put(tick)

MarketTickWorker
    -> take tick
    -> update cache
    -> persist batch
    -> publish public event
    -> submit risk evaluation
```

Data structure đề xuất:

```java
BlockingQueue<MarketTick> marketTickIngressQueue =
        new ArrayBlockingQueue<>(10_000);
```

`MarketTick`:

```text
eventId
symbol
price: BigDecimal
quantity: BigDecimal
sourceTradeId: Long
eventTime: Instant
receivedAt: Instant
source: BINANCE
```

Không để Binance callback gọi trực tiếp database hoặc `session.sendMessage()`. Callback chỉ parse, validate cơ bản và đưa tick vào queue.

##### Backpressure

Tách hai loại queue:

```text
riskQueue / persistenceQueue
    -> không được drop âm thầm
    -> block, retry hoặc chuyển sang durable stream

publicWebSocketQueue mỗi session
    -> có thể drop tick cũ
    -> ưu tiên giá mới nhất
```

Nếu `riskQueue` đầy:

1. Mark market-data health là degraded.
2. Không tiếp tục giả định risk evaluation đã xử lý đủ tick.
3. Retry hoặc chuyển tick sang Redis Stream.
4. Không silently discard tick cho liquidation.

##### Scale nhiều instance: Redis Streams

Khi chạy nhiều backend instance, thay queue nội bộ bằng:

```text
stream:market:ticks:v1
```

Consumer groups:

| Consumer group | Nhiệm vụ | Ack khi nào |
|---|---|---|
| `market-trade-writer` | Batch insert `trades` | Sau khi database batch commit |
| `market-risk-engine` | TP/SL/liquidation | Sau khi risk transaction commit |
| `market-public-broadcaster` | Publish public trade channel | Sau khi Redis publish thành công |

Mỗi group nhận một bản độc lập của stream entry. Các instance trong cùng group chia nhau xử lý để scale.

Stream entry:

```text
XADD stream:market:ticks:v1 *
  eventId trade-event-001
  symbol BTCUSDT
  price 65000.25
  quantity 0.015
  sourceTradeId 123456
  eventTime 1784100000123
```

Consumer phải:

1. `XREADGROUP` với consumer name duy nhất.
2. Parse message.
3. Validate schema.
4. Xử lý idempotent theo `sourceTradeId`/`eventId`.
5. `XACK` sau khi xử lý thành công.
6. Claim pending message nếu consumer chết.
7. Retry giới hạn.
8. Đưa message lỗi vào `stream:dead-letter:v1`.

Redis Streams là queue durable hơn Pub/Sub, nhưng PostgreSQL vẫn là nguồn sự thật cho order/balance/ledger.

#### 15.12. Market tick processing chi tiết

```text
1. BinanceMessageListener nhận raw JSON.
2. Parse field:
   a -> sourceTradeId
   s -> symbol
   p -> price
   q -> quantity
   T -> eventTime
3. Normalize symbol sang SymbolEnum.
4. Parse price/quantity bằng BigDecimal.
5. Reject price <= 0 hoặc quantity <= 0.
6. Tạo eventId deterministic hoặc UUID.
7. Đưa MarketTick vào ingress queue/stream.
8. MarketTickWorker xử lý:
   a. Update price:last:{SYMBOL} và timestamp atomically.
   b. Add tick vào batch writer.
   c. Publish market event.
   d. Submit risk event theo symbol.
9. Batch writer insert TradesEntity.
10. Risk engine query OPEN orders theo symbol.
11. TP/SL/liquidation dùng tick price.
```

Nếu database insert bị duplicate `sourceTradeId`, coi đó là duplicate delivery và ack message; không tạo thêm trade.

#### 15.13. Outbox cho order/account events

Redis Pub/Sub không bảo đảm event sau commit. Khi order transaction cần phát event đáng tin cậy:

```text
BEGIN TRANSACTION
    update orders
    update trading_accounts
    insert account_ledgers
    insert outbox_events
COMMIT

OutboxPublisher
    -> read unpublished outbox row
    -> publish Redis private channel
    -> set published_at
```

`outbox_events` fields:

```text
id: UUID
aggregate_type: ORDER/ACCOUNT
aggregate_id: String
event_type: order.placed/order.closed/account.updated
user_id: Long
payload: JSONB
created_at: TIMESTAMPTZ
published_at: TIMESTAMPTZ nullable
retry_count: integer
last_error: text nullable
```

Outbox publisher flow:

```text
1. Select unpublished rows ordered by created_at.
2. Lock a small batch with SKIP LOCKED nếu có nhiều publisher.
3. Publish tới channel theo event_type/user_id.
4. Nếu publish thành công, set published_at.
5. Nếu fail, tăng retry_count và giữ lại row.
6. Sau max retry, chuyển sang dead-letter/alert ops.
```

Không đánh dấu `published_at` trước khi `RedisTemplate.convertAndSend` thành công.

#### 15.14. WebSocket server flow

##### Public endpoint

```text
GET /ws/
```

Client lifecycle:

```text
Browser connects /ws/
    -> WebSocketHandler.afterConnectionEstablished
    -> register session
    -> subscribe session to public market broadcaster
    -> receive market.trade events
    -> ping/pong heartbeat
    -> unregister on close/error
```

Public session registry:

```text
ConcurrentHashMap<String, WebSocketSession>
```

Không giữ session đã closed. Kiểm tra `session.isOpen()` trước send.

##### Public broadcast

```text
Redis subscriber nhận pubsub:market:trades:v1
    -> deserialize envelope
    -> lấy envelope.payload
    -> enqueue cho từng public WebSocket session
    -> session send async/non-blocking
```

Nếu một browser chậm:

- Không block Redis subscriber thread.
- Có bounded outbound queue cho session.
- Drop trade cũ khi queue đầy.
- Giữ trade mới nhất.
- Đóng session nếu client liên tục không đọc được.

##### Private endpoint sau MVP

Không dùng JWT dài hạn trong query string:

```text
Không nên: /ws/private?token=<long-lived-jwt>
```

Flow đề xuất:

```text
1. Browser gọi REST tạo one-time WebSocket ticket.
2. Server lưu hash ticket vào Redis:
   ws:ticket:{hash} -> userId, TTL 60s
3. Browser connect /ws/private.
4. Browser gửi auth message chứa one-time ticket.
5. Server consume/delete ticket atomically.
6. Server đăng ký session vào userId.
7. Subscriber chỉ forward:
   pubsub:orders:user:{userId}:v1
   pubsub:alerts:user:{userId}:v1
8. Ticket dùng một lần và không được reuse.
```

MVP hiện tại chỉ cần `/ws/` public market stream. Chưa gửi private events qua public socket.

#### 15.15. Java components cần implement

```text
marketdata/
├── BinanceMarketDataClient
│   └── WebSocket listener, reconnect, parse raw Binance messages
├── MarketTick
├── MarketTickIngressQueue
├── MarketTickWorker
├── MarketPriceService
├── LatestPriceCache
│   └── StringRedisTemplate read/write price and timestamp
├── TradeBatchWriter
│   └── batch insert TradesEntity
└── RiskTickPublisher

realtime/
├── WebSocketConfig
├── MarketWebSocketHandler
├── WebSocketSessionRegistry
├── RedisMarketSubscriber
├── PrivateEventSubscriber
└── WebSocketOutboundQueue

messaging/
├── RedisChannelNames
├── EventEnvelope
├── RedisPublisher
├── RedisStreamConsumer
├── OutboxPublisher
└── DeadLetterPublisher
```

Trách nhiệm:

| Component | Không được làm |
|---|---|
| Binance client | Không ghi trực tiếp order/account |
| Redis cache | Không là nguồn sự thật của balance/order |
| Market tick worker | Không giữ transaction dài khi gọi external service |
| WebSocket handler | Không tự query toàn bộ database mỗi tick |
| Redis subscriber | Không block vì một browser chậm |
| Risk engine | Không bỏ qua conditional update khi close |
| Outbox publisher | Không đánh dấu published trước khi publish thành công |

#### 15.16. Redis Java configuration blueprint

Dependencies cần thêm nếu chưa có:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

Cho MVP dùng `StringRedisTemplate` để key/value và JSON payload dễ debug:

```java
@Bean
RedisTemplate<String, String> redisTemplate(
        RedisConnectionFactory connectionFactory
) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new StringRedisSerializer());
    template.afterPropertiesSet();
    return template;
}
```

Publisher contract:

```java
public void publish(String channel, EventEnvelope event) {
    String json = objectMapper.writeValueAsString(event);
    stringRedisTemplate.convertAndSend(channel, json);
}
```

Không serialize entity JPA trực tiếp vào event. Map entity sang event DTO để tránh lazy-loading, password/hash leakage và schema coupling.

#### 15.17. Failure, retry và delivery semantics

| Flow | Delivery | Retry strategy |
|---|---|---|
| Latest price cache | Best effort | Tick sau sẽ overwrite |
| Public trade Pub/Sub | At-most-once | Client reconnect gọi REST/latest endpoint |
| Trade persistence | At-least-once input | DB unique `(asset, source_trade_id)` |
| Risk processing | At-least-once | Conditional order state update |
| Private order event | At-least-once via outbox | Retry until published |
| WebSocket delivery | Best effort | Client refetch state after reconnect |

Không hứa exactly-once ở Redis/WebSocket. Đảm bảo business exactly-once bằng database constraint, transaction và conditional update.

#### 15.18. Channel và queue test checklist

```text
[ ] Binance reconnect không tạo duplicate sourceTradeId
[ ] Latest-price key có timestamp và stale check
[ ] Public trade message đúng schema và timestamp milliseconds
[ ] Public client không nhận private order event
[ ] Redis subscriber reconnect được
[ ] WebSocket close/error cleanup session
[ ] Slow WebSocket client không block market tick worker
[ ] Risk queue đầy không silently drop tick
[ ] Stream consumer ACK chỉ sau xử lý thành công
[ ] Pending stream message được claim sau consumer crash
[ ] Outbox event chỉ published_at sau Redis publish thành công
[ ] Failed outbox publish được retry
[ ] Manual close và TP/SL đồng thời chỉ một bên realize PnL
[ ] Redis unavailable làm health degraded và chặn execute order khi thiếu price
```

#### 15.19. Recommended implementation order

```text
1. WebSocketConfig + public MarketWebSocketHandler
2. WebSocketSessionRegistry + heartbeat/cleanup
3. Redis StringRedisTemplate latest-price cache
4. BinanceMarketDataClient + MarketTick parser
5. Bounded in-memory ingress queue
6. MarketTickWorker + trade batch writer
7. Redis public Pub/Sub publisher/subscriber
8. Public WebSocket broadcast
9. TP/SL risk queue and shared close service
10. OutboxEventsEntity + OutboxPublisher
11. Private order/alert channels
12. Redis Streams consumer groups when scaling instances
13. Dead-letter, metrics, health and load tests
```

---

## 16. Configuration

### 16.1. application.yml

```yaml
server:
  port: 8081
  shutdown: graceful

spring:
  application:
    name: crypto-trading-server

  datasource:
    url: jdbc:postgresql://postgres:5432/xness
    username: app
    password: change-me
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC

  flyway:
    enabled: true

  data:
    redis:
      host: redis
      port: 6379
      timeout: 2s
      repositories:
        enabled: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

trading:
  demo-starting-balance: 5000
  supported-symbols:
    - BTCUSDT
    - ETHUSDT
    - SOLUSDT
  max-leverage: 100
  maintenance-margin-rate: 0.005
  taker-fee-rate: 0
  max-price-age: 5s
  default-max-slippage-bps: 5
  max-open-positions: 100

market-data:
  binance-url: wss://fstream.binance.com/stream
  batch-size: 100
  flush-interval: 1s
  ingress-queue-capacity: 10000
  stream-key: stream:market:ticks:v1

websocket:
  public-path: /ws/
  allowed-origins: http://localhost:5173
  heartbeat-interval: 30s
  session-queue-capacity: 100
  private-enabled: false

messaging:
  public-trades-channel: pubsub:market:trades:v1
  market-status-channel: pubsub:market:status:v1
  order-channel-prefix: pubsub:orders:user:
  alert-channel-prefix: pubsub:alerts:user:
  outbox-poll-interval: 1s
  stream-max-retries: 5
```

Password, JWT key và database credentials phải đến từ environment/secret manager.

### 16.2. Profiles

- `dev`: local PostgreSQL/Redis, Swagger enabled, demo reset enabled.
- `test`: Testcontainers.
- `prod`: strict CORS, Swagger tùy policy, reset disabled, secrets bắt buộc.

---

## 17. Observability

### 17.1. Logging

Log theo structured fields:

- traceId.
- userId.
- accountId.
- orderId.
- symbol.
- event.
- duration.
- result code.

Không log:

- Password.
- JWT đầy đủ.
- Private key/secret.
- Toàn bộ request header.

### 17.2. Metrics

Nên có:

- REST request count/latency/error.
- Active WebSocket sessions.
- Binance reconnect count.
- Seconds since last Binance tick.
- Trade ticks received.
- DB batch insert duration/failures.
- Orders placed/rejected/closed/liquidated.
- SL/TP triggers.
- Redis publish failures.
- Database pool utilization.

### 17.3. Health

Health components:

- PostgreSQL.
- Redis.
- Binance stream freshness.
- Flyway/schema.

`/actuator/health/liveness` không nên fail chỉ vì Binance tạm mất.

`/actuator/health/readiness` có thể degraded/down nếu server không thể execute order do không có fresh price.

---

## 18. Testing bắt buộc

### 18.1. Unit tests

MarginCalculator:

- BUY/SELL PnL.
- Initial margin.
- Free margin.
- Maintenance margin.
- Rounding.
- Zero open position.

Order validation:

- Symbol invalid.
- Side invalid.
- Leverage 0/101.
- UNITS thiếu qty.
- NOTIONAL thiếu notional.
- TP/SL đúng và sai cho BUY/SELL.
- Slippage boundary.
- Stale price.

Liquidation:

- Chưa tới threshold.
- Đúng threshold.
- Vượt threshold.
- Balance không update hai lần.

### 18.2. Repository/integration tests

Dùng Testcontainers PostgreSQL với TimescaleDB image nếu test candle/hypertable:

- Flyway chạy thành công.
- Unique email.
- Unique idempotency key.
- Pessimistic lock.
- Conditional close chỉ update một lần.
- Candle query đúng bucket.
- Latest price query đúng.

### 18.3. API tests

- Signup -> signin -> verify.
- User A không đọc/đóng order của user B.
- Place order thành công.
- Place order thiếu margin.
- Place order khi price stale.
- Hai place request cùng idempotency key.
- Hai close request đồng thời.
- Account snapshot sau open/close.

### 18.4. WebSocket tests

- Connect `/ws/`.
- Nhận trade message đúng schema.
- Không nhận private order event của user khác.
- Session được cleanup sau disconnect.

### 18.5. End-to-end test

Luồng tối thiểu:

1. Signup.
2. Nhận 5000 balance.
3. Market price tồn tại.
4. Place BUY.
5. Position xuất hiện.
6. Account used/free thay đổi.
7. Price thay đổi làm uPnL thay đổi.
8. Close.
9. Balance nhận realized PnL.
10. Ledger có row tương ứng.

---

## 19. Docker và reverse proxy

Sau khi thay ba Node service bằng Spring Boot:

```text
services:
  postgres
  redis
  server
  client
```

Nginx:

```nginx
set $server_upstream http://server:8081;

location /api/ {
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_pass $server_upstream;
}

location /ws/ {
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "Upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 60s;
    proxy_pass $server_upstream;
}
```

Spring Boot phải chạy port 8081 để giảm thay đổi cấu hình frontend.

Docker healthcheck nên gọi `/actuator/health/readiness`.

---

## 20. Lộ trình hiện thực

### Phase 1: Scaffold và database

- [ ] Tạo module `server/` bằng Spring Boot.
- [ ] Thêm dependency.
- [ ] Cấu hình PostgreSQL, Redis, Flyway.
- [ ] Viết migration users/accounts/orders/ledger/trades.
- [ ] Tạo entity, enum và repository.
- [ ] Tắt Hibernate auto-create, dùng validate.

### Phase 2: Authentication

- [ ] SecurityFilterChain.
- [ ] BCrypt PasswordEncoder.
- [ ] JwtEncoder/JwtDecoder.
- [ ] Signup.
- [ ] Signin.
- [ ] Verify.
- [ ] JSON 401/403.
- [ ] Auth integration tests.

### Phase 3: Market data

- [ ] Binance WebSocket client.
- [ ] Reconnect/backoff.
- [ ] Redis latest price.
- [ ] Batch insert TimescaleDB.
- [ ] GET candles.
- [ ] GET last.
- [ ] Market-data health.

### Phase 4: Account và trading

- [ ] MarketPriceService.
- [ ] MarginCalculator.
- [ ] Account snapshot.
- [ ] GET account.
- [ ] GET positions/orders.
- [ ] POST orders.
- [ ] POST close.
- [ ] Ledger.
- [ ] Transaction locks.
- [ ] Idempotency.

### Phase 5: WebSocket

- [ ] Raw `/ws/` handler.
- [ ] Trade broadcast.
- [ ] Session lifecycle.
- [ ] Heartbeat.
- [ ] Không broadcast private order globally.

### Phase 6: Risk automation

- [ ] TP/SL watcher.
- [ ] Liquidation.
- [ ] Conditional close.
- [ ] Private alerts/event.
- [ ] Concurrency tests.

### Phase 7: Hardening

- [ ] OpenAPI.
- [ ] Rate limiting.
- [ ] Metrics.
- [ ] Structured logging.
- [ ] Docker image.
- [ ] Compose update.
- [ ] Backup/retention policy TimescaleDB.
- [ ] Load test WebSocket và order API.

---

## 21. Những lỗi thiết kế cần tránh

- Không dùng client price làm execution price.
- Không lưu balance/order chính trong Redis.
- Không dùng `double` cho tiền.
- Không cập nhật balance và order ở hai transaction khác nhau.
- Không publish private order event lên public WebSocket.
- Không query toàn bộ users/orders mỗi market tick.
- Không dùng JPA insert từng trade tick.
- Không dùng `ddl-auto=create/update` ở production.
- Không expose reset endpoint ở production.
- Không dùng JWT default secret.
- Không tin `userId` do client gửi.
- Không nuốt exception và vẫn trả dữ liệu account thiếu giá.
- Không có nhiều risk engine với công thức khác nhau.
- Không log từng tick/SQL ở mức INFO trong production.

---

## 22. Definition of Done cho backend MVP

Backend được xem là hoàn thành khi:

- Frontend đăng ký, đăng nhập và verify token được.
- User mới có đúng 5000 USDT demo balance.
- REST contract giữ đúng field frontend cần.
- Server nhận realtime Binance price cho ba symbol.
- Trade ticks được batch insert vào TimescaleDB.
- Candle API trả đúng timestamp/OHLC.
- WebSocket `/ws/` gửi trade message tương thích frontend.
- User mở market position với leverage 1..100.
- Server reject stale price, slippage và thiếu margin.
- Account snapshot tính đúng balance/equity/used/free/uPnL/maintenance.
- User chỉ xem và đóng position của chính mình.
- Manual close chỉ realize PnL một lần.
- TP/SL và liquidation không thể đóng trùng position.
- Mọi balance change có ledger.
- Các flow quan trọng có integration test.
- Docker Compose chỉ cần client, Spring Boot server, PostgreSQL/TimescaleDB và Redis.

---

## 23. Thứ tự class nên viết

Thứ tự thực tế giúp giảm việc phải sửa lại:

1. Enum và `TradingProperties`.
2. Flyway migrations.
3. User/TradingAccount/Order/Ledger entities.
4. Repository và locking query.
5. Error model và `GlobalExceptionHandler`.
6. Security/JWT.
7. Auth APIs.
8. MarketPriceService với implementation giả cho test.
9. MarginCalculator và AccountSnapshotService.
10. Account/position APIs.
11. PlaceOrderService.
12. CloseOrderService.
13. Binance client và Redis price cache.
14. Trade batch writer/candle APIs.
15. Public WebSocket.
16. TP/SL.
17. Liquidation.
18. Metrics, Docker và load tests.

Nếu tuân theo thứ tự này, trading service có thể được test bằng market price giả trước khi Binance WebSocket hoàn thành.
