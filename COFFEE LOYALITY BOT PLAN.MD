# Coffee Loyalty Telegram Bot — План проєкту

## Мета

Замінити Loyallyst (1500 грн/міс) на власного Telegram-бота для програми лояльності кав'ярні.
Бот інтегрується з Poster POS, який зберігає клієнтську базу та бонусні баланси.

## Архітектура

```
[Клієнт у Telegram] <──> [Telegram Bot API]
                                │
                       [Spring Boot App]  <── webhook ── [Poster POS]
                                │                         (закриття чеку)
                          [PostgreSQL]
                       (зв'язка клієнт↔telegram,
                        лог нотифікацій)
```

**Принцип:** Poster — джерело правди (клієнти, бонуси, транзакції).
Наш сервіс — легка прослойка між Poster і Telegram.

## Стек технологій

- **Backend:** Java 17+ / Spring Boot 3.x
- **БД:** PostgreSQL
- **Telegram:** telegrambots-spring-boot-starter або прямі HTTP-виклики до Bot API
- **Barcode:** ZXing (com.google.zxing) для генерації QR/штрихкодів
- **Хостинг:** Railway.app (~$5/міс)
- **Poster API:** REST API v3 з OAuth

## Структура проєкту

```
coffee-loyalty-bot/
├── src/main/java/ua/example/loyaltybot/
│   ├── LoyaltyBotApplication.java
│   │
│   ├── config/
│   │   ├── TelegramBotConfig.java          # Bot token, webhook URL
│   │   ├── PosterApiConfig.java            # Poster credentials, base URL
│   │   ├── AdminConfig.java                # Admin chat IDs
│   │   └── SchedulingConfig.java           # @EnableScheduling для retry/polling
│   │
│   ├── controller/
│   │   ├── PosterWebhookController.java    # POST /api/webhook/poster
│   │   ├── TelegramWebhookController.java  # POST /api/webhook/telegram
│   │   └── HealthController.java           # GET /health, GET /health/ready
│   │
│   ├── service/
│   │   ├── PosterApiService.java           # HTTP-клієнт до Poster API
│   │   ├── TelegramBotService.java         # Відправка повідомлень у Telegram
│   │   ├── CustomerService.java            # Бізнес-логіка: реєстрація, зв'язка
│   │   ├── BarcodeService.java             # Генерація QR/штрихкодів
│   │   ├── BroadcastService.java           # Масові та індивідуальні розсилки
│   │   └── NotificationRetryService.java   # Повторна відправка failed повідомлень
│   │
│   ├── model/
│   │   ├── Customer.java                   # JPA entity
│   │   └── BonusNotification.java          # Лог нотифікацій
│   │
│   ├── repository/
│   │   ├── CustomerRepository.java
│   │   └── BonusNotificationRepository.java
│   │
│   └── dto/
│       ├── PosterWebhookPayload.java       # DTO для webhook від Poster
│       └── PosterClientDto.java            # DTO для відповіді Poster API
│
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__init.sql
│
├── pom.xml
├── Dockerfile                              # Для Railway деплою
├── Procfile                                # Альтернатива Dockerfile
└── README.md
```

## База даних

```sql
-- V1__init.sql

CREATE TABLE customers (
    id              BIGSERIAL PRIMARY KEY,
    phone           VARCHAR(20) UNIQUE NOT NULL,
    telegram_chat_id BIGINT UNIQUE,
    poster_client_id INTEGER UNIQUE,
    first_name      VARCHAR(100),
    status          VARCHAR(20) DEFAULT 'active',  -- active, blocked, unsubscribed
    registered_at   TIMESTAMP DEFAULT NOW(),
    last_purchase_at TIMESTAMP,
    total_purchases  INTEGER DEFAULT 0
);

CREATE INDEX idx_customers_status ON customers(status);

CREATE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_poster_id ON customers(poster_client_id);
CREATE INDEX idx_customers_chat_id ON customers(telegram_chat_id);

CREATE TABLE bonus_notifications (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT REFERENCES customers(id),
    order_amount    DECIMAL(10,2),
    bonus_accrued   DECIMAL(10,2),
    bonus_balance   DECIMAL(10,2),
    notified_at     TIMESTAMP DEFAULT NOW(),
    delivered       BOOLEAN DEFAULT FALSE,
    retry_count     INTEGER DEFAULT 0,
    last_error      TEXT
);

CREATE TABLE broadcast_jobs (
    id              BIGSERIAL PRIMARY KEY,
    admin_chat_id   BIGINT NOT NULL,
    message_text    TEXT NOT NULL,
    target_filter   VARCHAR(50),           -- 'all', 'inactive', 'top'
    filter_value    INTEGER,               -- днів неактивності або кількість покупок
    total_recipients INTEGER DEFAULT 0,
    sent_count      INTEGER DEFAULT 0,
    failed_count    INTEGER DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'pending', -- pending, in_progress, completed, cancelled
    created_at      TIMESTAMP DEFAULT NOW(),
    completed_at    TIMESTAMP
);

CREATE INDEX idx_broadcast_jobs_status ON broadcast_jobs(status);
```

