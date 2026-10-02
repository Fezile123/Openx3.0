# OpenEx

## Digital Asset Exchange

OpenEx is a full-stack digital asset exchange simulation built to demonstrate how a modern cryptocurrency trading platform can be designed and developed.

The project combines a **Kotlin/Spring Boot backend**, **PostgreSQL database**, **React/Vite frontend**, **Python market-data service**, **WebSockets**, and **JWT authentication** into one integrated trading platform.

The application simulates the core functionality of a digital asset exchange, including account authentication, wallets, order placement, order matching, trade execution, order books, market data, and a real-time trading dashboard.

---

## Project Status

**Day 15 — Full-Stack Trading Platform Integration**

OpenEx has reached a working full-stack development stage.

The main components of the application are integrated and can be run locally:

* Spring Boot backend
* PostgreSQL database
* React trading frontend
* Python market-data service
* JWT authentication
* Order management and matching
* Simulated BTC-USD market data
* WebSocket/STOMP infrastructure

The project is currently focused on demonstrating a complete exchange workflow from the user interface through the backend and database.

---

# Features

### Authentication

* User login
* JWT-based authentication
* Password hashing with BCrypt
* Protected backend endpoints
* Authentication token stored by the frontend
* Authenticated order placement

### Trading

* Buy and sell orders
* Limit orders
* Market orders
* Order matching
* Partial order fills
* Filled and open orders
* Order cancellation
* Remaining quantity tracking
* Idempotency keys for order submission

### Wallets

* Account wallets
* Asset balances
* Deposits
* Balance updates after trading
* Persistent wallet data

### Order Book

The application maintains an exchange-style order book containing:

* Buy orders
* Sell orders
* Prices
* Quantities
* Open orders
* Matching orders

### Trade History

Executed orders generate trades that can be displayed as recent market executions.

The trading interface displays:

* Execution price
* Quantity
* Trade time

### Market Data

The project includes a Python market-data service that provides simulated cryptocurrency market data.

The application currently uses simulated **BTC-USD** market data.

The dashboard includes:

* Current market price
* Price changes
* Simulated candles
* Multiple time intervals
* Moving averages
* Volume information

### WebSockets

WebSocket/STOMP infrastructure is included to support real-time communication between the backend and frontend.

This provides the foundation for live:

* Market updates
* Order book updates
* Trade updates
* Trading events

### Trading Dashboard

The React frontend provides an exchange-style interface containing:

* Market header
* BTC-USD market information
* Trading chart
* Moving averages
* Order book
* Recent trades
* Order form
* My Orders
* Buy/Sell controls
* Limit/Market order selection
* Authentication controls
* Open order management
* OpenEx AI assistant

---

# Application Structure

The project is divided into several main parts:

```text
Openx3.0/
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   ├── Login.jsx
│   │   │   ├── OrderForm.jsx
│   │   │   ├── MyOrders.jsx
│   │   │   └── MarketChart.jsx
│   │   │
│   │   ├── hooks/
│   │   │   └── useMarketData.js
│   │   │
│   │   ├── App.jsx
│   │   └── App.css
│   │
│   ├── package.json
│   └── vite.config.js
│
├── python-service/
│   ├── app.py
│   └── market_simulator.py
│
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/openex/core/
│   │   │       ├── api/
│   │   │       ├── config/
│   │   │       ├── domain/
│   │   │       ├── repository/
│   │   │       └── service/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

# Technology Stack

## Backend

* Kotlin
* Spring Boot
* Spring Security
* Spring Data JPA
* Hibernate
* JWT
* BCrypt
* WebSockets / STOMP
* Gradle

## Database

* PostgreSQL
* Flyway migrations
* Docker

## Frontend

* React
* Vite
* JavaScript
* CSS
* Fetch API
* Recharts / charting components

## Market Data

* Python
* Flask
* Simulated market data
* Technical indicators

## Development Tools

* Git
* GitHub
* Docker Desktop
* IntelliJ IDEA / VS Code
* Gradle
* npm

---

# Backend Architecture

The backend follows a layered architecture.

```text
React Frontend
       │
       │ HTTP / WebSocket
       ▼
Spring Boot API
       │
       ├── Controllers
       │
       ├── Services
       │
       ├── Repositories
       │
       └── Security / JWT
       │
       ▼
PostgreSQL
```

The backend is responsible for:

* Authentication
* Account management
* Wallet management
* Order processing
* Order matching
* Trade execution
* Order book management
* Database persistence
* API security
* WebSocket communication

---

# Frontend Architecture

The frontend is built with React and Vite.

```text
React Application
       │
       ├── Login
       │
       ├── Market Chart
       │
       ├── Order Book
       │
       ├── Order Form
       │
       ├── My Orders
       │
       ├── Recent Trades
       │
       └── AI Assistant
              │
              ▼
        Spring Boot API
