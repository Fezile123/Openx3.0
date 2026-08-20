import AIAssistant from "./components/AIAssistant"
import "./App.css"
import { useMarketData } from "./hooks/useMarketData"
import { OrderForm } from "./components/OrderForm"
import { MyOrders } from "./components/MyOrders"

const SYMBOL = "BTC-USD"

// Temporary demo account.
// Later this will come from authentication/login.
const ACCOUNT_ID =
  "11111111-1111-1111-1111-111111111111"

function Header({ connected }) {
  return (
    <header className="app-header">
      <div className="header-brand">
        <div className="brand-mark">
          O
        </div>

        <div>
          <h1>OpenEx</h1>
          <p>Digital Asset Exchange</p>
        </div>
      </div>

      <div className="header-market">
        <div className="market-icon">
          ₿
        </div>

        <div>
          <strong>{SYMBOL}</strong>
          <span>Bitcoin / US Dollar</span>
        </div>
      </div>

      <div
        className={`market-status ${
          connected
            ? "connected"
            : "disconnected"
        }`}
      >
        <span className="status-indicator" />

        <div>
          <strong>
            {connected
              ? "Market Live"
              : "Connecting"}
          </strong>

          <span>
            {connected
              ? "Real-time data"
              : "Waiting for server"}
          </span>
        </div>
      </div>
    </header>
  )
}

function PanelHeader({
  title,
  subtitle,
  action,
}) {
  return (
    <div className="panel-header">
      <div>
        <h2>{title}</h2>

        {subtitle && (
          <span className="panel-subtitle">
            {subtitle}
          </span>
        )}
      </div>

      {action}
    </div>
  )
}

function OrderBookPanel({ orderBook }) {
  const { bids, asks } = orderBook

  return (
    <section className="panel order-book-panel">
      <PanelHeader
        title="Order Book"
        subtitle={SYMBOL}
        action={
          <span className="live-badge">
            <span />
            Live
          </span>
        }
      />

      <div className="book-columns">
        <span>Price (USD)</span>
        <span>Quantity (BTC)</span>
      </div>

      {bids.length === 0 && asks.length === 0 ? (
        <div className="placeholder">
          <div className="placeholder-icon">
            ◌
          </div>

          <strong>No open orders yet</strong>

          <span>
            Orders will appear here when available.
          </span>
        </div>
      ) : (
        <div className="order-book">
          {/* ASK SIDE */}
          <div className="book-side asks">
            {asks
              .slice()
              .reverse()
              .map((level, index) => (
                <div
                  key={`${level.price}-${index}`}
                  className="book-row ask-row"
                >
                  <span className="price">
                    {Number(level.price).toFixed(2)}
                  </span>

                  <span className="qty">
                    {Number(
                      level.quantity
                    ).toFixed(4)}
                  </span>
                </div>
              ))}
          </div>

          <div className="spread-row">
            <span>Spread</span>
            <span>—</span>
          </div>

          {/* BID SIDE */}
          <div className="book-side bids">
            {bids.map((level, index) => (
              <div
                key={`${level.price}-${index}`}
                className="book-row bid-row"
              >
                <span className="price">
                  {Number(level.price).toFixed(2)}
                </span>

                <span className="qty">
                  {Number(
                    level.quantity
                  ).toFixed(4)}
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
      <PanelHeader
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
      <PanelHeader
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
      <PanelHeader
        title="Recent Trades"
        subtitle="Latest executions"
      />

      {trades.length === 0 ? (
        <div className="placeholder compact">
          <strong>No trades yet</strong>

          <span>
            Completed trades will appear here.
          </span>
        </div>
      ) : (
        <div className="trade-table">
          <div className="trade-table-header">
            <span>Price</span>
            <span>Quantity</span>
            <span>Time</span>
          </div>

          <div className="trade-list">
            {trades.map((trade) => (
              <div
                key={trade.id}
                className="trade-row"
              >
                <span className="price">
                  {Number(
                    trade.price
                  ).toFixed(2)}
                </span>

                <span className="qty">
                  {Number(
                    trade.quantity
                  ).toFixed(4)}
                </span>

                <span className="time">
                  {new Date(
                    trade.executedAt
                  ).toLocaleTimeString()}
                </span>
              </div>
            ))}
          </div>
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

  return (
    <div className="app">
      <Header connected={connected} />

      <main className="dashboard">
        <OrderBookPanel
          orderBook={orderBook}
        />

        <OrderFormPanel
          symbol={SYMBOL}
        />

        <MyOrdersPanel />

        <TradeHistoryPanel
          trades={trades}
        />
      </main>

      {/* Floating chatbot */}
      <AIAssistant />
    </div>
  )
}

export default App