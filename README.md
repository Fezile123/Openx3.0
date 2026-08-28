# OpenEx

## Digital Asset Exchange

OpenEx is a full-stack digital asset exchange simulation designed to demonstrate how a modern cryptocurrency trading platform can be built using **Kotlin, Spring Boot, PostgreSQL, React, Vite, Python, WebSockets, and JWT authentication**.

The platform combines a secure backend, persistent trading data, simulated market data, and a responsive trading dashboard.

---

## Project Status

**Day 15 — Full-Stack Trading Platform Integration**

The project has reached a working local full-stack stage with the backend, database, Python market-data service, and React trading dashboard integrated.

### Current Features

- JWT authentication
- Secure login flow
- Development account authentication
- PostgreSQL database
- Dockerized PostgreSQL development environment
- Flyway database migrations
- Account management
- Wallet management
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
- Simulated BTC-USD market data
- Moving averages
- Python market-data service
- React trading dashboard
- Responsive trading interface
- AI assistant integration

---

# Trading Dashboard

The Day 15 dashboard is designed around a simple exchange-style trading layout.

```text
┌───────────────────────────────────────────────────────────────┐
│                     BTC-USD MARKET CHART                      │
│                                                               │
│                 Price / Moving Average Data                   │
│                                                               │
└───────────────────────────────────────────────────────────────┘

┌──────────────────────────────┬────────────────────────────────┐
│                              │                                │
│       RECENT TRADES          │          PLACE ORDER            │
│                              │                                │
│                              ├────────────────────────────────┤
│                              │          MY ORDERS              │
│                              │                                │
│                              ├────────────────────────────────┤
│                              │          ORDER BOOK              │
│                              │                                │
└──────────────────────────────┴────────────────────────────────┘
