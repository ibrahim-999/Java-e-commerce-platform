# Production Concerns & Solutions

> Real-world problems we identified and solved in this project.
> Each section explains the problem, why it matters, and how we solved it — from our scale to Amazon/Alibaba scale.

---

## 1. Race Conditions (Stock Management)

### The Problem

Two users try to buy the last item at the same time:

```
User A reads stock = 1       User B reads stock = 1
User A sets stock = 0 ✅      User B sets stock = 0 ✅  ← OVERSOLD!
```

This is called a **race condition** — two operations "race" each other, and the result depends on timing.
In our original code, `reduceStock()` did a read-then-write:

```java
// DANGEROUS — read and write are separate operations
Product product = repository.findById(id);         // READ: stock = 1
product.setStockQuantity(stock - quantity);         // WRITE: stock = 0
// Between READ and WRITE, another thread can also read stock = 1
```

### Solutions (3 Levels)

#### Level 1: Optimistic Locking (`@Version`) — what we use for general updates

A `version` column is added to the entity. Every time the row is updated, the version increments.
When two transactions try to update the same row, the second one fails because the version changed.

```java
@Entity
public class Product {
    @Version
    private Long version;  // Hibernate manages this automatically
}

// Under the hood, Hibernate generates:
// UPDATE products SET name = ?, version = 2 WHERE id = 1 AND version = 1
// If another transaction already changed version to 2, this returns 0 rows → exception
```

**When to use:** General updates where conflicts are rare (e.g., updating product name, description).
Retry the operation on conflict.

#### Level 2: Atomic SQL — what we use for stock operations

Skip the read-then-write entirely. Do everything in one atomic SQL statement:

```java
@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity " +
       "WHERE p.id = :id AND p.stockQuantity >= :quantity")
int reduceStock(Long id, int quantity);
// Returns 1 = success, 0 = insufficient stock (no race condition possible)
```

**Why this works:** The database executes the entire statement atomically.
The `WHERE stock >= quantity` check and the `SET stock = stock - quantity` happen as one indivisible operation.
No other transaction can sneak in between the check and the update.

**When to use:** High-contention operations like stock management, balance deductions, counter increments.

#### Level 3: Redis + Message Queue (Amazon/Alibaba scale)

At massive scale (millions of concurrent users), even database locks become a bottleneck.

```
User adds to cart
    → Redis: DECR stock (instant, in-memory)
    → Kafka: publish StockReservedEvent
    → Consumer: confirm against PostgreSQL
    → If DB says "oversold": Kafka → rollback Redis → notify user
```

- **Redis** handles the speed (sub-millisecond stock checks)
- **PostgreSQL** remains the source of truth
- **Kafka** ensures eventual consistency between Redis and PostgreSQL
- If the DB disagrees with Redis, a compensation event rolls back

**When to use:** When you have millions of concurrent purchases (flash sales, Black Friday).

### What We Implemented

- `@Version` on `Product` entity for optimistic locking on general updates
- Atomic SQL `reduceStock()` and `restoreStock()` in `ProductRepository` for race-condition-safe stock operations
- `InsufficientStockException` when atomic update returns 0 affected rows
- `OptimisticLockException` handler in `GlobalExceptionHandler` for version conflicts

---

## 2. Price Caching & Price Consistency

### The Problem (two parts)

**Part A — Performance:** Millions of users browsing products. Every request hits PostgreSQL.
A database can handle ~10K queries/sec. Amazon gets ~100M+ product views/day. The DB would melt.

**Part B — Consistency:** User sees a product at $99. Adds to cart 30 minutes later.
Seller changed the price to $129. What price does the user pay?

### Solution A: Cache product data in Redis

```java
@Cacheable(value = "products", key = "#id")      // first call → DB + cache in Redis
public Product getProductById(Long id) { ... }

@CacheEvict(value = "products", key = "#id")      // on update → remove from Redis
public Product updateProduct(Long id, ...) { ... }
```

```
Request → Redis cache → HIT? → return instantly (no DB query)
                       → MISS? → query PostgreSQL → store in Redis → return
```

We will implement this in **Phase 8 (Redis Caching)**.

### Solution B: Price snapshotting in orders

**Rule: Never reference the product's current price in an order. Always snapshot it.**

```java
// BAD — order only stores product ID
public class OrderItem {
    private Long productId;
    // What price did the customer pay? We don't know without querying the product.
    // But the product price might have changed since the order was placed!
}

// GOOD — order snapshots the price at purchase time
public class OrderItem {
    private Long productId;
    private BigDecimal priceAtPurchase;  // frozen — never changes after order
    private String productNameSnapshot;  // frozen — even if product is renamed later
}
```

