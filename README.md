# OpenEx

## Digital Asset Exchange

OpenEx is a full-stack digital asset exchange simulation built with **Kotlin, Spring Boot, PostgreSQL, React, Vite, Python, WebSockets, and JWT authentication**.

The project demonstrates the core components of a modern trading platform, including user authentication, wallets, order placement, order matching, order books, trades, market data, and real-time updates.

---

## Project Status

**Day 15 — Full-Stack Trading Platform Integration**

### Current Features

- JWT authentication
- Secure login flow
- Development account authentication
- PostgreSQL database
- Flyway database migrations
- Wallet management
- Account management
- Buy and sell orders
- Limit orders
- Market orders
- Order matching
- Partial order fills
- Order cancellation
- Open order tracking
- Trade history
- Order book
- WebSocket/STOMP infrastructure
- Real-time trading updates
- Simulated BTC-USD market data
- Moving averages
- Python market-data service
- React trading dashboard
- Responsive trading interface
- AI assistant integration

---

## Technology Stack

### Backend

- Kotlin
- Spring Boot
- Spring Data JPA
- Spring Security
- JWT
- Hibernate
- Gradle
- Flyway

### Database

- PostgreSQL

### Frontend

- React
- Vite
- JavaScript
- CSS
- STOMP
- SockJS

### Python Services

- Python
- Flask
- Pandas
- NumPy
- LangChain
- Ollama

---

## Architecture

```text
                         OpenEx
                    Digital Asset Exchange
                              |
          +-------------------+-------------------+
          |                   |                   |
          v                   v                   v
    React Frontend       Spring Boot API     Python Service
       Vite/React         Kotlin/Java            Flask
          |                   |                   |
          |                   v                   |
          |              PostgreSQL               |
          |                   |                   |
          |          +--------+--------+          |
          |          |        |        |          |
          |          v        v        v          |
          |       Accounts  Wallets  Orders        |
          |                            |            |
          |                            v            |
          |                          Trades         |
          |                                         |
          +---------- REST / WebSocket -------------+
