import MarketChart from "./components/MarketChart"
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

function Header({ connected, latestPrice }) {
  const hasPrice =
    latestPrice !== null &&
    latestPrice !== undefined &&
    Number.isFinite(Number(latestPrice))

  return (
    <header className="app-header">

      {/* Brand */}
      <div className="header-brand">
        <div className="brand-mark">
          O
        </div>

        <div>
          <h1>OpenEx</h1>
          <p>Digital Asset Exchange</p>
        </div>
      </div>

      {/* Market */}
      <div className="header-market">
        <div className="market-icon">
          ₿
        </div>

        <div>
          <strong>{SYMBOL}</strong>
          <span>Bitcoin / US Dollar</span>
        </div>
      </div>

      {/* Market status */}
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
            {hasPrice
              ? `$${Number(latestPrice).toLocaleString(
                  "en-US",
                  {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  }
                )}`
              : connected
                ? "Waiting for price"
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
  const bids = orderBook?.bids || []
  const asks = orderBook?.asks || []

  /*
   * Backend:
   *
   * asks -> lowest price first
   * bids -> highest price first
   *
   * Exchange-style display:
   *
   * ASK
   * highest ask
   * ...
   * best / lowest ask
   *
   * SPREAD
   *
   * best / highest bid
   * ...
   * lowest bid
   */

  const sortedAsks = asks
    .slice()
    .sort(
      (a, b) =>
        Number(b.price) -
        Number(a.price)
    )

  const sortedBids = bids
    .slice()
    .sort(
      (a, b) =>
        Number(b.price) -
        Number(a.price)
    )

  const bestAsk =
    asks.length > 0
      ? Math.min(
          ...asks.map((level) =>
            Number(level.price)
          )
        )
      : null

  const bestBid =
    bids.length > 0
      ? Math.max(
          ...bids.map((level) =>
            Number(level.price)
          )
        )
      : null

  const spread =
    bestAsk !== null &&
    bestBid !== null
      ? bestAsk - bestBid
      : null

  const spreadPercentage =
    bestAsk !== null &&
    bestBid !== null &&
    bestAsk !== 0
      ? (spread / bestAsk) * 100
      : null

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

      {bids.length === 0 &&
      asks.length === 0 ? (
        <div className="placeholder">
          <div className="placeholder-icon">
            ◌
          </div>

          <strong>
            No open orders yet
          </strong>

          <span>
            Orders will appear here
            when available.
          </span>
        </div>
      ) : (
        <div className="order-book">

          {/* =========================
              ASK SIDE
          ========================== */}

          <div className="book-side asks">
            {sortedAsks.length === 0 ? (
              <div className="book-empty">
                No sell orders
              </div>
            ) : (
              sortedAsks.map(
                (level, index) => {
                  const price =
                    Number(level.price)

                  const isBestAsk =
                    price === bestAsk

                  return (
                    <div
                      key={`ask-${level.price}-${index}`}
                      className={`book-row ask-row ${
                        isBestAsk
                          ? "best-ask"
                          : ""
                      }`}
                    >
                      <span className="price">
                        {price.toFixed(2)}
                      </span>

                      <span className="qty">
                        {Number(
                          level.quantity
                        ).toFixed(4)}
                      </span>
                    </div>
                  )
                }
              )
            )}
          </div>

          {/* =========================
              SPREAD
          ========================== */}

          <div className="spread-row">
            <span>Spread</span>

            <span>
              {spread !== null ? (
                <>
                  ${spread.toFixed(2)}

                  {spreadPercentage !==
                    null && (
                    <small>
                      {" "}
                      (
                      {spreadPercentage.toFixed(
                        2
                      )}
                      %)
                    </small>
                  )}
                </>
              ) : (
                "—"
              )}
            </span>
          </div>

          {/* =========================
              BID SIDE
          ========================== */}

          <div className="book-side bids">
            {sortedBids.length === 0 ? (
              <div className="book-empty">
                No buy orders
              </div>
            ) : (
              sortedBids.map(
                (level, index) => {
                  const price =
                    Number(level.price)

                  const isBestBid =
                    price === bestBid

                  return (
                    <div
                      key={`bid-${level.price}-${index}`}
                      className={`book-row bid-row ${
                        isBestBid
                          ? "best-bid"
                          : ""
                      }`}
                    >
                      <span className="price">
                        {price.toFixed(2)}
                      </span>

                      <span className="qty">
                        {Number(
                          level.quantity
                        ).toFixed(4)}
                      </span>
                    </div>
                  )
                }
              )
            )}
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
    <MyOrders
      accountId={ACCOUNT_ID}
    />
  )
}

function TradeHistoryPanel({ trades }) {
  const safeTrades = Array.isArray(trades)
    ? trades
    : []

  return (
    <section className="panel trade-history-panel">
      <PanelHeader
        title="Recent Trades"
        subtitle="Latest executions"
      />

      {safeTrades.length === 0 ? (
        <div className="placeholder compact">
          <strong>
            No trades yet
          </strong>

          <span>
            Completed trades will
            appear here.
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
            {safeTrades.map(
              (trade) => {
                const price =
                  Number(trade.price)

                const quantity =
                  Number(
                    trade.quantity
                  )

                return (
                  <div
                    key={trade.id}
                    className="trade-row"
                  >
                    <span className="price">
                      {Number.isFinite(
                        price
                      )
                        ? price.toFixed(2)
                        : "—"}
                    </span>

                    <span className="qty">
                      {Number.isFinite(
                        quantity
                      )
                        ? quantity.toFixed(4)
                        : "—"}
                    </span>

                    <span className="time">
                      {trade.executedAt
                        ? new Date(
                            trade.executedAt
                          ).toLocaleTimeString(
                            [],
                            {
                              hour: "2-digit",
                              minute:
                                "2-digit",
                              second:
                                "2-digit",
                            }
                          )
                        : "—"}
                    </span>
                  </div>
                )
              }
            )}
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

  const latestPrice =
    Array.isArray(trades) &&
    trades.length > 0
      ? Number(trades[0].price)
      : null

  return (
    <div className="app">

      <Header
  connected={connected}
  latestPrice={latestPrice}
/>

<main className="dashboard">

  <OrderBookPanel
    orderBook={orderBook}
  />

  <MarketChart
    symbol={SYMBOL}
  />

  <OrderFormPanel
    symbol={SYMBOL}
  />

  <MyOrdersPanel />

  <TradeHistoryPanel
    trades={trades}
  />

</main>

      {/* Floating AI assistant */}
      <AIAssistant />

    </div>
  )
}

export default App