```

The frontend communicates with the backend using HTTP requests and authentication tokens.

---

# Trading Flow

A typical trading flow works like this:

```text
1. User signs in
        │
        ▼
2. Backend validates credentials
        │
        ▼
3. JWT token is returned
        │
        ▼
4. Frontend stores authentication token
        │
        ▼
5. User selects BUY or SELL
        │
        ▼
6. User enters price and quantity
        │
        ▼
7. Frontend sends order to backend
        │
        ▼
8. Backend validates the order
        │
        ▼
9. Order enters the matching engine
        │
        ▼
10. Matching orders are found
        │
        ▼
11. Trade is executed
        │
        ▼
12. Wallets and order balances are updated
        │
        ▼
13. Trade appears in trade history
```

---

# Authentication Flow

OpenEx uses JWT authentication.

```text
User
 │
 │ email + password
 ▼
Login API
 │
 │ validate credentials
 ▼
JWT Token
 │
 ▼
Frontend
 │
 │ Authorization: Bearer <token>
 ▼
Protected API
```

The JWT contains the authenticated account information and is validated by the backend security filter before protected requests are processed.

---

# Database

PostgreSQL is used as the persistent database for the application.

The database stores important exchange information including:

* Accounts
* Wallets
* Orders
* Trades
* Ledger entries
* Flyway migration history

Database schema changes are managed using **Flyway migrations**.

PostgreSQL can be run locally through Docker for development.

---

# API Examples

### Login

```http
POST /api/auth/login
```

Example request:

```json
{
  "email": "alice@openex.test",
  "password": "password"
}
```

### Create Order

```http
POST /orders
```

Example:

```json
{
  "symbol": "BTC-USD",
  "side": "SELL",
  "type": "LIMIT",
  "price": 3000,
  "quantity": 0.1
}
```

Authenticated requests use:

```http
Authorization: Bearer <JWT_TOKEN>
```

An idempotency key is also supplied when creating an order to help prevent accidental duplicate submissions.

---

# Running the Project

## 1. Start PostgreSQL

Make sure Docker Desktop is running and the PostgreSQL container is available.

Example:

```bash
docker ps
```

---

## 2. Start the Backend

From the project root:

```bash
./gradlew bootRun
```

The Spring Boot backend runs on:

```text
http://localhost:8080
```

---

## 3. Start the Frontend

Open another terminal:

```bash
cd frontend
npm install
npm run dev
```

The Vite development server will provide the frontend URL shown in the terminal, normally:

```text
http://localhost:5173
```

---

## 4. Start the Python Market Service

From the project root:

```bash
cd python-service
python app.py
```

The Python service provides simulated market data for the trading dashboard.

---

# Testing

Backend tests can be run with:

```bash
./gradlew test
```

A successful build should finish with:

```text
BUILD SUCCESSFUL
```

Frontend production build:

```bash
cd frontend
npm run build
```

The production build is generated in:

```text
frontend/dist/
```

---

# Example Trading Scenario

The project includes development account data that can be used to test the trading workflow.

Example development account:

```text
Email:    alice@openex.test
Password: password
```

A basic test workflow is:

```text
Login
  ↓
Open trading dashboard
  ↓
Select BTC-USD
  ↓
Choose Buy or Sell
  ↓
Enter price
  ↓
Enter quantity
  ↓
Place order
  ↓
Order enters the exchange
  ↓
Order is matched or remains open
  ↓
Trade/wallet balances are updated
```

---

# Development Progress

### Day 15

The project currently includes the following integrated functionality:

* Full-stack application structure
* PostgreSQL persistence
* Flyway database migrations
* Account management
* Wallet management
* JWT authentication
* BCrypt password verification
* Protected API requests
* Order creation
* Buy and sell functionality
* Limit and market orders
* Order matching
* Partial fills
* Order cancellation
* Trade history
* Order book
* Simulated market data
* Python market-data service
* React/Vite trading dashboard
* Market chart
* Moving averages
* My Orders interface
* WebSocket/STOMP infrastructure
* CORS configuration
* Health endpoint
* Idempotent order submission
* Backend and frontend build verification

---

# Project Goal

The goal of OpenEx is to demonstrate the development of a realistic full-stack financial application while gaining practical experience with:

* Backend development
* Frontend development
* Database design
* REST APIs
* Authentication and authorization
* Financial transaction logic
* Order matching
* Real-time communication
* Python services
* Docker
* Automated testing
* Git and GitHub

The project is designed as a **learning and portfolio project** and uses simulated market data rather than connecting to a real cryptocurrency exchange.

---

# Disclaimer

OpenEx is an educational trading simulation.

It does **not** execute real cryptocurrency transactions, manage real funds, or connect to a live financial exchange.

All market prices and trading activity are simulated for development and demonstration purposes.

