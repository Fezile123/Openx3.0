import { useEffect, useRef, useState } from "react"
import MarketChart from "./components/MarketChart"
import Login from "./components/Login"
import AIAssistant from "./components/AIAssistant"
import { useMarketData } from "./hooks/useMarketData"
import { OrderForm } from "./components/OrderForm"
import { MyOrders } from "./components/MyOrders"
import "./styles.css"

const SYMBOL = "BTC-USD"

const fmt = (value, digits = 2) =>
  Number(value).toLocaleString("en-US", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })

function getTokenPayload(token) {
  try {
    const payload = token.split(".")[1]
    return JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")))
  } catch {
    return null
  }
}

/* ---------- Header ---------- */

function AccountMenu({ label, accountId, onLogout }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    function closeOnOutsideClick(event) {
      if (ref.current && !ref.current.contains(event.target)) {
        setOpen(false)
      }
    }
    document.addEventListener("mousedown", closeOnOutsideClick)
    return () => document.removeEventListener("mousedown", closeOnOutsideClick)
  }, [])

  return (
    <div className="account" ref={ref}>
      <button
        type="button"
        className="account-btn"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
      >
        <span className="avatar">{label.charAt(0).toUpperCase()}</span>
        <span className="account-name">{label}</span>
        <span className="chevron">▾</span>
      </button>

      {open && (
        <div className="account-dropdown">
          <div className="account-meta">
            <strong>{label}</strong>
            <span>ID {accountId.slice(0, 8)}…</span>
          </div>
          <button type="button" className="signout-btn" onClick={onLogout}>
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}

function Header({ connected, ticker, fallbackPrice, accountLabel, accountId, onLogout }) {
  const price = ticker ? ticker.price : fallbackPrice
  const hasPrice = price !== null && price !== undefined && Number.isFinite(Number(price))
  const isUp = ticker ? ticker.change >= 0 : true

  return (
    <header className="topbar">
      <div className="topbar-left">
        <div className="brand">
          <div className="brand-mark">O</div>
          <div className="brand-text">
            <h1>OpenEx</h1>
            <p>Digital Asset Exchange</p>
          </div>
        </div>

        <div className="pair">
          <div className="pair-icon">₿</div>
          <div className="pair-text">
            <strong>{SYMBOL}</strong>
            <span>Bitcoin / US Dollar</span>
          </div>
        </div>

        {hasPrice && (
          <div className="ticker">
            <span className="ticker-price">${fmt(price)}</span>
            {ticker && (
              <span className={`ticker-change ${isUp ? "up" : "down"}`}>
                {isUp ? "▲" : "▼"} {fmt(Math.abs(ticker.changePct))}%
              </span>
            )}
          </div>
        )}
      </div>

      <div className="topbar-right">
        <div className={`status-pill ${connected ? "on" : "off"}`}>
          <span className="status-dot" />
          {connected ? "Live" : "Connecting…"}
        </div>
        <AccountMenu label={accountLabel} accountId={accountId} onLogout={onLogout} />
      </div>
    </header>
  )
}

/* ---------- Panels ---------- */

function PanelHeader({ title, subtitle, action }) {
  return (
    <div className="panel-header">
      <div>
        <h2>{title}</h2>
        {subtitle && <span className="panel-subtitle">{subtitle}</span>}
      </div>
      {action}
    </div>
  )
}

function BookRow({ level, side, isBest, depth }) {
  return (
    <div
      className={`book-row ${side}-row ${isBest ? `best-${side}` : ""}`}
      style={{ "--depth": depth }}
    >
      <span className="price">{fmt(level.price)}</span>
      <span className="qty">{fmt(level.quantity, 4)}</span>
    </div>
  )
}

function OrderBookPanel({ orderBook }) {
  const bids = orderBook?.bids || []
  const asks = orderBook?.asks || []

  // Exchange layout: highest ask on top ... best ask, spread, best bid ... lowest bid
  const LEVELS = 8
  // Show only the levels closest to the spread, like a real exchange
  const sortedAsks = asks
    .slice()
    .sort((a, b) => Number(a.price) - Number(b.price))
    .slice(0, LEVELS)
    .reverse()
  const sortedBids = bids
    .slice()
    .sort((a, b) => Number(b.price) - Number(a.price))
    .slice(0, LEVELS)

  const bestAsk = asks.length ? Math.min(...asks.map((l) => Number(l.price))) : null
  const bestBid = bids.length ? Math.max(...bids.map((l) => Number(l.price))) : null
  const spread = bestAsk !== null && bestBid !== null ? bestAsk - bestBid : null
  const spreadPct = spread !== null && bestAsk ? (spread / bestAsk) * 100 : null

  const maxQty = Math.max(
    0,
    ...bids.map((l) => Number(l.quantity)),
    ...asks.map((l) => Number(l.quantity))
  )
  const depth = (qty) => (maxQty > 0 ? `${Math.min(100, (Number(qty) / maxQty) * 100)}%` : "0%")

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
          <div className="placeholder-icon">◌</div>
          <strong>No open orders yet</strong>
          <span>Orders will appear here when available.</span>
        </div>
      ) : (
        <div className="order-book">
          <div className="book-side">
            {sortedAsks.length === 0 ? (
              <div className="book-empty">No sell orders</div>
            ) : (
              sortedAsks.map((level, i) => (
                <BookRow
                  key={`ask-${level.price}-${i}`}
                  level={level}
                  side="ask"
                  isBest={Number(level.price) === bestAsk}
                  depth={depth(level.quantity)}
                />
              ))
            )}
          </div>

          <div className="spread-row">
            <span>Spread</span>
            <span>
              {spread !== null ? (
                <>
                  ${fmt(spread)}
                  {spreadPct !== null && <small> ({fmt(spreadPct)}%)</small>}
                </>
              ) : (
                "—"
              )}
            </span>
          </div>

          <div className="book-side">
            {sortedBids.length === 0 ? (
              <div className="book-empty">No buy orders</div>
            ) : (
              sortedBids.map((level, i) => (
                <BookRow
                  key={`bid-${level.price}-${i}`}
                  level={level}
                  side="bid"
                  isBest={Number(level.price) === bestBid}
                  depth={depth(level.quantity)}
                />
              ))
            )}
          </div>
        </div>
      )}
    </section>
  )
}

