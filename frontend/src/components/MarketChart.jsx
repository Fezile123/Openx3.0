import { useEffect, useState } from "react"
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
} from "chart.js"
import { Line } from "react-chartjs-2"

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend
)

const API_URL = "http://localhost:5000"
const REFRESH_INTERVAL = 10000

function MarketChart({ symbol = "BTC-USD" }) {
  const [marketData, setMarketData] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

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

  if (loading) {
    return (
      <section className="panel market-chart-panel">
        <div className="panel-header">
          <div>
            <h2>Market Analytics</h2>

            <span className="panel-subtitle">
              {symbol} · Price & Moving Averages
            </span>
          </div>
        </div>

        <div className="chart-placeholder">
          Loading market data...
        </div>
      </section>
    )
  }

  if (error) {
    return (
      <section className="panel market-chart-panel">
        <div className="panel-header">
          <div>
            <h2>Market Analytics</h2>

            <span className="panel-subtitle">
              {symbol} · Price & Moving Averages
            </span>
          </div>
        </div>

        <div className="chart-placeholder chart-error">
          {error}
        </div>
      </section>
    )
  }

  const labels = marketData.map((point) =>
    new Date(point.timestamp).toLocaleTimeString(
      [],
      {
        hour: "2-digit",
        minute: "2-digit",
      }
    )
  )

  const prices = marketData.map(
    (point) => point.price
  )

  const movingAverage20 = marketData.map(
    (point) => point.movingAverage20
  )

  const movingAverage50 = marketData.map(
    (point) => point.movingAverage50
  )

  const data = {
    labels,

    datasets: [
      {
        label: `${symbol} Price`,        data: prices,
        tension: 0.25,
        pointRadius: 0,
        borderWidth: 2,
      },
      {
        label: "MA 20",
        data: movingAverage20,
        tension: 0.25,
        pointRadius: 0,
        borderWidth: 1.5,
        spanGaps: false,
      },
      {
        label: "MA 50",
        data: movingAverage50,
        tension: 0.25,
        pointRadius: 0,
        borderWidth: 1.5,
        spanGaps: false,
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
      duration: 500,
    },

    plugins: {
      legend: {
        display: true,
        position: "top",
      },

      tooltip: {
        callbacks: {
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
            ).toFixed(2)}`
          },
        },
      },
    },

    scales: {
      x: {
        ticks: {
          maxTicksLimit: 8,
        },
      },

      y: {
        ticks: {
          callback: (value) =>
            `$${Number(value).toFixed(0)}`,
        },
      },
    },
  }

  return (
    <section className="panel market-chart-panel">

      <div className="panel-header">

        <div>
          <h2>Market Analytics</h2>

          <span className="panel-subtitle">
            {symbol} · Price & Moving Averages
          </span>
        </div>

        <span className="live-badge">
          <span />
          Simulated Market
        </span>

      </div>

      <div className="market-chart">
        <Line
          data={data}
          options={options}
        />
      </div>

    </section>
  )
}

export default MarketChart