## Poster API — необхідні ендпоінти

Базовий URL: `https://{account_name}.joinposter.com/api/`
Авторизація: `?token={access_token}` до кожного запиту

### Клієнти

```
GET  /clients.getClients     — список всіх клієнтів
GET  /clients.getClient      — один клієнт по client_id
POST /clients.createClient   — створити нового
POST /clients.changeClient   — оновити дані (включно з бонусами)
GET  /clients.findClients    — пошук по телефону, імені тощо
```

### Транзакції

```
GET  /transactions.getTransactions  — історія транзакцій
GET  /dash.getTransaction          — деталі конкретної транзакції
```

### Webhooks

Poster надсилає POST на зареєстровану URL при закритті чеку.
Налаштовується через адмінку Poster або API.

**Очікуваний payload від Poster:**
```json
{
  "account": "your_account",
  "object": "transaction",
  "object_id": 123,
  "action": "added",
  "data": {
    "client_id": 456,
    "sum": 12000,        // у копійках
    "bonus_accrued": 600 // у копійках
  },
  "time": "2026-02-26 14:30:00"
}
```

> ⚠️ Точний формат webhook потрібно перевірити в документації Poster
> (dev.joinposter.com — SPA, потрібно відкрити в браузері).
> Також перевірити: чи є webhook саме на закриття чеку з бонусною інформацією.
> Альтернатива: polling транзакцій через API кожні N хвилин.

## PosterApiService

```java
@Service
public class PosterApiService {

    private final RestTemplate restTemplate;
    private final String baseUrl;    // https://{account}.joinposter.com/api/
    private final String token;

    // Пошук клієнта по телефону
    public Optional<PosterClientDto> findClientByPhone(String phone) {
        // GET /clients.findClients?token={token}&phone={phone}
    }

    // Отримати клієнта по ID
    public PosterClientDto getClient(int clientId) {
        // GET /clients.getClient?token={token}&client_id={clientId}
    }

    // Створити нового клієнта
    public PosterClientDto createClient(String phone, String firstName) {
        // POST /clients.createClient
        // body: client_name, client_groups_id_client, phone
    }

    // Отримати бонусний баланс
    public BigDecimal getClientBonus(int clientId) {
        // GET /clients.getClient → поле bonus або loyalty_balance
    }

    // Нарахувати welcome-бонус
    public void addBonus(int clientId, BigDecimal amount) {
        // POST /clients.changeClient → оновити бонусний баланс
    }
}
```

## Telegram Bot — команди

### Клієнтські команди

| Команда     | Дія                                           |
|-------------|------------------------------------------------|
| `/start`    | Вітання + запит номера телефону                |
| `/balance`  | Поточний бонусний баланс з Poster              |
| `/card`     | QR-код для ідентифікації на касі               |
| `/help`     | Довідка                                        |

### Адмін-команди (тільки для chat_id адміна)

| Команда                             | Дія                                          |
|-------------------------------------|----------------------------------------------|
| `/broadcast <текст>`                | Розсилка всім клієнтам                       |
| `/broadcast_inactive <дні> <текст>` | Клієнтам, хто не заходив N+ днів             |
| `/broadcast_top <покупок> <текст>`  | Топ-клієнтам (N+ покупок)                    |
| `/send <телефон> <текст>`           | Індивідуальне повідомлення                    |
| `/stats`                            | Статистика: кількість клієнтів, активні тощо |