function TradeHistoryPanel({ trades }) {
  const safeTrades = (Array.isArray(trades) ? trades.slice() : []).sort(
    (a, b) => new Date(b.executedAt) - new Date(a.executedAt)
  )

  return (
    <section className="panel trade-history-panel">
      <PanelHeader title="Recent Trades" subtitle="Latest executions" />

      {safeTrades.length === 0 ? (
        <div className="placeholder compact">
          <strong>No trades yet</strong>
          <span>Completed trades will appear here.</span>
        </div>
      ) : (
        <div className="trade-table">
          <div className="trade-table-header">
            <span>Price</span>
            <span>Quantity</span>
            <span>Time</span>
          </div>

          <div className="trade-list">
            {safeTrades.map((trade, i) => {
              const price = Number(trade.price)
              const quantity = Number(trade.quantity)
              const older = safeTrades[i + 1] ? Number(safeTrades[i + 1].price) : null
              const direction = older === null || price >= older ? "up" : "down"

              return (
                <div key={trade.id} className="trade-row">
                  <span className={`price ${direction}`}>
                    {Number.isFinite(price) ? fmt(price) : "—"}
                  </span>
                  <span className="qty">
                    {Number.isFinite(quantity) ? fmt(quantity, 4) : "—"}
                  </span>
                  <span className="time">
                    {trade.executedAt
                      ? new Date(trade.executedAt).toLocaleTimeString([], {
                          hour: "2-digit",
                          minute: "2-digit",
                          second: "2-digit",
                        })
                      : "—"}
                  </span>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </section>
  )
}

function OrderFormPanel({ symbol, accountId }) {
  return (
    <section className="panel order-form-panel">
      <PanelHeader title="Place Order" subtitle={`Trade ${symbol}`} />
      <OrderForm symbol={symbol} accountId={accountId} />
    </section>
  )
}

function MyOrdersPanel({ accountId }) {
  return (
    <section className="panel my-orders-panel">
      <PanelHeader title="Open Orders" subtitle="Your active orders" />
      <MyOrders accountId={accountId} />
    </section>
  )
}

/* ---------- Pages ---------- */

function Dashboard({ payload, accountId, onLogout }) {
  const { orderBook, trades, connected } = useMarketData(SYMBOL)
  const [ticker, setTicker] = useState(null)

  const lastTradePrice =
    Array.isArray(trades) && trades.length > 0 ? Number(trades[0].price) : null

  const accountLabel = payload?.email || payload?.username || payload?.name || "My account"

  return (
    <div className="app">
      <Header
        connected={connected}
        ticker={ticker}
        fallbackPrice={lastTradePrice}
        accountLabel={accountLabel}
        accountId={accountId}
        onLogout={onLogout}
      />

      <main className="dashboard">
        <div className="col-main">
          <MarketChart symbol={SYMBOL} onTicker={setTicker} />

          <div className="row-two">
            <OrderBookPanel orderBook={orderBook} />
            <TradeHistoryPanel trades={trades} />
          </div>
        </div>

        <aside className="col-side">
          <OrderFormPanel symbol={SYMBOL} accountId={accountId} />
          <MyOrdersPanel accountId={accountId} />
        </aside>
      </main>

      <AIAssistant />
    </div>
  )
}

function App() {
  const [token, setToken] = useState(() => localStorage.getItem("openex_token"))

  const payload = token ? getTokenPayload(token) : null
  const accountId = payload?.sub || null

  function handleLogout() {
    localStorage.removeItem("openex_token")
    setToken(null)
  }

  if (!token || !accountId) {
    return <Login onLogin={(newToken) => setToken(newToken)} />
  }

  return <Dashboard payload={payload} accountId={accountId} onLogout={handleLogout} />
}

export default App