**The checkout flow:**
1. User sees $99 in search (cached, might be slightly stale — that's OK)
2. User adds to cart → price is re-checked against DB/cache
3. At checkout → price is verified one final time against DB
4. If price changed → show: "Price has changed since you added this item"
5. User confirms → order stores `priceAtPurchase = $99` permanently

**Why this matters:** A customer orders 3 items. Next month they dispute the charge.
You need to show exactly what they paid. If you only stored product IDs and the product
prices changed 5 times since then, you can't reconstruct the original order total.

### What We Implemented

- `priceAtPurchase` field will be stored in `OrderItem` (order-service, next step)
- Price is always verified before order creation by calling product-service
- Each service owns its own data — order-service stores the price snapshot, not a reference

### At Amazon/Alibaba Scale

- Product catalog served from **CDN + Elasticsearch** (not PostgreSQL)
- Prices cached in **Redis clusters** with 30-second TTL
- Price changes publish events to **Kafka** → invalidate caches across all regions
- Order history always uses snapshot prices

---

## 3. Aggregations with Millions of Products

### The Problem

`SELECT AVG(price) FROM products` with 50 million rows = full table scan = slow (seconds to minutes).
Now add: "average price per category, per brand, per price range, per rating" = even slower.

### Solutions (4 Levels)

#### Level 1: Database-level aggregation — what we use now

**Never load millions of products into Java to calculate the average.**
Let the database do it — it's optimized for this:

```java
@Query("SELECT AVG(p.price) FROM Product p")
BigDecimal getAveragePrice();

@Query("SELECT AVG(p.price) FROM Product p WHERE p.category.id = :categoryId")
BigDecimal getAveragePriceByCategory(Long categoryId);
```

The database scans the table once and returns one number. Only that single number
travels over the network to Java. Works fine up to ~10M rows with proper indexes.

#### Level 2: Materialized Views — medium scale

Pre-compute aggregations and refresh periodically:

```sql
CREATE MATERIALIZED VIEW category_price_stats AS
SELECT
    category_id,
    AVG(price) as avg_price,
    MIN(price) as min_price,
    MAX(price) as max_price,
    COUNT(*) as product_count
FROM products
GROUP BY category_id;

-- Refresh every 5 minutes via a scheduled job
REFRESH MATERIALIZED VIEW CONCURRENTLY category_price_stats;
```

Queries hit the materialized view (pre-computed table) instead of scanning 50M rows.

#### Level 3: CQRS + Elasticsearch — large scale

Separate the write model (PostgreSQL) from the read model (Elasticsearch):

```
Product created/updated → PostgreSQL (source of truth)
                        → Kafka event → Elasticsearch (optimized for reads/aggregations)

User asks "avg price of laptops?"
    → Elasticsearch (milliseconds, even with 100M products)
    → NOT PostgreSQL
```

Elasticsearch can compute avg price grouped by category, brand, price range,
and rating — across 100M products — in milliseconds. PostgreSQL would take minutes.

#### Level 4: Stream Processing — Amazon/Alibaba scale

Maintain running aggregates as data flows through the system:

```
Product price changed → Kafka event
    → Kafka Streams / Apache Flink
    → Updates running average incrementally (no full table scan ever)
    → Stores result in Redis
    → User query hits Redis (sub-millisecond)
```

No full scan. No periodic refresh. The average is always up-to-date because
it's updated incrementally every time a product changes.

### What We Implemented

- Database-level `AVG()`, `MIN()`, `MAX()`, `COUNT()` queries in `ProductRepository`
- `PriceStatsResponse` DTO for the aggregation results
- `GET /api/products/stats` endpoint for overall stats
- `GET /api/products/stats/category/{id}` for per-category stats
- All computation happens in PostgreSQL — Java receives only the final numbers

---

## Summary Table

| Concern | Our Solution (Learning Scale) | Enterprise Solution (Amazon Scale) |
|---------|-------------------------------|-----------------------------------|
| Race conditions (stock) | Atomic SQL + `@Version` optimistic locking | Redis stock reservation + Kafka event sourcing |
| Price caching | Price snapshot in order items | Redis clusters + CDN + Kafka invalidation |
| Price aggregation | Database `AVG()` queries | CQRS + Elasticsearch + Kafka Streams |
| General DB caching | Spring `@Cacheable` (Phase 8) | Redis clusters with TTL + CDN edge caching |
