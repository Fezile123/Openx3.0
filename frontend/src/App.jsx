import { useState } from "react"
import AIAssistant from "./components/AIAssistant"
import "./App.css"
import { useMarketData } from "./hooks/useMarketData"
import { OrderForm } from "./components/OrderForm"
import { MyOrders } from "./components/MyOrders"

const SYMBOL = "BTC-USD"

// Temporary demo account.
// Later this will come from authentication/login.
const ACCOUNT_ID = "11111111-1111-1111-1111-111111111111"

function Header({ connected }) {
  return (
    <header className="app-header">
      <div className="header-brand">
        <div className="brand-mark">O</div>

        <div>
          <h1>OpenEx</h1>
          <span className="tagline">Digital Asset Exchange</span>
        </div>
      </div>

      <div className="market-summary">
        <div className="market-symbol">
          <span className="coin-icon">₿</span>
          <div>
            <strong>{SYMBOL}</strong>
            <span>Bitcoin / US Dollar</span>
          </div>
        </div>

        <div
          className={`connection-status ${
            connected ? "connected" : "disconnected"
          }`}
        >
          <span className="connection-dot" />
          {connected ? "Market Live" : "Connecting..."}
        </div>
      </div>
    </header>
  )
}

function SectionHeader({ title, subtitle, action }) {
  return (
    <div className="panel-header">
      <div>
        <h2>{title}</h2>
        {subtitle && (
          <span className="panel-subtitle">{subtitle}</span>
        )}
      </div>

      {action && (
        <span className="panel-action">{action}</span>
      )}
    </div>
  )
}

function OrderBookPanel({ orderBook }) {
  const { bids, asks } = orderBook

  return (
    <section className="panel order-book-panel">
      <SectionHeader
        title="Order Book"
        subtitle={SYMBOL}
        action="Live"
      />

      <div className="book-column-header">
        <span>Price (USD)</span>
        <span>Quantity (BTC)</span>
      </div>

      {bids.length === 0 && asks.length === 0 ? (
        <div className="placeholder">
          No open orders yet
        </div>
      ) : (
        <div className="order-book">

          <div className="book-side asks">
            {asks
              .slice()
              .reverse()
              .map((level, i) => (
                <div
                  key={`ask-${i}`}
                  className="book-row ask-row"
                >
                  <span className="price">
                    {Number(level.price).toFixed(2)}
                  </span>

                  <span className="qty">
                    {Number(level.quantity).toFixed(4)}
                  </span>
                </div>
              ))}
          </div>

          <div className="spread-row">
            <span>Spread</span>
            <span>Market</span>
          </div>

          <div className="book-side bids">
            {bids.map((level, i) => (
              <div
                key={`bid-${i}`}
                className="book-row bid-row"
              >
                <span className="price">
                  {Number(level.price).toFixed(2)}
                </span>

                <span className="qty">
                  {Number(level.quantity).toFixed(4)}
                </span>
              </div>
            ))}
          </div>

        </div>
      )}
    </section>
  )
}

function OrderFormPanel({ symbol }) {
  return (
    <section className="panel order-form-panel">
      <SectionHeader
        title="Place Order"
        subtitle={`Trade ${symbol}`}
      />

      <OrderForm symbol={symbol} />
    </section>
  )
}

function MyOrdersPanel() {
  return (
    <section className="panel my-orders-panel">
      <SectionHeader
        title="My Open Orders"
        subtitle="Active orders"
      />

      <MyOrders accountId={ACCOUNT_ID} />
    </section>
  )
}

function TradeHistoryPanel({ trades }) {
  return (
    <section className="panel trade-history-panel">
      <SectionHeader
        title="Recent Trades"
        subtitle="Latest executions"
      />

      <div className="trade-column-header">
        <span>Price</span>
        <span>Quantity</span>
        <span>Time</span>
      </div>

      {trades.length === 0 ? (
        <div className="placeholder">
          No trades yet
        </div>
      ) : (
        <div className="trade-list">
          {trades.map((trade) => (
            <div
              key={trade.id}
              className="trade-row"
            >
              <span className="price">
                {Number(trade.price).toFixed(2)}
              </span>

              <span className="qty">
                {Number(trade.quantity).toFixed(4)}
              </span>

              <span className="time">
                {new Date(
                  trade.executedAt
                ).toLocaleTimeString()}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function App() {
  const {
    orderBook,
    trades,
    connected,
  } = useMarketData(SYMBOL)

  const [aiOpen, setAiOpen] = useState(false)

  return (
    <div className="app">

      <Header connected={connected} />

      <main className="dashboard">

        {/* Main market section */}
        <OrderBookPanel orderBook={orderBook} />

        {/* Trading section */}
        <OrderFormPanel symbol={SYMBOL} />

        {/* User activity */}
        <MyOrdersPanel />

        {/* Trade history */}
        <TradeHistoryPanel trades={trades} />

      </main>

      {/* Floating AI chatbot */}
      <AIAssistant
        isOpen={aiOpen}
        onToggle={() => setAiOpen((current) => !current)}
      />

    </div>
  )
}

export default App