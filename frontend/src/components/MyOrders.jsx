import { useEffect, useState } from 'react'

const API_URL = 'http://localhost:8080/orders'

export function MyOrders({ accountId }) {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [cancelling, setCancelling] = useState(null)

  async function loadOrders() {
    try {
      setError(null)

      const response = await fetch(
        `${API_URL}?accountId=${accountId}`
      )

      if (!response.ok) {
        throw new Error('Could not load orders')
      }

      const data = await response.json()
      setOrders(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!accountId) return

    loadOrders()

    // Refresh orders so fills/cancellations are reflected
    // without requiring a page refresh.
    const interval = setInterval(loadOrders, 2000)

    return () => clearInterval(interval)
  }, [accountId])

  async function cancelOrder(orderId) {
    try {
      setCancelling(orderId)
      setError(null)

      const response = await fetch(
        `${API_URL}/${orderId}?accountId=${accountId}`,
        {
          method: 'DELETE'
        }
      )

      const data = await response.json()

      if (!response.ok) {
        throw new Error(
          data.error || 'Could not cancel order'
        )
      }

      await loadOrders()
    } catch (err) {
      setError(err.message)
    } finally {
      setCancelling(null)
    }
  }

  const openOrders = orders.filter(
    (order) =>
      order.status === 'OPEN' ||
      order.status === 'PARTIALLY_FILLED'
  )

  if (loading) {
    return (
      <section className="panel my-orders-panel">
        <h2>My Open Orders</h2>

        <div className="placeholder">
          Loading orders...
        </div>
      </section>
    )
  }

  return (
    <section className="panel my-orders-panel">
      <h2>My Open Orders</h2>

      {error && (
        <div className="form-error">
          {error}
        </div>
      )}

      {openOrders.length === 0 ? (
        <div className="placeholder">
          No open orders
        </div>
      ) : (
        <div className="my-orders-list">
          {openOrders.map((order) => (
            <div
              key={order.id}
              className="my-order-row"
            >
              <div className="order-info">

                <div
                  className={`order-side ${order.side.toLowerCase()}`}
                >
                  {order.side}
                </div>

                <div className="order-details">
                  <strong>{order.symbol}</strong>

                  <span>
                    {order.type} ·{' '}
                    {order.price != null
                      ? Number(order.price).toFixed(2)
                      : 'Market'}
                  </span>

                  <span>
                    Qty:{' '}
                    {Number(
                      order.remainingQuantity
                    ).toFixed(4)}
                  </span>

                  <span>
                    Status: {order.status}
                  </span>
                </div>

              </div>

              <button
                type="button"
                className="cancel-btn"
                onClick={() => cancelOrder(order.id)}
                disabled={cancelling === order.id}
              >
                {cancelling === order.id
                  ? 'Cancelling...'
                  : 'Cancel'}
              </button>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}