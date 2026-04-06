# E-Commerce Microservices — Spring Boot Sample

A sample Spring Boot microservices project demonstrating Service Discovery, API Gateway, and two REST microservices backed by PostgreSQL.

## Architecture

```
                    ┌──────────────────────┐
                    │   Service Registry   │
                    │   (Eureka Server)    │
                    │     Port: 8761       │
                    └──────────┬───────────┘
                               │
                    ┌──────────┴───────────┐
                    │     API Gateway      │
                    │     Port: 8080       │
                    └──┬───────────────┬───┘
                       │               │
          ┌────────────┴──┐     ┌──────┴────────────┐
          │ Product Svc   │     │   Order Svc       │
          │ Port: 8081    │     │   Port: 8082      │
          │ DB: product_db│     │   DB: order_db    │
          └───────────────┘     └───────────────────┘
```

## Tech Stack

| Component        | Technology                          |
|------------------|-------------------------------------|
| Language         | Java 17+ (tested with OpenJDK 24)   |
| Framework        | Spring Boot 3.4.2                   |
| Service Discovery| Netflix Eureka                      |
| API Gateway      | Spring Cloud Gateway (Reactive)     |
| Database         | PostgreSQL (port 5434)              |
| Build Tool       | Apache Maven                        |

---

## Prerequisites

- **Java 17+** installed (`java -version`)
- **Maven** installed (`mvn -version`)
- **PostgreSQL** running on `localhost:5434` with user `postgres` / password `postgres`

---

## Step 1: Create the Databases

Connect to your PostgreSQL instance and create the required databases:

```bash
# Connect to PostgreSQL (Docker on port 5434)
psql -h localhost -p 5434 -U postgres
```

Once inside the `psql` shell:

```sql
CREATE DATABASE product_db;
CREATE DATABASE order_db;

-- Verify
\l

-- Exit
\q
```

---

## Step 2: Build All Projects

From the `ecommerce-microservices` root directory:

```bash
mvn clean install -DskipTests
```

This builds all 4 modules in the correct order.

---

## Step 3: Run the Services

**Important:** Start the services in this order. Open a **separate terminal** for each service.

### Terminal 1 — Service Registry (start first, wait for it to be ready)
```bash
cd service-registry
mvn spring-boot:run
```
Wait until you see: `Started ServiceRegistryApplication`  
Verify at: [http://localhost:8761](http://localhost:8761)

### Terminal 2 — API Gateway
```bash
cd api-gateway
mvn spring-boot:run
```
Wait until you see: `Started ApiGatewayApplication`

### Terminal 3 — Product Service
```bash
cd product-service
mvn spring-boot:run
```

### Terminal 4 — Order Service
```bash
cd order-service
mvn spring-boot:run
```

---

## Step 4: Test the APIs

### Product Service (direct)
```bash
# Create a product
curl -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "MacBook Pro", "price": 2499.99}'

# Get all products
curl http://localhost:8081/api/products

# Get product by ID
curl http://localhost:8081/api/products/1

# Update a product
curl -X PUT http://localhost:8081/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "MacBook Pro M4", "price": 2799.99}'

# Delete a product
curl -X DELETE http://localhost:8081/api/products/1
```

### Order Service (direct)
```bash
# Create an order
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderNumber": "ORD-001", "productId": 1, "quantity": 2}'

# Get all orders
curl http://localhost:8082/api/orders
```

### Via API Gateway (port 8080)
```bash
# Products through gateway
curl http://localhost:8080/api/products

# Orders through gateway
curl http://localhost:8080/api/orders
```

---

## API Endpoints Summary

### Product Service (`/api/products`)
| Method | Endpoint             | Description         |
|--------|----------------------|---------------------|
| GET    | `/api/products`      | Get all products    |
| GET    | `/api/products/{id}` | Get product by ID   |
| POST   | `/api/products`      | Create a product    |
| PUT    | `/api/products/{id}` | Update a product    |
| DELETE | `/api/products/{id}` | Delete a product    |

### Order Service (`/api/orders`)
| Method | Endpoint            | Description        |
|--------|---------------------|--------------------|
| GET    | `/api/orders`       | Get all orders     |
| GET    | `/api/orders/{id}`  | Get order by ID    |
| POST   | `/api/orders`       | Create an order    |
| PUT    | `/api/orders/{id}`  | Update an order    |
| DELETE | `/api/orders/{id}`  | Delete an order    |

---

## Eureka Dashboard

Once all services are running, visit the Eureka Dashboard at:  
**[http://localhost:8761](http://localhost:8761)**

You should see `PRODUCT-SERVICE` and `ORDER-SERVICE` registered as instances.

---

## Project Structure

```
ecommerce-microservices/
├── pom.xml                          # Root aggregator POM
├── README.md
├── service-registry/                # Eureka Server (port 8761)
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../ServiceRegistryApplication.java
│       └── resources/application.yml
├── api-gateway/                     # Spring Cloud Gateway (port 8080)
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../ApiGatewayApplication.java
│       └── resources/application.yml
├── product-service/                 # Product Microservice (port 8081)
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../
│       │   ├── ProductServiceApplication.java
│       │   ├── entity/Product.java
│       │   ├── repository/ProductRepository.java
│       │   ├── service/ProductService.java
│       │   └── controller/ProductController.java
│       └── resources/application.yml
└── order-service/                   # Order Microservice (port 8082)
    ├── pom.xml
    └── src/main/
        ├── java/.../
        │   ├── OrderServiceApplication.java
        │   ├── entity/Order.java
        │   ├── repository/OrderRepository.java
        │   ├── service/OrderService.java
        │   └── controller/OrderController.java
        └── resources/application.yml
```
