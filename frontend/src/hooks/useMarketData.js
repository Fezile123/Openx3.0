import { useEffect, useRef, useState } from 'react'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

const API_URL = 'http://localhost:8080'
const SIMULATOR_URL = 'http://localhost:5000'

function getAuthHeaders() {
  const token = localStorage.getItem('openex_token')

  return token
    ? {
        Authorization: `Bearer ${token}`,
      }
    : {}
}

/**
 * Market data hook.
 *
 * Order book:
 *   Comes from the Python market simulator.
 *
 * Trades:
 *   Comes from the Spring Boot WebSocket/database.
 */
export function useMarketData(symbol) {
  const [orderBook, setOrderBook] = useState({
    bids: [],
    asks: [],
    midPrice: null,
    bestBid: null,
    bestAsk: null,
    spread: null,
  })

  const [trades, setTrades] = useState([])
  const [connected, setConnected] = useState(false)

  const clientRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    let orderBookTimer = null

    /**
     * Load simulated order book from Python.
     */
    async function loadSimulatedOrderBook() {
      try {
        const response = await fetch(
          `${SIMULATOR_URL}/api/order-book?symbol=${encodeURIComponent(symbol)}`
        )

        if (!response.ok) {
          throw new Error(
            `Failed to load simulated order book: ${response.status}`
          )
        }

        const snapshot = await response.json()

        if (!cancelled) {
          setOrderBook(snapshot)
        }
      } catch (error) {
        console.error(
          'Failed to load simulated order book:',
          error
        )
      }
    }

    /**
     * Load existing trades from Spring Boot.
     */
    async function loadHistoricalTrades() {
      try {
        const response = await fetch(
          `${API_URL}/trades?symbol=${encodeURIComponent(symbol)}`,
          {
            headers: getAuthHeaders(),
          }
        )

        if (!response.ok) {
          throw new Error(
            `Failed to load trades: ${response.status}`
          )
        }

        const historicalTrades = await response.json()

        if (!cancelled) {
          setTrades(
            historicalTrades
              .slice(0, 50)
              .sort(
                (a, b) =>
                  new Date(b.executedAt) -
                  new Date(a.executedAt)
              )
          )
        }
      } catch (error) {
        console.error(
          'Failed to load historical trades:',
          error
        )
      }
    }

    /**
     * Initial data.
     */
    loadSimulatedOrderBook()
    loadHistoricalTrades()

    /**
     * Refresh the simulated order book every 3 seconds.
     */
    orderBookTimer = setInterval(() => {
      loadSimulatedOrderBook()
    }, 3000)

    /**
     * Connect to Spring Boot WebSocket.
     *
     * We keep this connection for LIVE TRADES.
     *
     * We intentionally DO NOT subscribe to:
     *
     * /topic/orderbook/${symbol}
     *
     * because the order book now comes from Python.
     */
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_URL}/ws`),

      reconnectDelay: 3000,

      onConnect: () => {
        if (!cancelled) {
          setConnected(true)
        }

        /**
         * Live trade updates.
         */
        client.subscribe(
          `/topic/trades/${symbol}`,
          (message) => {
            try {
              const trade = JSON.parse(message.body)

              if (!cancelled) {
                setTrades((prev) => {
                  const exists = prev.some(
                    (existingTrade) =>
                      existingTrade.id === trade.id
                  )

                  if (exists) {
                    return prev
                  }

                  return [trade, ...prev].slice(0, 50)
                })
              }
            } catch (error) {
              console.error(
                'Failed to parse trade update:',
                error
              )
            }
          }
        )
      },

      onDisconnect: () => {
        if (!cancelled) {
          setConnected(false)
        }
      },

      onStompError: (frame) => {
        console.error(
          'STOMP error:',
          frame.headers['message']
        )
      },

      onWebSocketError: (error) => {
        console.error(
          'WebSocket error:',
          error
        )
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      cancelled = true

      if (orderBookTimer) {
        clearInterval(orderBookTimer)
      }

      client.deactivate()
    }
  }, [symbol])

  return {
    orderBook,
    trades,
    connected,
  }
}
