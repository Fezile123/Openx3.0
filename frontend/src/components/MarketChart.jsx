import { useEffect, useMemo, useState } from "react"
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Tooltip,
  Legend,
} from "chart.js"
import { Line } from "react-chartjs-2"

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Tooltip,
  Legend
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
          )}&points=100`
        )

        if (!response.ok) {
          throw new Error(
            `Market data request failed: ${response.status}`
          )
        }

        const result = await response.json()

        if (!cancelled) {
          setMarketData(
            Array.isArray(result.data)
              ? result.data
              : []
          )

          setError(null)
          setLoading(false)
        }
      } catch (err) {
        console.error(
          "Failed to load market data:",
          err
        )

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
    if (!marketData.length) return []

    const limits = {
      "1m": 10,
      "5m": 30,
      "15m": 60,
      "1H": 100,
      "4H": 100,
      "1D": 100,
    }

    const limit = limits[timeframe] || 100

    return marketData.slice(-limit)
  }, [marketData, timeframe])

  const latestPrice =
    visibleData.length > 0
      ? Number(
          visibleData[visibleData.length - 1].price
        )
      : 0

  const previousPrice =
    visibleData.length > 1
      ? Number(
          visibleData[visibleData.length - 2].price
        )
      : latestPrice

  const priceChange = latestPrice - previousPrice

  const priceChangePercent =
    previousPrice !== 0
      ? (priceChange / previousPrice) * 100
      : 0

  const isPositive = priceChange >= 0

  const labels = visibleData.map((point) =>
    new Date(point.timestamp).toLocaleTimeString(
      [],
      {
        hour: "2-digit",
        minute: "2-digit",
      }
    )
  )

  const prices = visibleData.map((point) =>
    Number(point.price)
  )

  const movingAverage20 = visibleData.map(
    (point) =>
      point.movingAverage20 == null
        ? null
        : Number(point.movingAverage20)
  )

  const movingAverage50 = visibleData.map(
    (point) =>
      point.movingAverage50 == null
        ? null
        : Number(point.movingAverage50)
  )

  const data = {
    labels,

    datasets: [
      {
        label: `${symbol} Price`,
        data: prices,
        borderColor: "#22c55e",
        backgroundColor: "rgba(34, 197, 94, 0.10)",
        fill: true,
        tension: 0.22,
        pointRadius: 0,
        pointHoverRadius: 4,
        borderWidth: 2,
      },

      {
        label: "MA 20",
        data: movingAverage20,
        borderColor: "#f59e0b",
        backgroundColor: "transparent",
        tension: 0.25,
        pointRadius: 0,
        pointHoverRadius: 3,
        borderWidth: 1.4,
        spanGaps: true,
      },

      {
        label: "MA 50",
        data: movingAverage50,
        borderColor: "#a78bfa",
        backgroundColor: "transparent",
        tension: 0.25,
        pointRadius: 0,
        pointHoverRadius: 3,
        borderWidth: 1.4,
        spanGaps: true,
      },
    ],
  }

  const options = {
    responsive: true,
    maintainAspectRatio: false,

    interaction: {
      mode: "index",
      intersect: false,
    },

    animation: {
      duration: 350,
    },

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

        backgroundColor: "#111827",
        borderColor: "#374151",
        borderWidth: 1,

        titleColor: "#f9fafb",
        bodyColor: "#d1d5db",

        padding: 12,

        displayColors: true,

        callbacks: {
          title: (items) => {
            if (!items.length) return ""

            const index = items[0].dataIndex
            const point = visibleData[index]

            if (!point) return ""

            return new Date(
              point.timestamp
            ).toLocaleString()
          },

          label: (context) => {
            const value = context.parsed.y

            if (
              value === null ||
              value === undefined
            ) {
              return `${context.dataset.label}: —`
            }

            return `${context.dataset.label}: $${Number(
              value
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
        grid: {
          color: "rgba(148, 163, 184, 0.08)",
          drawBorder: false,
        },

        border: {
          display: false,
        },

        ticks: {
          color: "#64748b",
          maxTicksLimit: 7,
          font: {
            size: 10,
          },
        },
      },

      y: {
        position: "right",

        grid: {
          color: "rgba(148, 163, 184, 0.10)",
          drawBorder: false,
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

      {/* Chart header */}
      <div className="trading-chart-header">

        <div className="chart-market-info">

          <div className="chart-symbol">
            <span className="coin-icon">₿</span>

            <div>
              <h2>{symbol}</h2>

              <span className="panel-subtitle">
                Bitcoin / US Dollar
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
              {priceChange.toFixed(2)}
              {" "}
              ({isPositive ? "+" : ""}
              {priceChangePercent.toFixed(2)}%)
            </span>

          </div>

        </div>

        <div className="chart-live-status">
          <span className="live-dot" />
          LIVE
        </div>

      </div>

      {/* Timeframe controls */}
      <div className="chart-toolbar">

        <div className="timeframe-buttons">

          {["1m", "5m", "15m", "1H", "4H", "1D"].map(
            (period) => (
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
            )
          )}

        </div>

        <div className="chart-indicators">

          <span className="indicator price-indicator">
            Price
          </span>

          <span className="indicator ma20-indicator">
            MA20
          </span>

          <span className="indicator ma50-indicator">
            MA50
          </span>

        </div>

      </div>

      {/* Main chart */}
      <div className="market-chart trading-chart">

        <Line
          data={data}
          options={options}
        />

      </div>

      {/* Chart footer */}
      <div className="chart-footer">

        <span>
          Simulated Market
        </span>

        <span>
          Updated every 10 seconds
        </span>

      </div>

    </section>
  )
}

export default MarketChart
