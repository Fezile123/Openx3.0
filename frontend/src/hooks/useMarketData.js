import { useEffect, useRef, useState } from 'react'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

const WS_URL = 'http://localhost:8080/ws'

/**
 * Connects to the OpenEx WebSocket server and subscribes to live order
 * book and trade updates for a single symbol. Returns the latest snapshot
 * of each — components just read these values and re-render automatically
 * whenever a new message arrives.
 */
export function useMarketData(symbol) {
  const [orderBook, setOrderBook] = useState({ bids: [], asks: [] })
  const [trades, setTrades] = useState([])
  const [connected, setConnected] = useState(false)
  const clientRef = useRef(null)

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)

        client.subscribe(`/topic/orderbook/${symbol}`, (message) => {
          const snapshot = JSON.parse(message.body)
          setOrderBook(snapshot)
        })

        client.subscribe(`/topic/trades/${symbol}`, (message) => {
          const trade = JSON.parse(message.body)
          // newest trade first, keep the list from growing unbounded
          setTrades((prev) => [trade, ...prev].slice(0, 50))
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
      }
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [symbol])

  return { orderBook, trades, connected }
}