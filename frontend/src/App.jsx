import './App.css'

function Header() {
  return (
    <header className="app-header">
      <h1>OpenEx</h1>
      <span className="tagline">BTC-USD</span>
    </header>
  )
}

function OrderBookPanel() {
  return (
    <section className="panel order-book-panel">
      <h2>Order Book</h2>
      <div className="placeholder">Order book data will appear here (Day 9-10)</div>
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

function TradeHistoryPanel() {
  return (
    <section className="panel trade-history-panel">
      <h2>Recent Trades</h2>
      <div className="placeholder">Live trade feed will appear here (Day 9-10)</div>
    </section>
  )
}

function App() {
  return (
    <div className="app">
      <Header />
      <main className="dashboard">
        <OrderBookPanel />
        <OrderFormPanel />
        <TradeHistoryPanel />
      </main>
    </div>
  )
}

export default App