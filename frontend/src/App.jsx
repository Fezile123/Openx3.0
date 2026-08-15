import './App.css'
import { useMarketData } from './hooks/useMarketData'

const SYMBOL = 'BTC-USD'

function Header({ connected }) {
  return (
    <header className="app-header">
      <h1>OpenEx</h1>
      <span className="tagline">{SYMBOL}</span>
      <span className={`status-dot ${connected ? 'connected' : 'disconnected'}`}>
        {connected ? '● Live' : '○ Connecting...'}
      </span>
    </header>
  )
}

function OrderBookPanel({ orderBook }) {
  const { bids, asks } = orderBook

  return (
    <section className="panel order-book-panel">
      <h2>Order Book</h2>
      {bids.length === 0 && asks.length === 0 ? (
        <div className="placeholder">No open orders yet</div>
      ) : (
        <div className="order-book">
          <div className="book-side asks">
            {asks.slice().reverse().map((level, i) => (
              <div key={i} className="book-row ask-row">
                <span className="price">{Number(level.price).toFixed(2)}</span>
                <span className="qty">{Number(level.quantity).toFixed(4)}</span>
              </div>
            ))}
          </div>
          <div className="book-side bids">
            {bids.map((level, i) => (
              <div key={i} className="book-row bid-row">
                <span className="price">{Number(level.price).toFixed(2)}</span>
                <span className="qty">{Number(level.quantity).toFixed(4)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}

function OrderFormPanel() {
  return (
    <section className="panel order-form-panel">
      <h2>Place Order</h2>
      <div className="placeholder">Buy/Sell form will appear here (Day 11)</div>
    </section>
  )
}

function TradeHistoryPanel({ trades }) {
  return (
    <section className="panel trade-history-panel">
      <h2>Recent Trades</h2>
      {trades.length === 0 ? (
        <div className="placeholder">No trades yet</div>
      ) : (
        <div className="trade-list">
          {trades.map((trade) => (
            <div key={trade.id} className="trade-row">
              <span className="price">{Number(trade.price).toFixed(2)}</span>
              <span className="qty">{Number(trade.quantity).toFixed(4)}</span>
              <span className="time">
                {new Date(trade.executedAt).toLocaleTimeString()}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function App() {
  const { orderBook, trades, connected } = useMarketData(SYMBOL)

  return (
    <div className="app">
      <Header connected={connected} />
      <main className="dashboard">
        <OrderBookPanel orderBook={orderBook} />
        <OrderFormPanel />
        <TradeHistoryPanel trades={trades} />
      </main>
    </div>
  )
}

export default App