## Флоу

### Реєстрація клієнта

```
Клієнт натискає /start
  → Бот: "Вітаю! ☕ Натисніть кнопку щоб поділитися номером"
  → [Кнопка: 📱 Поділитися контактом]
  → Клієнт натискає кнопку (Telegram передає номер)
  →
  → Сервіс шукає в Poster по телефону:
     ├─ ЗНАЙДЕНО:
     │   → зберігаємо зв'язку poster_client_id ↔ telegram_chat_id
     │   → нараховуємо welcome-бонус (наприклад +20)
     │   → Бот: "Ми вас знайшли! У вас 84 бонуси + 20 за підключення = 104 ☕"
     │
     └─ НЕ ЗНАЙДЕНО:
         → створюємо нового клієнта в Poster
         → зберігаємо зв'язку
         → нараховуємо welcome-бонус
         → Бот: "Ласкаво просимо! Вам нараховано 20 стартових бонусів 🎁"
```

### Покупка (автоматичне повідомлення)

```
Клієнт купує каву → Бариста закриває чек у Poster
  → Poster надсилає webhook на наш сервер
  → Сервер:
     1. Отримує client_id, суму, бонуси з webhook
     2. Шукає customer по poster_client_id в нашій БД
     3. Якщо є telegram_chat_id → надсилає повідомлення:

        ☕ Дякуємо за каву!

        Сума: 120 грн
        Нараховано: +6 бонусів
        Ваш баланс: 110 бонусів

        Бонусами можна оплатити до 30% замовлення
```

### QR-код картки

```
Клієнт пише /card
  → Сервер генерує зображення:
     ┌─────────────────────────┐
     │    ☕ [Назва кав'ярні]   │
     │                         │
     │    Віталій               │
     │    Баланс: 110 бонусів   │
     │                         │
     │    [QR-код з client_id]  │
     │                         │
     └─────────────────────────┘
  → Бот надсилає як фото
  → Клієнт показує бариста → сканує → Poster ідентифікує
```

### Адмін-розсилка

```
Адмін пише: /broadcast Завтра новий сезонний латте! ☕
  → Бот: "Надіслати 847 клієнтам? [Так] [Ні]"
  → Адмін: [Так]
  → Сервер:
     1. Бере всіх customers з telegram_chat_id IS NOT NULL
     2. Надсилає повідомлення кожному (з throttling 30 msg/sec)
     3. Логує результат
  → Бот: "✅ Надіслано: 841 | ❌ Помилок: 6"
```

## Генерація QR-коду (BarcodeService)

```java
@Service
public class BarcodeService {

    public byte[] generateClientCard(String name, int balance, String clientCode) {
        // 1. Створюємо зображення 600x400
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 2. Малюємо фон, текст
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 400);
        g.setColor(new Color(0x5C, 0x3D, 0x2E)); // coffee brown
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("☕ Назва Кав'ярні", 20, 40);
        g.drawString(name, 20, 80);
        g.drawString("Баланс: " + balance + " бонусів", 20, 120);

        // 3. Генеруємо QR-код через ZXing
        BitMatrix matrix = new QRCodeWriter()
            .encode(clientCode, BarcodeFormat.QR_CODE, 200, 200);
        BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);
        g.drawImage(qr, 200, 160, null);

        g.dispose();

        // 4. Конвертуємо в PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
```

## Health Check та моніторинг

### HealthController

```java
@RestController
@RequestMapping("/health")
public class HealthController {

    private final PosterApiService posterApiService;

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());

        // Перевірка Poster API
        try {
            posterApiService.healthCheck();
            health.put("poster", "OK");
        } catch (Exception e) {
            health.put("poster", "DEGRADED: " + e.getMessage());
        }

        return ResponseEntity.ok(health);
    }
}
```

## Retry механізм для Telegram повідомлень

### NotificationRetryService

