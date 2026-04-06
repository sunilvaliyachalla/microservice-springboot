# E-Commerce Microservices Stack

A complete, production-ready, containerized Spring Boot Microservices architecture integrated with a Vanilla JS/HTML Frontend, Eureka Service Discovery, and Spring Cloud API Gateway.

```text
                    ┌──────────────────────────┐
                    │    UI Dashboard (Nginx)  │
                    │       Port: 3000         │
                    └──────────┬───────────────┘
                               │
                    ┌──────────┴───────────┐
                    │     API Gateway      │
                    │     Port: 8080       │
                    └──┬────────┬──────┬───┘
                       │        │      │
          ┌────────────┴──┐     │   ┌──┴────────────────┐
          │ Product Svc   │     │   │   Order Svc       │
          │ Port: 8081    │     │   │   Port: 8082      │
          │ DB: mydb      │     │   │   DB: mydb        │
          └───────────────┘     │   └───────────────────┘
                                │
                    ┌───────────┴──────────┐
                    │   Service Registry   │
                    │   (Eureka Server)    │
                    │     Port: 8761       │
                    └──────────────────────┘
```

## Tech Stack

| Component        | Technology                          |
|------------------|-------------------------------------|
| Language         | Java 17 (Dockerized)                |
| Framework        | Spring Boot 3.4.2                   |
| Service Discovery| Netflix Eureka                      |
| API Gateway      | Spring Cloud Gateway (Reactive)     |
| Database         | PostgreSQL (host.docker.internal)   |
| Frontend         | Vanilla JS/HTML with Nginx          |
| Containerization | Docker & Docker Compose             |

## Project Structure

```text
ecommerce-microservices/
├── docker-compose.yml
├── .gitignore
├── README.md
├── pom.xml (Parent)
│
├── service-registry/
│   ├── src/main/java...
│   ├── pom.xml
│   ├── Dockerfile
│   └── .dockerignore
│
├── api-gateway/
│   ├── src/main/java...
│   ├── src/main/resources/application.yml
│   ├── pom.xml
│   ├── Dockerfile
│   └── .dockerignore
│
├── product-service/
│   ├── src/main/java.../controller, entity, repository, service
│   ├── src/main/resources/application.yml
│   ├── pom.xml
│   ├── Dockerfile
│   └── .dockerignore
│
├── order-service/
│   ├── src/main/java.../controller, entity, repository, service
│   ├── src/main/resources/application.yml
│   ├── pom.xml
│   ├── Dockerfile
│   └── .dockerignore
│
└── ui-service/
    ├── index.html
    ├── style.css
    ├── app.js
    └── Dockerfile
```

## Architecture Overview
This project consists of 5 isolated containerized applications that talk to each other alongside a local PostgreSQL database:

1. **Service Registry (Eureka)**: Runs on `:8761`. Tracks all active microservice instances.
2. **API Gateway**: Runs on `:8080`. Routes traffic to the correct microservice and handles CORS.
3. **Product Service**: Runs on `:8081`. Manages products, connected to PostgreSQL.
4. **Order Service**: Runs on `:8082`. Manages orders, connected to PostgreSQL.
5. **UI Service**: Runs on `:3000`. A sleek, dark-mode frontend built with pure HTML/JS/CSS served via Nginx.

---

## 🚀 Setup & Commands Used Throughout the Project

### Phase 1: Java & Spring Boot Setup
1. **Removed Lombok**: Your machine uses Java 24, which causes compilation issues with Lombok. We completely stripped `@Data`, `@NoArgsConstructor`, etc. from your Spring Boot entity and controller files, replacing them with standard Java getters, setters, and constructors.
2. **Local Compilation Check**: We tested local compilation using Standard Maven without running tests to verify the removal of Lombok was successful:
   ```bash
   mvn clean install -DskipTests
   ```
3. **Database Setup**: We connected the local Java Spring Boot properties (`application.yml`) directly to your running PostgreSQL database using standard credentials (`postgres`/`postgres`).

### Phase 2: Microservice Testing
To ensure the microservices were working properly before containerizing, we started each service manually on their distinct ports and fired test payloads:
```bash
# Creating a product via API
curl -s -X POST http://localhost:8081/api/products -H "Content-Type: application/json" -d '{"name": "MacBook Pro", "price": 2499.99}'

# Creating an order via API
curl -s -X POST http://localhost:8082/api/orders -H "Content-Type: application/json" -d '{"orderNumber": "ORD-001", "productId": 1, "quantity": 2}'
```

