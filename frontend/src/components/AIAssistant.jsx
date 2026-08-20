import { useEffect, useRef, useState } from "react"

const API_URL = "http://localhost:5000"

const SUGGESTIONS = [
  "What is my USD balance?",
  "What funds are available?",
  "What are my holdings?",
  "Explain available vs reserved balance",
]

function AIAssistant() {
  const [isOpen, setIsOpen] = useState(false)
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(false)

  const [messages, setMessages] = useState([
    {
      id: 1,
      role: "assistant",
      content:
        "Hi! I'm OpenEx AI. I can help you understand your wallet, available funds, holdings, and trading activity.",
    },
  ])

  const messagesEndRef = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({
      behavior: "smooth",
    })
  }, [messages, loading])

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => {
        inputRef.current?.focus()
      }, 100)
    }
  }, [isOpen])

  const sendMessage = async (messageOverride = null) => {
    const message = (
      messageOverride !== null ? messageOverride : input
    ).trim()

    if (!message || loading) {
      return
    }

    const userMessage = {
      id: Date.now(),
      role: "user",
      content: message,
    }

    setMessages((previous) => [
      ...previous,
      userMessage,
    ])

    setInput("")
    setLoading(true)

    try {
      const response = await fetch(`${API_URL}/api/ai`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message,
        }),
      })

      const data = await response.json()

      if (!response.ok) {
        throw new Error(
          data.error || "The AI service returned an error."
        )
      }

      setMessages((previous) => [
        ...previous,
        {
          id: Date.now() + 1,
          role: "assistant",
          content:
            data.response ||
            "I couldn't generate a response.",
        },
      ])
    } catch (error) {
      setMessages((previous) => [
        ...previous,
        {
          id: Date.now() + 1,
          role: "assistant",
          error: true,
          content:
            "I couldn't connect to OpenEx AI. Please make sure the Python AI service and Ollama are running.",
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    sendMessage()
  }

  const handleKeyDown = (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      sendMessage()
    }
  }

  const handleSuggestion = (suggestion) => {
    sendMessage(suggestion)
  }

  const clearChat = () => {
    setMessages([
      {
        id: Date.now(),
        role: "assistant",
        content:
          "Chat cleared. How can I help you with OpenEx?",
      },
    ])
  }

  return (
    <>
      {/* Floating AI Button */}
      {!isOpen && (
        <button
          type="button"
          className="ai-floating-button"
          onClick={() => setIsOpen(true)}
          aria-label="Open OpenEx AI assistant"
          title="Open OpenEx AI"
        >
          <span className="ai-floating-icon">✦</span>

          <span className="ai-floating-text">
            <strong>OpenEx AI</strong>
            <small>Ask anything</small>
          </span>
        </button>
      )}

      {/* Chat Window */}
      {isOpen && (
        <section
          className="ai-chat-window"
          aria-label="OpenEx AI assistant"
        >
          {/* Header */}
          <header className="ai-chat-header">
            <div className="ai-chat-brand">
              <div className="ai-avatar">
                ✦
              </div>

              <div className="ai-chat-heading">
                <h2>OpenEx AI</h2>

                <div className="ai-online">
                  <span className="ai-online-dot" />
                  <span>AI Assistant Online</span>
                </div>
              </div>
            </div>

            <div className="ai-header-actions">
              <button
                type="button"
                className="ai-header-button"
                onClick={clearChat}
                title="Clear conversation"
                aria-label="Clear conversation"
              >
                ↻
              </button>

              <button
                type="button"
                className="ai-header-button"
                onClick={() => setIsOpen(false)}
                title="Close OpenEx AI"
                aria-label="Close OpenEx AI"
              >
                ×
              </button>
            </div>
          </header>

          {/* Model information */}
          <div className="ai-model-bar">
            <span className="ai-model-badge">
              Local AI
            </span>

            <span className="ai-model-name">
              Llama 3.2
            </span>

            <span className="ai-model-dot" />
          </div>

          {/* Messages */}
          <div className="ai-chat-messages">
            {messages.map((message) => (
              <div
                key={message.id}
                className={`ai-chat-message-row ${message.role}`}
              >
                {message.role === "assistant" && (
                  <div className="ai-message-avatar">
                    ✦
                  </div>
                )}

                <div
                  className={`ai-chat-message ${
                    message.role
                  } ${message.error ? "error" : ""}`}
                >
                  {message.role === "assistant" && (
                    <div className="ai-message-label">
                      OpenEx AI
                    </div>
                  )}

                  <div className="ai-message-content">
                    {message.content}
                  </div>
                </div>
              </div>
            ))}

            {loading && (
              <div className="ai-chat-message-row assistant">
                <div className="ai-message-avatar">
                  ✦
                </div>

                <div className="ai-thinking-bubble">
                  <span />
                  <span />
                  <span />
                </div>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>

          {/* Suggestions */}
          {!loading && messages.length <= 2 && (
            <div className="ai-suggestions">
              <span className="ai-suggestions-label">
                Try asking
              </span>

              <div className="ai-suggestions-grid">
                {SUGGESTIONS.map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    className="ai-suggestion"
                    onClick={() =>
                      handleSuggestion(suggestion)
                    }
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Input */}
          <form
            className="ai-chat-input-area"
            onSubmit={handleSubmit}
          >
            <textarea
              ref={inputRef}
              className="ai-chat-input"
              value={input}
              onChange={(event) =>
                setInput(event.target.value)
              }
              onKeyDown={handleKeyDown}
              placeholder="Ask OpenEx AI..."
              rows={1}
              disabled={loading}
            />

            <button
              type="submit"
              className="ai-send-button"
              disabled={!input.trim() || loading}
              aria-label="Send message"
            >
              <span>➤</span>
            </button>
          </form>

          {/* Footer */}
          <div className="ai-chat-footer">
            <span>
              Responses are generated locally using your
              OpenEx account data.
            </span>
          </div>
        </section>
      )}
    </>
  )
}

export default AIAssistant