```java
@Service
@Slf4j
public class NotificationRetryService {

    private final BonusNotificationRepository notificationRepo;
    private final TelegramBotService telegramService;

    @Value("${retry.max-attempts:3}")
    private int maxAttempts;

    // Запускається кожні 5 хвилин
    @Scheduled(fixedDelayString = "${retry.delay-minutes:5}000")
    public void retryFailedNotifications() {
        List<BonusNotification> failed = notificationRepo
            .findByDeliveredFalseAndRetryCountLessThan(maxAttempts);

        for (BonusNotification notification : failed) {
            try {
                telegramService.sendBonusNotification(notification);
                notification.setDelivered(true);
            } catch (TelegramApiException e) {
                notification.setRetryCount(notification.getRetryCount() + 1);
                notification.setLastError(e.getMessage());

                // Якщо користувач заблокував бота — позначаємо клієнта
                if (e.getMessage().contains("bot was blocked")) {
                    customerService.markAsBlocked(notification.getCustomerId());
                }
            }
            notificationRepo.save(notification);
        }

        log.info("Retry completed: {} notifications processed", failed.size());
    }
}
```

## Graceful Degradation для Poster API

### PosterApiService з fallback

```java
@Service
@Slf4j
public class PosterApiService {

    private final RestTemplate restTemplate;
    private final Cache<Integer, BigDecimal> balanceCache;  // Caffeine cache

    @Value("${poster.fallback-enabled:true}")
    private boolean fallbackEnabled;

    public BigDecimal getClientBonus(int clientId) {
        try {
            BigDecimal balance = fetchBalanceFromPoster(clientId);
            balanceCache.put(clientId, balance);  // Кешуємо успішну відповідь
            return balance;
        } catch (Exception e) {
            log.warn("Poster API unavailable, using cache for client {}", clientId);

            if (fallbackEnabled) {
                BigDecimal cached = balanceCache.getIfPresent(clientId);
                if (cached != null) {
                    return cached;  // Повертаємо кешоване значення
                }
            }
            throw new PosterApiException("Poster API недоступний", e);
        }
    }

    // Для health check
    public void healthCheck() {
        // Легкий запит до Poster API для перевірки доступності
        restTemplate.getForEntity(baseUrl + "settings.getSettings?token=" + token, String.class);
    }
}
```

### Повідомлення користувачу при недоступності Poster

```java
// В TelegramBotService при обробці /balance
public void handleBalanceCommand(Long chatId) {
    try {
        BigDecimal balance = posterApiService.getClientBonus(clientId);
        sendMessage(chatId, "☕ Ваш баланс: " + balance + " бонусів");
    } catch (PosterApiException e) {
        sendMessage(chatId,
            "⚠️ Не вдалося отримати актуальний баланс. " +
            "Спробуйте пізніше або зверніться до бариста.");
    }
}
```

## Конфігурація

### application.yml

```yaml
server:
  port: ${PORT:8080}

spring:
  datasource:
    url: ${DATABASE_URL}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

poster:
  account-name: ${POSTER_ACCOUNT}
  access-token: ${POSTER_TOKEN}
  base-url: https://${POSTER_ACCOUNT}.joinposter.com/api/
  webhook-secret: ${POSTER_WEBHOOK_SECRET:}

telegram:
  bot-token: ${TELEGRAM_BOT_TOKEN}
  webhook-url: ${APP_URL}/api/webhook/telegram
  admin-chat-ids: ${ADMIN_CHAT_IDS}  # comma-separated

loyalty:
  welcome-bonus: 20
  cashback-percent: 5
  max-bonus-payment-percent: 30

broadcast:
  rate-limit-per-second: 25

retry:
  max-attempts: 3
  delay-minutes: 5

poster:
  cache-ttl-seconds: 60           # Кешування балансу на 60 сек
  timeout-ms: 5000
  fallback-enabled: true          # Graceful degradation
```

### Railway environment variables

```
DATABASE_URL=postgresql://...       # Railway надає автоматично
POSTER_ACCOUNT=your-account-name
POSTER_TOKEN=your-access-token
TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
APP_URL=https://your-app.up.railway.app
ADMIN_CHAT_IDS=123456789
```

## Залежності (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Flyway -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- Telegram Bot -->
    <dependency>
        <groupId>org.telegram</groupId>
        <artifactId>telegrambots-spring-boot-starter</artifactId>
        <version>6.9.7</version>
    </dependency>

    <!-- QR/Barcode generation -->
    <dependency>
        <groupId>com.google.zxing</groupId>
        <artifactId>core</artifactId>
        <version>3.5.3</version>
    </dependency>
    <dependency>
        <groupId>com.google.zxing</groupId>
        <artifactId>javase</artifactId>
        <version>3.5.3</version>
    </dependency>

    <!-- Lombok (optional) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Caching (Caffeine) -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
