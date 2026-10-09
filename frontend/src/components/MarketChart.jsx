import { useEffect, useMemo, useRef, useState } from "react"
import {
  createChart,
  ColorType,
  CrosshairMode,
  CandlestickSeries,
  LineSeries,
  HistogramSeries,
} from "lightweight-charts"

const API_URL = "http://localhost:5000"
const REFRESH_INTERVAL = 10000

const TIMEFRAMES = ["1m", "5m", "15m", "1H", "4H", "1D"]
const LIMITS = { "1m": 30, "5m": 60, "15m": 100, "1H": 120, "4H": 160, "1D": 200 }

const UP = "#26a69a"
const DOWN = "#ef5350"
const MA20_COLOR = "#f59e0b"
const MA50_COLOR = "#a78bfa"

const money = (n) =>
  Number(n).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })

function MarketChart({ symbol = "BTC-USD" }) {
  const [marketData, setMarketData] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [timeframe, setTimeframe] = useState("1H")
  const [hovered, setHovered] = useState(null)

  const containerRef = useRef(null)
  const chartRef = useRef(null)
  const seriesRef = useRef({})

  // ---------- Data loading (unchanged behaviour) ----------
  useEffect(() => {
    let cancelled = false

    async function loadMarketData() {
      try {
        const response = await fetch(
          `${API_URL}/api/market-data?symbol=${encodeURIComponent(symbol)}&points=200`
        )
        if (!response.ok) {
          throw new Error(`Market data request failed: ${response.status}`)
        }
        const result = await response.json()

        if (!cancelled) {
          const records = Array.isArray(result.data)
            ? result.data
            : Array.isArray(result.records)
              ? result.records
              : []
          setMarketData(records)
          setError(null)
          setLoading(false)
        }
      } catch (err) {
        console.error("Failed to load market data:", err)
        if (!cancelled) {
          setError("Unable to load market data. Make sure the Python service is running.")
          setLoading(false)
        }
      }
    }

    loadMarketData()
    const interval = setInterval(loadMarketData, REFRESH_INTERVAL)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [symbol])

  const visibleData = useMemo(() => {
    if (!marketData.length) return []
    return marketData.slice(-(LIMITS[timeframe] || 120))
  }, [marketData, timeframe])

  // ---------- Convert to lightweight-charts format ----------
  // Library requires: time in UTC seconds, strictly ascending, no duplicates.
  const chartData = useMemo(() => {
    const rows = new Map()
    for (const p of visibleData) {
      const time = Math.floor(new Date(p.timestamp).getTime() / 1000)
      if (Number.isFinite(time)) rows.set(time, p)
    }
    const times = [...rows.keys()].sort((a, b) => a - b)

    const candles = []
    const volume = []
    const ma20 = []
    const ma50 = []

    for (const time of times) {
      const p = rows.get(time)
      const open = Number(p.open)
      const high = Number(p.high)
      const low = Number(p.low)
      const close = Number(p.close)

      candles.push({ time, open, high, low, close })
      volume.push({
        time,
        value: Number(p.volume || 0),
        color: close >= open ? "rgba(38,166,154,0.35)" : "rgba(239,83,80,0.35)",
      })

      if (p.movingAverage20 != null && !Number.isNaN(Number(p.movingAverage20))) {
        ma20.push({ time, value: Number(p.movingAverage20) })
      }
      if (p.movingAverage50 != null && !Number.isNaN(Number(p.movingAverage50))) {
        ma50.push({ time, value: Number(p.movingAverage50) })
      }
    }

    return { candles, volume, ma20, ma50 }
  }, [visibleData])

  // ---------- Create the chart once ----------
  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: "#8b949e",
        fontSize: 11,
        fontFamily: "Inter, system-ui, -apple-system, Segoe UI, sans-serif",
      },
      grid: {
        vertLines: { color: "rgba(148,163,184,0.06)" },
        horzLines: { color: "rgba(148,163,184,0.08)" },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: "rgba(148,163,184,0.45)", labelBackgroundColor: "#30363d" },
        horzLine: { color: "rgba(148,163,184,0.45)", labelBackgroundColor: "#30363d" },
      },
      rightPriceScale: {
        borderVisible: false,
        scaleMargins: { top: 0.08, bottom: 0.25 },
      },
      timeScale: {
        borderVisible: false,
        timeVisible: true,
        secondsVisible: false,
        rightOffset: 4,
      },
    })

    const candles = chart.addSeries(CandlestickSeries, {
      upColor: UP,
      downColor: DOWN,
      borderUpColor: UP,
      borderDownColor: DOWN,
      wickUpColor: UP,
      wickDownColor: DOWN,
    })

    const volume = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: "volume",
      priceLineVisible: false,
      lastValueVisible: false,
    })
    volume.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } })

    const lineOptions = {
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    }
    const ma20 = chart.addSeries(LineSeries, { ...lineOptions, color: MA20_COLOR })
    const ma50 = chart.addSeries(LineSeries, { ...lineOptions, color: MA50_COLOR })

    chart.subscribeCrosshairMove((param) => {
      const c = param.seriesData ? param.seriesData.get(candles) : null
      setHovered(c ? { open: c.open, high: c.high, low: c.low, close: c.close } : null)
    })

    chartRef.current = chart
    seriesRef.current = { candles, volume, ma20, ma50 }

    return () => {
      chart.remove()
      chartRef.current = null
      seriesRef.current = {}
    }
  }, [])

  // ---------- Push data into the chart ----------
  useEffect(() => {
    const s = seriesRef.current
    if (!s.candles) return
    s.candles.setData(chartData.candles)
    s.volume.setData(chartData.volume)
    s.ma20.setData(chartData.ma20)
    s.ma50.setData(chartData.ma50)
  }, [chartData])

  // Re-fit the view only when the user switches timeframe,
  // so the 10-second refresh doesn't reset their zoom.
  useEffect(() => {
    if (chartRef.current) chartRef.current.timeScale().fitContent()
  }, [timeframe])

  // ---------- Header numbers ----------
  const latest = chartData.candles[chartData.candles.length - 1]
  const previous = chartData.candles[chartData.candles.length - 2] || latest
  const latestPrice = latest ? latest.close : 0
  const change = latest && previous ? latest.close - previous.close : 0
  const changePct = previous && previous.close !== 0 ? (change / previous.close) * 100 : 0
  const isPositive = change >= 0
  const shown = hovered || latest
  const shownUp = shown ? shown.close >= shown.open : true

  return (
    <section className="panel market-chart-panel">
      <div className="mc-header">
        <div className="mc-symbol">
          <span className="mc-coin">₿</span>
          <div>
            <h2 className="mc-title">{symbol}</h2>
            <span className="mc-subtitle">Bitcoin / US Dollar · Simulated</span>
          </div>
        </div>

        <div className="mc-price-block">
          <span className="mc-price">${money(latestPrice)}</span>
          <span className={isPositive ? "mc-change up" : "mc-change down"}>
            {isPositive ? "+" : ""}
            {money(change)} ({isPositive ? "+" : ""}
            {changePct.toFixed(2)}%)
          </span>
        </div>

        <div className="mc-live">
          <span className="mc-live-dot" />
          LIVE
        </div>
      </div>

      <div className="mc-toolbar">
        <div className="mc-timeframes">
          {TIMEFRAMES.map((period) => (
            <button
              key={period}
              type="button"
              className={timeframe === period ? "mc-tf active" : "mc-tf"}
              onClick={() => setTimeframe(period)}
            >
              {period}
            </button>
          ))}
        </div>

        <div className="mc-legend">
          {shown && (
            <>
              <span>O <b className={shownUp ? "up" : "down"}>{money(shown.open)}</b></span>
              <span>H <b className={shownUp ? "up" : "down"}>{money(shown.high)}</b></span>
              <span>L <b className={shownUp ? "up" : "down"}>{money(shown.low)}</b></span>
              <span>C <b className={shownUp ? "up" : "down"}>{money(shown.close)}</b></span>
            </>
          )}
          <span className="mc-key"><i style={{ background: MA20_COLOR }} />MA20</span>
          <span className="mc-key"><i style={{ background: MA50_COLOR }} />MA50</span>
        </div>
      </div>

      <div className="mc-chart" ref={containerRef}>
        {loading && <div className="mc-overlay">Loading market data...</div>}
        {error && <div className="mc-overlay error">{error}</div>}
      </div>

      <div className="mc-footer">
        <span>{symbol} · Simulated Market</span>
        <span>1-minute candles · refreshes every 10s</span>
      </div>
    </section>
  )
}

export default MarketChart