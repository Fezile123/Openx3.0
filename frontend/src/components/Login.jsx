import { useState } from "react"
import "./Login.css"

const API_URL = "http://localhost:8080"

export default function Login({ onLogin }) {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
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

  return (
    <div className="login-page">
      <div className="login-card">

        <div className="login-brand">
          <div className="login-logo">
            O
          </div>

          <div>
            <h1>OpenEx</h1>
            <p>Digital Asset Exchange</p>
          </div>
        </div>

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
            <input
              type="password"
              placeholder="Enter your password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              disabled={loading}
            />
          </label>

          {error && (
            <div className="login-error">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="login-button"
          >
            {loading ? "Signing in..." : "Sign in"}
          </button>

        </form>

        <div className="login-demo">
          <span>Development account</span>
          <strong>alice@openex.test</strong>
          <small>Password: password</small>
        </div>

        <p className="login-footer">
          OpenEx • Secure JWT Authentication
        </p>

      </div>
    </div>
  )
}
