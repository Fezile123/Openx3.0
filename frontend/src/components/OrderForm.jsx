import { useState } from 'react'

const API_URL = 'http://localhost:8080/orders'

export function OrderForm({ symbol, accountId }) {
  const [side, setSide] = useState('BUY')
  const [type, setType] = useState('LIMIT')
  const [price, setPrice] = useState('')
  const [quantity, setQuantity] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  function validate() {
    const qty = Number(quantity)

    if (!quantity || qty <= 0) {
      return 'Quantity must be greater than 0'
    }

    if (type === 'LIMIT') {
      const p = Number(price)

      if (!price || p <= 0) {
        return 'Price is required for limit orders'
      }
    }

    return null
  }

  async function handleSubmit(e) {
    e.preventDefault()

    setError(null)
    setSuccess(null)

    const validationError = validate()

    if (validationError) {
      setError(validationError)
      return
    }

    const token = localStorage.getItem('openex_token')

    if (!token) {
      setError('You are not authenticated. Please sign in again.')
      return
    }

    if (!accountId) {
      setError('Account ID is missing. Please sign in again.')
      return
    }

    setSubmitting(true)

    try {
      const response = await fetch(API_URL, {
        method: 'POST',

        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          'Idempotency-Key': crypto.randomUUID(),
        },

        body: JSON.stringify({
          accountId,
          symbol,
          side,
          type,
          price: type === 'LIMIT' ? Number(price) : null,
          quantity: Number(quantity),
        }),
      })

      const data = await response.json()

      if (!response.ok) {
        setError(
          data.error ||
          `Order could not be placed (${response.status})`
        )
        return
      }

      setSuccess(
        `Order ${data.status.toLowerCase()} — ${data.id.slice(0, 8)}...`
      )

      setPrice('')
      setQuantity('')
    } catch (err) {
      console.error('Order submission failed:', err)

      setError(
        'Could not reach the server. Is the backend running?'
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="order-form" onSubmit={handleSubmit}>

      <div className="side-toggle">
        <button
          type="button"
          className={
            side === 'BUY'
              ? 'side-btn buy active'
              : 'side-btn buy'
          }
          onClick={() => setSide('BUY')}
        >
          Buy
        </button>

        <button
          type="button"
          className={
            side === 'SELL'
              ? 'side-btn sell active'
              : 'side-btn sell'
          }
          onClick={() => setSide('SELL')}
        >
          Sell
        </button>
      </div>

      <label className="field">
        <span>Type</span>

        <select
          value={type}
          onChange={(e) => setType(e.target.value)}
        >
          <option value="LIMIT">Limit</option>
          <option value="MARKET">Market</option>
        </select>
      </label>

      {type === 'LIMIT' && (
        <label className="field">
          <span>Price</span>

          <input
            type="number"
            step="0.01"
            min="0"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            placeholder="0.00"
          />
        </label>
      )}

      <label className="field">
        <span>Quantity</span>

        <input
          type="number"
          step="0.0001"
          min="0"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          placeholder="0.0000"
        />
      </label>

      {error && (
        <div className="form-error">
          {error}
        </div>
      )}

      {success && (
        <div className="form-success">
          {success}
        </div>
      )}

      <button
        type="submit"
        className={
          side === 'BUY'
            ? 'submit-btn buy'
            : 'submit-btn sell'
        }
        disabled={submitting}
      >
        {submitting
          ? 'Placing...'
          : `${side === 'BUY' ? 'Buy' : 'Sell'} ${
              symbol.split('-')[0]
            }`}
      </button>

    </form>
  )
}
