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
