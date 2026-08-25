import { useEffect, useState } from 'react'

const API_URL = 'http://localhost:8080/orders'

function getAuthHeaders() {
  const token = localStorage.getItem('openex_token')

  return token
    ? {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      }
    : {
        'Content-Type': 'application/json',
      }
}

export function MyOrders({ accountId }) {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [cancelling, setCancelling] = useState(null)

  async function loadOrders() {
    try {
      setError(null)

      const response = await fetch(
        `${API_URL}?accountId=${accountId}`,
        {
          headers: getAuthHeaders(),
        }
      )

      if (!response.ok) {
        throw new Error(
          `Could not load orders (${response.status})`
        )
      }

      const data = await response.json()
      setOrders(data)
    } catch (err) {
      console.error('Failed to load orders:', err)
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!accountId) return

    loadOrders()

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
          method: 'DELETE',
          headers: getAuthHeaders(),
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
      console.error('Failed to cancel order:', err)
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
      <div className="placeholder">
        Loading orders...
      </div>
    )
  }

  return (
    <>
      <div className="orders-summary">
        <span className="order-count">
          {openOrders.length} ACTIVE
        </span>
      </div>

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
          {openOrders.map((order) => {
            const totalQuantity = Number(order.quantity)
            const remainingQuantity = Number(
              order.remainingQuantity
            )

            const filledQuantity = Math.max(
              0,
              totalQuantity - remainingQuantity
            )

            const filledPercentage =
              totalQuantity > 0
                ? (filledQuantity / totalQuantity) * 100
                : 0

            return (
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

                    <div className="order-main">
                      <strong>{order.symbol}</strong>

                      <span className="order-type">
                        {order.type}

                        {order.price != null &&
                          ` · ${Number(
                            order.price
                          ).toFixed(2)}`}
                      </span>
                    </div>

                    <div className="order-quantity">
                      <span>
                        Filled: {filledQuantity.toFixed(4)}
                      </span>

                      <span>
                        Remaining:{' '}
                        {remainingQuantity.toFixed(4)}
                      </span>

                      <span>
                        Total: {totalQuantity.toFixed(4)}
                      </span>
                    </div>

                    <div className="fill-progress">
                      <div className="fill-progress-track">
                        <div
                          className="fill-progress-bar"
                          style={{
                            width: `${Math.min(
                              filledPercentage,
                              100
                            )}%`,
                          }}
                        />
                      </div>

                      <span>
                        {filledPercentage.toFixed(0)}% filled
                      </span>
                    </div>

                    <div
                      className={`order-status ${order.status.toLowerCase()}`}
                    >
                      {order.status === 'PARTIALLY_FILLED'
                        ? 'PARTIALLY FILLED'
                        : 'OPEN'}
                    </div>

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
            )
          })}
        </div>
      )}
    </>
  )
}