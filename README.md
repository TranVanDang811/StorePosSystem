# ⚙️ POS System – Spring Boot Enterprise Backend API

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6.x-6DB33F?logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Flyway](https://img.shields.io/badge/Flyway-Database_Migration-CC0202?logo=flyway&logoColor=white)](https://flywaydb.org/)
[![Swagger / OpenAPI 3](https://img.shields.io/badge/OpenAPI-Swagger_UI-85EA2D?logo=swagger&logoColor=black)](https://swagger.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

> A robust, secure, and production-ready RESTful API backend for a modern Point of Sale (POS) and Retail Management System. Built on **Spring Boot 3** and **Java 17**, engineered with layered architecture, strict shift enforcement, automated reconciliation, and stateless JWT authentication with token rotation.

---

## 📌 Table of Contents

- [🚀 Overview](#-overview)
- [🖼️ Demo Screenshots](#️-demo-screenshots)
- [✨ Core Features & Modules](#-core-features--modules)
  - [1. Order Processing & Cashier Terminal](#1-order-processing--cashier-terminal)
  - [2. Cashier Shift Lifecycle & Cash Counting](#2-cashier-shift-lifecycle--cash-counting)
  - [3. Inventory & Purchase Procurement (Import Orders)](#3-inventory--purchase-procurement-import-orders)
  - [4. Product Catalog & Image Storage](#4-product-catalog--image-storage)
  - [5. Promotions & Discount Engine](#5-promotions--discount-engine)
  - [6. BI & Analytics Reporting](#6-bi--analytics-reporting)
  - [7. Security, JWT & Dynamic RBAC](#7-security-jwt--dynamic-rbac)
- [🏗️ System Architecture & Design Patterns](#️-system-architecture--design-patterns)
- [📊 Database Design & Migrations](#-database-design--migrations)
- [📖 API Specifications & Swagger UI](#-api-specifications--swagger-ui)
  - [Unified API Response Structure](#unified-api-response-structure)
  - [Key REST API Endpoints](#key-rest-api-endpoints)
- [🛠️ Tech Stack & Key Libraries](#️-tech-stack--key-libraries)
- [📂 Project Directory Structure](#-project-directory-structure)
- [⚙️ Environment Variables](#️-environment-variables)
- [🚀 Getting Started & Local Development](#-getting-started--local-development)
  - [Prerequisites](#prerequisites)
  - [Step-by-step Setup](#step-by-step-setup)
- [🐳 Docker & Production Deployment](#-docker--production-deployment)
- [🔗 Connected Repositories](#-connected-repositories)

---

## 🚀 Overview

This backend powers real-time retail store transactions and administrative dashboards. It ensures transaction safety, automated inventory sync, financial reconciliation, and role-based access control.

### Key Highlights:
- **Strict Shift Guarding**: Prevents checkout operations unless a cashier has verified or created an active working shift.
- **Cash Reconciliation**: Automatically tallies cash drawer denominations, flags surplus/shortage discrepancies, and generates exportable audit documents.
- **Stateless JWT Security**: Nimbus-based OAuth2 Resource Server implementation featuring access tokens, refresh token rotation, and invalidation queues.
- **Enterprise Ready**: Graceful shutdown, HikariCP connection pooling, Hibernate batch processing (`jdbc.batch_size: 25`), Spring Boot Actuator health checks, and multi-stage Docker builds.

---

## 🖼️ Demo Screenshots

### 🔐 Authentication & Role-Based Authorization
<img width="1919" height="978" alt="Authentication" src="https://github.com/user-attachments/assets/f4b5c686-1491-4c6a-b0a1-44d6b71495d5" />

### 🛒 Cashier POS Interface
<img width="1911" height="962" alt="Screenshot 2026-11-09 202454" src="https://github.com/user-attachments/assets/3b7ca8c3-9bd2-4c66-a6de-a4851c23d960" />


### 💳 Checkout & Split Payment
<img width="1912" height="964" alt="Screenshot 2026-11-09 202622" src="https://github.com/user-attachments/assets/f5db3742-5b8d-4b2b-b30c-0ab040cc28de" />


### 💵 End-of-Shift Settlement & Cash Counting
<img width="1919" height="978" alt="Settlement Interface" src="https://github.com/user-attachments/assets/115bb1d8-31e1-417c-891c-95b1dc2588a1" />

### 📦 Product & Inventory Catalog Management
<img width="1915" height="965" alt="Screenshot 2026-11-09 202332" src="https://github.com/user-attachments/assets/76cac100-8de3-4283-a9f8-1c9c8976413c" />


### 📊 Business Intelligence & Reporting Dashboard
<img width="1915" height="962" alt="Screenshot 2026-11-09 202247" src="https://github.com/user-attachments/assets/817ea73c-e8d8-4f93-a015-7e45df4ccdf9" />

### 📥 Supplier Import Orders & Stock Receiving
<img width="1915" height="962" alt="Screenshot 2026-11-09 203659" src="https://github.com/user-attachments/assets/38e0b51d-1fdb-4272-a9a2-d2144ffbb83f" />

### Product Sales Report
<img width="1913" height="963" alt="Screenshot 2026-11-09 202432" src="https://github.com/user-attachments/assets/7eac0b84-b776-4070-ad19-2a42ee7dd2cd" />

### Create a new user
<img width="1915" height="963" alt="Screenshot 2026-11-09 202412" src="https://github.com/user-attachments/assets/e4be3e57-22f6-4bc5-a25a-c72c90606b4d" />


---

## ✨ Core Features & Modules

### 1. Order Processing & Cashier Terminal
* **Status Lifecycle**: Supports `PENDING`, `COMPLETED`, `CANCELLED`, and `HOLD` (suspend/resume carts when customers add items or retrieve payment methods).
* **Automated Price Computation**: Real-time evaluation of subtotal, tax rates, tiered discounts, line-item totals, and final net payment.
* **Customer Assignment**: Binds transactions to registered customer profiles for purchase history tracking.
* **Receipt Generation**: Thermal printable invoices and PDF exports via OpenPDF.

### 2. Cashier Shift Lifecycle & Cash Counting
* **Enforced Validation**: Validates cashier shift status on every order creation (`/shifts/current`).
* **Open Shift Flow**: Records cashier starting float (initial cash in drawer).
* **End-of-Shift Reconciliation (`/shifts/close`)**: Accepts denomination-level cash counts (500,000 VND down to 1,000 VND), compares against total cash receipts in system orders, and records exact variances (Over / Short).
* **Shift Handover Reports**: Generates detailed closing summaries and exports formatted reports (`/shifts/export/{shiftCode}`).

### 3. Inventory & Purchase Procurement (Import Orders)
* **Procurement Lifecycle**: Tracks supplier purchase orders from `PENDING` through `APPROVED` and `COMPLETED`.
* **Automatic Stock Sync**: Automatically increments warehouse stock balances upon marking import orders as completed.
* **Cost Accounting**: Tracks supplier purchase cost versus retail sales prices to calculate gross margin.

### 4. Product Catalog & Image Storage
* **Cloud Storage**: Multipart uploads streamed directly to Cloudinary with secure URL generation.
* **Barcode / QR Engine**: Powered by Google ZXing for barcode parsing, SKU management, and scanner integration.
* **Granular Filtering**: Search by keyword, category, status (`ACTIVE`, `OUT_OF_STOCK`), price range, and supplier.

### 5. Promotions & Discount Engine
* **Flexible Coupon Rules**: Supports percentage-based discounts and fixed-amount deductions.
* **Validation Constraints**: Date range validity checks, minimum purchase requirements, and usage frequency counters.

### 6. BI & Analytics Reporting
* **Time-Series Revenue**: Daily and monthly sales analytics with order volume breakdowns.
* **Product Performance**: Top-selling products, category sales distribution, and high-margin product tracking.
* **Multi-Format Exports**: Automated report generation to Microsoft Excel (.xlsx via Apache POI) and PDF (.pdf via LibrePDF/OpenPDF).

### 7. Security, JWT & Dynamic RBAC
* **Stateless JWT Authentication**: Access tokens signed with HMAC-SHA512 (`valid-duration: 86400s`).
* **Token Rotation**: Secure refresh token exchange (`/auth/refresh`) and explicit session revocation (`/auth/logout`).
* **Dynamic Database Seeding**: `ApplicationInitConfig` initializes system roles (`ADMIN`, `MANAGE`, `EMPLOYEE`, `CUSTOMER`) and default root administrator accounts on initial boot.
* **Method-Level Security**: Declarative `@PreAuthorize` guards restricting sensitive endpoints.

---

## 🏗️ System Architecture & Design Patterns

The backend follows clean layered architecture and enterprise Spring best practices:

```
┌─────────────────────────────────────────────────────────────┐
│                 Client Requests (Web / POS)                 │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTP / JSON / Multipart
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                  Security Filter Chain                      │
│   (CORS, CustomJwtDecoder, JwtAuthenticationEntryPoint)     │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      Controller Layer                       │
│    (REST Endpoints, Swagger Docs, Request Validation)       │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       Service Layer                         │
│      (Business Rules, Shift Guards, Transaction Boundary)   │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼ MapStruct                    ▼ Spring Data JPA
┌──────────────────────────────┐  ┌───────────────────────────┐
│          DTO Layer           │  │     Repository Layer      │
│  (Type-safe input / output)  │  │ (Spring Data JPA / MySQL) │
└──────────────────────────────┘  └─────────────┬─────────────┘
                                                ▼
                                  ┌───────────────────────────┐
                                  │      Database (MySQL)     │
                                  └───────────────────────────┘
```

- **DTO Mapping**: MapStruct 1.5 generates compile-time, zero-reflection mapper implementations.
- **Global Exception Handling**: Centralized `@ControllerAdvice` maps validation faults, business rule errors, and database constraints to standard HTTP responses.
- **Connection Optimization**: Configured with `open-in-view: false` to eliminate lazy-loading connection leakage outside transaction scopes.

---

## 📊 Database Design & Migrations

The database schema is managed via **Flyway**:
* `V1__initial_schema.sql`: Core relational tables (`users`, `roles`, `products`, `categories`, `orders`, `order_items`, `payments`, `shifts`, `suppliers`, `import_orders`, `discounts`).
* `V2__add_constraints_and_indexes.sql`: Foreign keys, unique constraints, and search indexes on SKU, order codes, barcodes, and transaction dates.

**Default Database Collation**: `utf8mb4_unicode_ci`  
**Timezone**: `Asia/Ho_Chi_Minh`

---

## 📖 API Specifications & Swagger UI

### Interactive Swagger UI
When the application is running, access full interactive API documentation and testing at:
* **Swagger UI**: [http://localhost:8080/backend/swagger-ui/index.html](http://localhost:8080/backend/swagger-ui/index.html)
* **OpenAPI 3 JSON Specification**: [http://localhost:8080/backend/v3/api-docs](http://localhost:8080/backend/v3/api-docs)

### Unified API Response Structure

Every endpoint produces a predictable, standardized envelope:

**Success Response (`code = 1000`):**
```json
{
  "code": 1000,
  "success": true,
  "message": "Success",
  "result": { ... },
  "timestamp": "2026-11-09 20:45:00"
}
```

**Error Response:**
```json
{
  "code": 1001,
  "success": false,
  "message": "User not existed",
  "timestamp": "2026-11-09 20:45:00"
}
```

### Key REST API Endpoints

| Module | Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- | :--- |
| **Auth** | `POST` | `/auth/token` | User authentication & issue tokens | Public |
| **Auth** | `POST` | `/auth/refresh` | Refresh access token using refresh token | Public |
| **Auth** | `POST` | `/auth/logout` | Invalidate active token session | Public |
| **Auth** | `POST` | `/auth/introspect` | Validate token status and claims | Public |
| **Shift** | `POST` | `/shifts/open` | Open cashier shift with opening float | Staff / Cashier |
| **Shift** | `POST` | `/shifts/close` | Close shift with cash denomination count | Staff / Cashier |
| **Shift** | `GET` | `/shifts/current` | Retrieve active shift for logged-in user | Staff / Cashier |
| **Shift** | `GET` | `/shifts/export/{code}` | Export shift settlement report (HTML/PDF) | Staff / Manager |
| **Orders** | `POST` | `/orders` | Create sales order | Staff / Cashier |
| **Orders** | `GET` | `/orders` | Paginated orders with date/status filter | Staff / Manager |
| **Orders** | `PUT` | `/orders/{id}/hold` | Put active order on hold | Staff / Cashier |
| **Orders** | `PUT` | `/orders/{id}/update` | Update or resume held order | Staff / Cashier |
| **Payments** | `POST` | `/payments` | Process payment (Cash, Transfer, Card) | Staff / Cashier |
| **Payments** | `GET` | `/payments` | Audit transaction history | Manager / Admin |
| **Products** | `POST` | `/products` | Create product with Cloudinary image upload | Manager / Admin |
| **Products** | `GET` | `/products` | Paginated product list with search/filter | Authenticated |
| **Products** | `PUT` | `/products/{id}` | Update product information & photo | Manager / Admin |
| **Procurement** | `POST` | `/import-orders` | Create supplier purchase order | Manager / Admin |
| **Procurement** | `GET` | `/import-orders` | List purchase orders | Manager / Admin |
| **Reports** | `GET` | `/reports/dashboard` | Executive KPI metrics | Manager / Admin |
| **Reports** | `GET` | `/reports/daily` | Detailed daily revenue breakdown | Manager / Admin |
| **Reports** | `GET` | `/reports/monthly` | Monthly sales statistics | Manager / Admin |
| **Users** | `GET` | `/users/my-info` | Get current authenticated user details | Authenticated |
| **Users** | `GET` | `/users` | List staff accounts with role filtering | Admin / Manager |

---

## 🛠️ Tech Stack & Key Libraries

| Technology | Version | Purpose |
| :--- | :--- | :--- |
| **Java** | 17 (LTS) | Core programming language |
| **Spring Boot** | 3.4.5 | Application framework & dependency injection |
| **Spring Data JPA** | 3.4.5 | ORM persistence layer (Hibernate 6) |
| **Spring Security** | 6.x | Security framework & OAuth2 Resource Server |
| **MySQL Connector** | 8.0 | Relational database driver |
| **Flyway** | 10.x | Database schema versioning & migration |
| **MapStruct** | 1.5.5.Final | High-performance DTO mapping processor |
| **Lombok** | 1.18.30 | Boilerplate code reducer |
| **SpringDoc OpenAPI** | 2.8.13 | Interactive Swagger UI & OpenAPI 3 docs |
| **Cloudinary SDK** | 2.0.0 | Cloud product image hosting |
| **Google ZXing** | 3.5.2 | Barcode & QR code scanning and generation |
| **Apache POI** | 5.2.5 | Excel spreadsheet (.xlsx) data export |
| **LibrePDF OpenPDF** | 1.3.30 | Document printing & PDF invoice generation |
| **Dotenv Java** | 2.2.4 | Local `.env` file configuration reader |

---

## 📂 Project Directory Structure

```plaintext
backend/
├── src/
│   ├── main/
│   │   ├── java/com/possystem/backend/
│   │   │   ├── auth/           # Login, Token generation, Introspection, Logout
│   │   │   ├── category/       # Category domain (Entity, DTO, Repo, Service, Controller)
│   │   │   ├── common/         # Response wrapper, Error codes, GlobalExceptionHandler, Enums
│   │   │   ├── config/         # Security, JWT Decoder, Cloudinary, Swagger, Seed Init
│   │   │   ├── discount/       # Vouchers, coupon validation, discount calculations
│   │   │   ├── importorder/    # Stock procurement, supplier receiving, inventory increment
│   │   │   ├── order/          # Order lifecycle, hold/resume, subtotal calculation
│   │   │   ├── payment/        # Payment methods, gateway tracking, payment receipts
│   │   │   ├── product/        # Product catalog, stock tracking, barcode integration
│   │   │   ├── report/         # BI sales stats, daily/monthly reports, Excel/PDF exports
│   │   │   ├── role/           # Role entities and authority management
│   │   │   ├── shift/          # Cashier shifts, open/close shift, denomination cash counting
│   │   │   ├── supplier/       # Vendor database management
│   │   │   ├── user/           # Employee records, password management, profile
│   │   │   └── BackendApplication.java # Spring Boot main entrypoint
│   │   └── resources/
│   │       ├── db/migration/   # Flyway SQL migrations (V1, V2)
│   │       ├── application.yaml        # Base application settings
│   │       ├── application-dev.yml     # Local development profile
│   │       ├── application-prod.yml    # Production profile (Hikari, Graceful shutdown)
│   │       ├── application-test.yml    # Unit/Integration testing profile (H2)
│   │       └── logback-spring.xml      # Enterprise rolling log configuration
├── .dockerignore               # Docker build exclusions
├── .env.example                # Blueprint for environment variables
├── Dockerfile                  # Multi-stage production Docker build
├── docker-compose.yml          # Local MySQL development service
└── pom.xml                     # Maven project descriptor & dependencies
```

---

## ⚙️ Environment Variables

Create a `.env` file in the root `backend` directory based on `.env.example`:

```bash
cp .env.example .env
```

| Variable | Description | Default | Sample Value |
| :--- | :--- | :--- | :--- |
| `SERVER_PORT` | HTTP port for backend server | `8080` | `8080` |
| `CONTEXT_PATH` | Application servlet context path | `/backend` | `/backend` |
| `SPRING_PROFILES_ACTIVE` | Active Spring Boot profile | `dev` | `dev` or `prod` |
| `DBMS_CONNECTION` | JDBC Connection URL | - | `jdbc:mysql://localhost:3306/backend` |
| `DBMS_USERNAME` | Database username | `root` | `root` |
| `DBMS_PASSWORD` | Database password | `root` | `YourSecretDbPassword` |
| `JWT_SIGNER_KEY` | Base64 HMAC secret key (min 256-bit) | - | `8MF1jmNo+KGXSruCeew5...` |
| `JWT_VALID_DURATION` | Access token lifespan (seconds) | `86400` | `86400` (24h) |
| `JWT_REFRESH_DURATION` | Refresh token lifespan (seconds) | `604800` | `604800` (7 days) |
| `CORS_ALLOWED_ORIGINS` | Permitted frontend URLs (comma-separated)| `http://localhost:5173` | `http://localhost:5173,http://localhost:3000` |
| `ADMIN_USERNAME` | Initial seeded root administrator | `admin` | `admin` |
| `ADMIN_PASSWORD` | Initial seeded admin password | `admin` | `Admin12345` |
| `CLOUDINARY_CLOUD_NAME`| Cloudinary storage cloud identifier | - | `dx3mcnnfa` |
| `CLOUDINARY_API_KEY` | Cloudinary API Key | - | `763191867466888` |
| `CLOUDINARY_API_SECRET`| Cloudinary API Secret | - | `AvAmuie9...` |

---

## 🚀 Getting Started & Local Development

### Prerequisites
* **Java Development Kit (JDK)**: Version 17 or higher ([Eclipse Temurin Recommended](https://adoptium.net/))
* **Apache Maven**: Version 3.9+ (or use the included Maven wrapper)
* **MySQL Server**: Version 8.0+ running on port `3306`

### Step-by-step Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/TranVanDang811/StorePosSystem.git
   cd StorePosSystem
   ```

2. **Launch Database (via Docker or local MySQL service):**
   ```bash
   # Option A: Start MySQL via Docker
   docker-compose up -d mysql

   # Option B: Use an already installed local MySQL instance
   # Ensure a database named 'backend' exists:
   # CREATE DATABASE backend CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

3. **Configure Environment:**
   ```bash
   cp .env.example .env
   # Edit .env with your local credentials and JWT key
   ```

4. **Compile and Run Application:**
   ```bash
   # Compile project
   mvn clean install -DskipTests

   # Run application with active dev profile
   mvn spring-boot:run
   ```

5. **Verify Running System:**
   * **Health Check**: [http://localhost:8080/backend/actuator/health](http://localhost:8080/backend/actuator/health)
   * **Swagger Documentation**: [http://localhost:8080/backend/swagger-ui/index.html](http://localhost:8080/backend/swagger-ui/index.html)
   * **Default Admin**: `username: admin` / `password: Admin12345` (or credentials set in `.env`)

---

## 🐳 Docker & Production Deployment

The project provides a multi-stage `Dockerfile` creating a secured, minimal JRE runtime image with non-root user execution:

### 1. Build Docker Image

```bash
docker build -t pos-backend:latest .
```

### 2. Run Container Standalone

```bash
docker run -d \
  --name pos-backend-app \
  -p 8080:8080 \
  --env-file .env \
  pos-backend:latest
```

### 3. Production Multi-Service Stack (`docker-compose.prod.yml`)

Run MySQL, Spring Boot Backend, and React Frontend together in an isolated network:

```bash
docker compose -f docker-compose.prod.yml up -d
```

---

## 🔗 Connected Repositories

* ⚙️ **Backend Repository (Spring Boot)**: [StorePosSystem](https://github.com/TranVanDang811/StorePosSystem.git)
* 🌐 **Frontend Repository (React + Vite)**: [Interface-Frontend-Pos](https://github.com/TranVanDang811/Interface-Frontend-Pos.git)

---

<p align="center">
  Crafted with ❤️ for scalable, reliable retail business operations.
</p>