</dependencies>
```

## Деплой на Railway

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Railway Setup

1. Створити проєкт на railway.app
2. Підключити GitHub репо
3. Додати PostgreSQL сервіс (кнопка "New" → "Database" → "PostgreSQL")
4. Додати environment variables (див. вище)
5. Push в main → автодеплой

## Міграція з Loyallyst

### Етап 1: Підготовка (до відключення Loyallyst)

1. Налаштувати бонусну механіку у вбудованій лояльності Poster
   (Маркетинг → Програма лояльності → створити групу з кешбеком)
2. Перевірити що клієнтська база в Poster повна і баланси збігаються з Loyallyst
3. Якщо баланси не збігаються — експорт з Loyallyst (CSV) і синхронізація через Poster API

### Етап 2: Паралельна робота (1–2 тижні)

1. Розгорнути бота і протестувати
2. QR-код на касі: "Програма лояльності тепер у Telegram! Скануй → отримай +20 бонусів"
3. Через Loyallyst push: "Ми переходимо в Telegram! t.me/YourCoffeeBot"
4. Бариста озвучує при кожному замовленні

### Етап 3: Відключення Loyallyst (через 2–4 тижні)

1. Переконатися що 60–80% активних клієнтів підключились
2. Відключити Loyallyst
3. Клієнти, які ще не підключились — їхні бонуси залишаються в Poster,
   вони підключаться коли наступного разу прийдуть

## Вартість

| Стаття              | Loyallyst | Свій бот      |
|---------------------|-----------|---------------|
| Хостинг             | —         | ~$5/міс       |
| БД                  | —         | $0 (Railway)  |
| Telegram API        | —         | безкоштовно   |
| Poster API          | —         | безкоштовно   |
| Сервіс лояльності   | 1500 грн  | —             |
| **Разом**           | **1500 грн/міс** | **~210 грн/міс** |

**Економія: ~1290 грн/міс → ~15 500 грн/рік**

## Часові рамки

| Фаза | Що                                              | Час       |
|------|-------------------------------------------------|-----------|
| 0    | Підготовка: акаунти, бонусна механіка в Poster  | 1 вечір   |
| 1    | Каркас Spring Boot, БД, Dockerfile              | 1 вечір   |
| 2    | Poster API інтеграція + webhook обробка         | 1 вечір   |
| 3    | Telegram Bot: реєстрація, /balance, /card (QR)  | 1 вечір   |
| 4    | Нотифікації після покупки + адмін-розсилки       | 1 вечір   |
| 5    | Деплой Railway, міграція, QR на касу, тестування | 1 вечір   |
| **Разом** |                                            | **~6 вечорів** |

## Ризики та TODO

### Критичні (перед початком розробки)
- [ ] Перевірити точний формат Poster webhook (відкрити dev.joinposter.com в браузері)
- [ ] Перевірити чи є webhook на закриття чеку з client_id та бонусною інформацією
- [ ] Перевірити як Poster сканер розпізнає QR-код (який формат коду очікується)
- [ ] Визначити: чи бонусний баланс в Poster синхронізований з Loyallyst

### Реалізовано в плані
- [x] Обробити edge-case: клієнт заблокував бота (Telegram повертає 403) → `NotificationRetryService`
- [x] Додати retry для failed webhook повідомлень → `NotificationRetryService` + `retry_count` в БД
- [x] Моніторинг: простий health-check endpoint → `HealthController`
- [x] Graceful degradation при недоступності Poster API → кешування + fallback
- [x] Статус клієнта (active/blocked) → поле `status` в таблиці `customers`
- [x] Відстеження прогресу розсилок → таблиця `broadcast_jobs`

### Додаткові покращення (після MVP)
- [ ] Якщо webhook не має потрібних даних — реалізувати polling (перевірка нових транзакцій кожні 2 хв)
- [ ] Інтеграція з Sentry або аналогом для відстеження помилок
- [ ] Prometheus метрики для моніторингу
- [ ] Rate limiting для захисту від зловживань
- [ ] Backup стратегія для PostgreSQL