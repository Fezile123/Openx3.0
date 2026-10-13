import { useState } from "react"
import "./Login.css"

const API_URL = "http://localhost:8080"

export default function Login({ onLogin }) {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()

    setError("")

    if (!email.trim() || !password) {
      setError("Please enter your email and password.")
      return
    }

    setLoading(true)

    try {
      const response = await fetch(`${API_URL}/api/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: email.trim(),
          password,
        }),
      })

      if (!response.ok) {
        throw new Error("Invalid email or password")
      }

      const data = await response.json()

      if (!data.authenticated || !data.token) {
        throw new Error("Authentication failed")
      }

      localStorage.setItem("openex_token", data.token)

      onLogin(data.token)
    } catch (err) {
      setError(err.message || "Unable to login")
    } finally {
      setLoading(false)
    }
  }

  function useDemoAccount() {
    setEmail("alice@openex.test")
    setPassword("password")
    setError("")
  }

  return (
    <div className="login-page">
      <div className="login-shell">
        <aside className="login-hero">
          <div className="login-brand">
            <div className="login-logo">O</div>
            <div>
              <h1>OpenEx</h1>
              <p>Digital Asset Exchange</p>
            </div>
          </div>

          <div className="login-hero-body">
            <h2>Trade digital assets with confidence.</h2>
            <p>
              A real-time exchange with a live order book, professional charts
              and an AI assistant that understands your wallet.
            </p>

            <ul className="login-points">
              <li>
                <span className="login-point-icon">◆</span>
                <div>
                  <strong>Live order book</strong>
                  <small>Every trade and order streams in as it happens.</small>
                </div>
              </li>
              <li>
                <span className="login-point-icon">◆</span>
                <div>
                  <strong>Double-entry ledger</strong>
                  <small>Every balance change is recorded and auditable.</small>
                </div>
              </li>
              <li>
                <span className="login-point-icon">◆</span>
                <div>
                  <strong>OpenEx AI</strong>
                  <small>Ask about your balances and recent trades.</small>
                </div>
              </li>
            </ul>
          </div>

          <svg
            className="login-spark"
            viewBox="0 0 400 90"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <defs>
              <linearGradient id="sparkFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#26a69a" stopOpacity="0.35" />
                <stop offset="100%" stopColor="#26a69a" stopOpacity="0" />
              </linearGradient>
            </defs>
            <path
              d="M0 70 L30 62 L60 68 L90 50 L120 56 L150 38 L180 46 L210 28 L240 36 L270 20 L300 30 L330 14 L360 22 L400 6 L400 90 L0 90 Z"
              fill="url(#sparkFill)"
            />
            <path
              d="M0 70 L30 62 L60 68 L90 50 L120 56 L150 38 L180 46 L210 28 L240 36 L270 20 L300 30 L330 14 L360 22 L400 6"
              fill="none"
              stroke="#26a69a"
              strokeWidth="2"
            />
          </svg>
        </aside>

        <main className="login-panel">
          <div className="login-heading">
            <h2>Welcome back</h2>
            <p>Sign in to access your trading dashboard.</p>
          </div>

          <form onSubmit={handleSubmit} className="login-form">
            <label>
              Email
              <input
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
                disabled={loading}
              />
            </label>

            <label>
              Password
              <div className="login-password">
                <input
                  type={showPassword ? "text" : "password"}
                  placeholder="Enter your password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  disabled={loading}
                />
                <button
                  type="button"
                  className="login-toggle"
                  onClick={() => setShowPassword((value) => !value)}
                  tabIndex={-1}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
            </label>

            {error && <div className="login-error">{error}</div>}

            <button
              type="submit"
              disabled={loading}
              className="login-button"
            >
              {loading ? "Signing in..." : "Sign in"}
            </button>
          </form>

          <div className="login-demo">
            <div>
              <span>Development account</span>
              <strong>alice@openex.test</strong>
            </div>
            <button type="button" onClick={useDemoAccount} disabled={loading}>
              Use demo account
            </button>
          </div>

          <p className="login-footer">OpenEx · Secured with JWT authentication</p>
        </main>
      </div>
    </div>
  )
}