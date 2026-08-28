import { useEffect, useMemo, useState } from "react"

import {
  Chart as ChartJS,
  LinearScale,
  TimeScale,
  Tooltip,
  Legend,
  LineController,
  LineElement,
  PointElement,
  BarController,
  BarElement,
} from "chart.js"

import {
  CandlestickController,
  CandlestickElement,
} from "chartjs-chart-financial"

import { Chart as FinancialChart } from "react-chartjs-2"

import "chartjs-adapter-date-fns"

ChartJS.register(
  LinearScale,
  TimeScale,
  Tooltip,
  Legend,
  CandlestickController,
  CandlestickElement,
  LineController,
  LineElement,
  PointElement,
  BarController,
  BarElement
)

const API_URL = "http://localhost:5000"
const REFRESH_INTERVAL = 10000

function MarketChart({ symbol = "BTC-USD" }) {
  const [marketData, setMarketData] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [timeframe, setTimeframe] = useState("1H")

  useEffect(() => {
    let cancelled = false

    async function loadMarketData() {
      try {
        const response = await fetch(
          `${API_URL}/api/market-data?symbol=${encodeURIComponent(
            symbol
          )}&points=200`
        )

        if (!response.ok) {
          throw new Error(
            `Market data request failed: ${response.status}`
          )
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
          setError(
            "Unable to load market data. Make sure the Python service is running."
          )
          setLoading(false)
        }
      }
    }

    loadMarketData()

    const interval = setInterval(
      loadMarketData,
      REFRESH_INTERVAL
    )

    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [symbol])

  const visibleData = useMemo(() => {
    if (!marketData.length) {
      return []
    }

    const limits = {
      "1m": 30,
      "5m": 60,
      "15m": 100,
      "1H": 120,
      "4H": 160,
      "1D": 200,
    }

    return marketData.slice(
      -(limits[timeframe] || 120)
    )
  }, [marketData, timeframe])

  const latestCandle =
    visibleData.length > 0
      ? visibleData[visibleData.length - 1]
      : null

  const previousCandle =
    visibleData.length > 1
      ? visibleData[visibleData.length - 2]
      : latestCandle

  const latestPrice = latestCandle
    ? Number(latestCandle.close)
    : 0

  const previousClose = previousCandle
    ? Number(previousCandle.close)
    : latestPrice

  const priceChange =
    latestPrice - previousClose

  const priceChangePercent =
    previousClose !== 0
      ? (priceChange / previousClose) * 100
      : 0

  const isPositive = priceChange >= 0

  const candleData = visibleData.map((point) => ({
    x: new Date(point.timestamp),
    o: Number(point.open),
    h: Number(point.high),
    l: Number(point.low),
    c: Number(point.close),
  }))

  const ma20Data = visibleData.map((point) => ({
    x: new Date(point.timestamp),
    y:
      point.movingAverage20 == null ||
      Number.isNaN(Number(point.movingAverage20))
        ? null
        : Number(point.movingAverage20),
  }))

  const ma50Data = visibleData.map((point) => ({
    x: new Date(point.timestamp),
    y:
      point.movingAverage50 == null ||
      Number.isNaN(Number(point.movingAverage50))
        ? null
        : Number(point.movingAverage50),
  }))

  const volumeData = visibleData.map((point) => ({
    x: new Date(point.timestamp),
    y: Number(point.volume || 0),
  }))

  const data = {
    datasets: [
      {
  type: "candlestick",
  label: symbol,
  data: candleData,

  borderColor: (context) => {
    const candle = context.raw

    if (!candle) {
      return "#94a3b8"
    }

    if (candle.c > candle.o) {
      return "#22c55e"
    }

    if (candle.c < candle.o) {
      return "#ef4444"
    }

    return "#94a3b8"
  },

  backgroundColor: (context) => {
    const candle = context.raw

    if (!candle) {
      return "#94a3b8"
    }

    if (candle.c > candle.o) {
      return "#22c55e"
    }

    if (candle.c < candle.o) {
      return "#ef4444"
    }

    return "#94a3b8"
  },

  hoverBackgroundColor: (context) => {
    const candle = context.raw

    if (!candle) {
      return "#94a3b8"
    }

    if (candle.c > candle.o) {
      return "#22c55e"
    }

    if (candle.c < candle.o) {
      return "#ef4444"
    }

    return "#94a3b8"
  },

  hoverBorderColor: (context) => {
    const candle = context.raw

    if (!candle) {
      return "#94a3b8"
    }

    if (candle.c > candle.o) {
      return "#22c55e"
    }

    if (candle.c < candle.o) {
      return "#ef4444"
    }

    return "#94a3b8"
  },

  borderWidth: 1,
  barPercentage: 0.65,
  categoryPercentage: 0.8,
},
      {
        type: "line",
        label: "MA50",
        data: ma50Data,

        borderColor: "#a78bfa",
        backgroundColor: "#a78bfa",

        borderWidth: 1.4,

        pointRadius: 0,
        pointHoverRadius: 3,

        tension: 0.15,
        spanGaps: true,

        yAxisID: "price",
      },

      {
        type: "bar",
        label: "Volume",
        data: volumeData,

        backgroundColor: "rgba(100, 116, 139, 0.28)",

        borderWidth: 0,

        yAxisID: "volume",
      },
    ],
  }

  const options = {
    responsive: true,
    maintainAspectRatio: false,

    animation: false,

    interaction: {
      mode: "index",
      intersect: false,
    },

    parsing: false,
    normalized: true,

    layout: {
      padding: {
        top: 8,
        right: 8,
        bottom: 4,
        left: 4,
      },
    },

    plugins: {
      legend: {
        display: false,
      },

      tooltip: {
        enabled: true,

        backgroundColor: "#0f172a",
        borderColor: "#334155",
        borderWidth: 1,

        titleColor: "#f8fafc",
        bodyColor: "#cbd5e1",

        padding: 12,

        displayColors: false,

        callbacks: {
          title: (items) => {
            if (!items.length) {
              return ""
            }

            return new Date(
              items[0].parsed.x
            ).toLocaleString()
          },

          label: (context) => {
            const raw = context.raw

            if (
              context.dataset.type ===
              "candlestick"
            ) {
              return [
                `Open: $${Number(raw.o).toLocaleString(
                  undefined,
                  {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  }
                )}`,

                `High: $${Number(raw.h).toLocaleString(
                  undefined,
                  {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  }
                )}`,

                `Low: $${Number(raw.l).toLocaleString(
                  undefined,
                  {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  }
                )}`,

                `Close: $${Number(raw.c).toLocaleString(
                  undefined,
                  {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  }
                )}`,
              ]
            }

            if (context.dataset.label === "Volume") {
              return `Volume: ${Number(raw.y).toFixed(2)}`
            }

            return `${context.dataset.label}: $${Number(
              raw.y
            ).toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}`
          },
        },
      },
    },

    scales: {
      x: {
        type: "time",

        time: {
          unit: "minute",

          displayFormats: {
            minute: "HH:mm",
          },
        },

        grid: {
          color: "rgba(148, 163, 184, 0.08)",
        },

        border: {
          display: false,
        },

        ticks: {
          color: "#64748b",

          maxTicksLimit: 8,

          font: {
            size: 10,
          },
        },
      },

      price: {
        position: "right",

        grid: {
          color: "rgba(148, 163, 184, 0.10)",
        },

        border: {
          display: false,
        },

        ticks: {
          color: "#94a3b8",

          font: {
            size: 10,
          },

          callback: (value) =>
            `$${Number(value).toLocaleString(
              undefined,
              {
                maximumFractionDigits: 0,
              }
            )}`,
        },
      },

      volume: {
        position: "left",

        display: false,

        grid: {
          display: false,
        },

        beginAtZero: true,
      },
    },
  }

  if (loading) {
    return (
      <section className="panel market-chart-panel">
        <div className="chart-placeholder">
          Loading market data...
        </div>
      </section>
    )
  }

  if (error) {
    return (
      <section className="panel market-chart-panel">
        <div className="chart-placeholder chart-error">
          {error}
        </div>
      </section>
    )
  }

  return (
    <section className="panel market-chart-panel">

      <div className="trading-chart-header">

        <div className="chart-market-info">

          <div className="chart-symbol">

            <span className="coin-icon">
              ₿
            </span>

            <div>
              <h2>{symbol}</h2>

              <span className="panel-subtitle">
                Bitcoin / US Dollar · Simulated
              </span>
            </div>

          </div>

          <div className="chart-price-block">

            <strong>
              $
              {latestPrice.toLocaleString(
                undefined,
                {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                }
              )}
            </strong>

            <span
              className={
                isPositive
                  ? "price-change positive"
                  : "price-change negative"
              }
            >
              {isPositive ? "+" : ""}
              {priceChange.toFixed(2)}{" "}
              (
              {isPositive ? "+" : ""}
              {priceChangePercent.toFixed(2)}
              %)
            </span>

          </div>

        </div>

        <div className="chart-live-status">
          <span className="live-dot" />
          LIVE
        </div>

      </div>

      <div className="chart-toolbar">

        <div className="timeframe-buttons">

          {[
            "1m",
            "5m",
            "15m",
            "1H",
            "4H",
            "1D",
          ].map((period) => (
            <button
              key={period}
              type="button"
              className={
                timeframe === period
                  ? "timeframe-btn active"
                  : "timeframe-btn"
              }
              onClick={() =>
                setTimeframe(period)
              }
            >
              {period}
            </button>
          ))}

        </div>

        <div className="chart-indicators">

          <span className="indicator price-indicator">
            Candles
          </span>

          <span className="indicator ma20-indicator">
            MA20
          </span>

          <span className="indicator ma50-indicator">
            MA50
          </span>

          <span className="indicator">
            Volume
          </span>

        </div>

      </div>

      <div className="market-chart trading-chart">

        <FinancialChart
          data={data}
          options={options}
        />

      </div>

      <div className="chart-footer">

        <span>
          {symbol} · Simulated Market
        </span>

        <span>
          Simulated · 1-minute candles
        </span>

      </div>

    </section>
  )
}

export default MarketChart
