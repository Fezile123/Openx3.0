import { useEffect, useRef, useState } from 'react'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

const API_URL = 'http://localhost:8080'
const WS_URL = `${API_URL}/ws`

function getAuthHeaders() {
  const token = localStorage.getItem('openex_token')

  return token
    ? {
        Authorization: `Bearer ${token}`,
      }
    : {}
}

/**
 * Connects to the OpenEx WebSocket server and subscribes to live order
 * book and trade updates for a single symbol.
 *
 * Historical trades are loaded from the REST API first.
 * New trades then arrive through WebSocket and are added to the top.
 */
export function useMarketData(symbol) {
  const [orderBook, setOrderBook] = useState({
    bids: [],
    asks: [],
  })

  const [trades, setTrades] = useState([])
  const [connected, setConnected] = useState(false)

  const clientRef = useRef(null)

  useEffect(() => {
    let cancelled = false

    // Load existing trades from the database
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

    loadHistoricalTrades()

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),

      reconnectDelay: 3000,

      onConnect: () => {
        setConnected(true)

        // Live order book updates
        client.subscribe(
          `/topic/orderbook/${symbol}`,
          (message) => {
            try {
              const snapshot = JSON.parse(message.body)

              if (!cancelled) {
                setOrderBook(snapshot)
              }
            } catch (error) {
              console.error(
                'Failed to parse order book update:',
                error
              )
            }
          }
        )

        // Live trade updates
        client.subscribe(
          `/topic/trades/${symbol}`,
          (message) => {
            try {
              const trade = JSON.parse(message.body)

              if (!cancelled) {
                setTrades((prev) => {
                  // Prevent duplicate trades
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

        // Request the current order book immediately
        // through the REST endpoint so the UI does not
        // have to wait for the next WebSocket event.
        fetch(
          `${API_URL}/orderbook?symbol=${encodeURIComponent(symbol)}`
        )
          .then((response) => {
            if (!response.ok) {
              throw new Error(
                `Failed to load order book: ${response.status}`
              )
            }

            return response.json()
          })
          .then((snapshot) => {
            if (!cancelled) {
              setOrderBook(snapshot)
            }
          })
          .catch((error) => {
            console.error(
              'Failed to load initial order book:',
              error
            )
          })
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
      client.deactivate()
    }
  }, [symbol])

  return {
    orderBook,
    trades,
    connected,
  }
}