### Phase 3: Dockerization & Multi-Stage Builds
Instead of depending on the local machine's Java/Maven installation, we isolated every service into its own Docker container. 

For each Java service (`service-registry`, `api-gateway`, `product-service`, `order-service`), a **Multi-Stage Dockerfile** was created:
* **Stage 1 (Builder)**: Uses `maven:3.9-eclipse-temurin-17` to pull dependencies and compile the `.jar` cleanly.
* **Stage 2 (Production)**: Uses `eclipse-temurin:17-jre` (ARM64 Apple Silicon friendly), grabs only the compiled `.jar`, and discards the `/src` code to ensure secure, tiny, production-ready images.

We also created `.dockerignore` files for each service to prevent `target/` and `.git` folders from bloating the Docker context.

### Phase 4: UI Service Integration
To make interacting with the APIs visual, we created a brand new `ui-service` folder.
* Built **without heavy frameworks** — pure HTML, CSS, app.js.
* Styled with Modern Dark Mode (Glassmorphism, gradients, CSS animations).
* Packaged into its own lightweight `nginx:alpine` Docker image exposing port `:80` inside the container to `:3000` on the local machine.
* Configured `globalcors` in the **API Gateway** (`application.yml`) to specifically whitelist web traffic fetching data from `http://localhost:3000`.

### Phase 5: Docker Compose Orchestration
We tied all these pieces together in a `docker-compose.yml`.
* Configured isolated networking (`ecommerce-net`).
* Utilized `host.docker.internal:5434` so the Dockerized microservices could successfully securely reach your Mac's locally running Postgres database.
* Added `depends_on` healthchecks so the Gateway, Product, and Order services gracefully wait for Eureka to start before launching themselves.

**To run the entire ecosystem with one command:**
```bash
docker compose up --build
```
*(Use `-d` detached mode if you want to run it silently in the background).*

### Phase 6: Git Cleanup
To ensure your repository stays clean, we:
1. Created a root `.gitignore` tracking unwanted Java directories (Maven properties, Eclipse/IntelliJ metadata, Mac `.DS_Store` files).
2. Cleared the already polluted Git tracker by running:
```bash
git rm -r --cached "*/target" "target"
```

---

## 🎯 Navigating the Application

Once `docker compose up --build` is running, you can access the system at the following addresses:

| Application | Local URL | Description |
|---|---|---|
| **Front-End Dashboard** | [http://localhost:3000](http://localhost:3000) | Interact visually, Add Products, View Orders. |
| **API Gateway** | `http://localhost:8080/api/...` | Central backend entrypoint. All JS calls go here. |
| **Eureka Registry** | [http://localhost:8761](http://localhost:8761) | The discovery dashboard showing running containers |
| **Product Backend** | `http://localhost:8081` | (Internal to container) |
| **Order Backend** | `http://localhost:8082` | (Internal to container) |

### Shutting Down
To gracefully kill all containers, network configurations, and UI environments, run:
```bash
docker compose down
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

## 🛠️ Full Command Cheat Sheet

Here are all the commands you might need when working with this project.

### Maven Local Development (No Docker)
If you want to run or build the services individually on your machine:
```bash
# Build the entire project and skip tests
mvn clean install -DskipTests

# Run a specific microservice locally (run this inside its directory)
mvn spring-boot:run
```

### Docker Compose Complete Setup
The standard commands for managing the entire stack together:
```bash
# Build brand new images and start all containers
docker compose up --build

# Run in the background (detached mode)
docker compose up -d --build

# View the live logs of all running containers
docker compose logs -f

# View the live logs of just ONE specific container (e.g. gateway)
docker compose logs -f api-gateway

# Stop and remove all containers
docker compose down
```

### Docker Individual Operations
If you want to manually build or debug a specific Docker image without Compose:
```bash
# Build a single Docker image (run this inside the service directory, e.g., product-service)
docker build -t mycompany/ecommerce-product-service:v1.0 .

# Run that single image
docker run -p 8081:8081 mycompany/ecommerce-product-service:v1.0

# Push an image to a registry like DockerHub or AWS ECR (For Production Deployment)
docker push mycompany/ecommerce-product-service:v1.0
```

### Git / Cleanup
If you accidentally track generated `.class` or `.jar` files in Git before setting up your `.gitignore`:
```bash
# Untrack all 'target' folders from Git's cache
git rm -r --cached "*/target" "target"

# Commit the clean up
git add .
git commit -m "Removed target folders from git tracking"
```
