import { useState } from "react"

const AI_API_URL = "http://localhost:5000/api/ai"

function AIAssistant() {
  const [message, setMessage] = useState("")
  const [loading, setLoading] = useState(false)

  const [messages, setMessages] = useState([
    {
      role: "assistant",
      content:
        "Hi! I'm your OpenEx AI assistant. I can help you understand your wallet, available funds and trading activity."
    }
  ])

  const sendMessage = async (event) => {
    event.preventDefault()

    if (!message.trim() || loading) {
      return
    }

    const userMessage = message.trim()

    setMessages((previous) => [
      ...previous,
      {
        role: "user",
        content: userMessage
      }
    ])

    setMessage("")
    setLoading(true)

    try {
      const response = await fetch(AI_API_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          message: userMessage
        })
      })

      const data = await response.json()

      if (!response.ok) {
        throw new Error(
          data.error || "AI request failed"
        )
      }

      setMessages((previous) => [
        ...previous,
        {
          role: "assistant",
          content: data.response
        }
      ])
    } catch (error) {
      console.error("AI request failed:", error)

      setMessages((previous) => [
        ...previous,
        {
          role: "assistant",
          content:
            "I couldn't reach the OpenEx AI service. Please make sure the Python service is running on port 5000."
        }
      ])
    } finally {
      setLoading(false)
    }
  }

  const askSuggestion = (question) => {
    setMessage(question)
  }

  return (
    <section className="ai-assistant">

      {/* Header */}
      <div className="ai-assistant-header">

        <div className="ai-title-area">
          <div className="ai-icon">
            ✦
          </div>

          <div>
            <h2>OpenEx AI</h2>

            <div className="ai-online">
              <span className="ai-online-dot"></span>
              AI Assistant Online
            </div>
          </div>
        </div>

        <span className="ai-powered">
          Llama 3.2
        </span>

      </div>

      {/* Chat */}
      <div className="ai-chat">

        {messages.map((item, index) => (

          <div
            key={index}
            className={`ai-chat-message ${item.role}`}
          >

            {item.role === "assistant" && (
              <div className="ai-avatar">
                ✦
              </div>
            )}

            <div className="ai-bubble">

              <div className="ai-message-name">
                {item.role === "user"
                  ? "You"
                  : "OpenEx AI"}
              </div>

              <div className="ai-message-text">
                {item.content}
              </div>

            </div>

          </div>

        ))}

        {loading && (
          <div className="ai-chat-message assistant">

            <div className="ai-avatar">
              ✦
            </div>

            <div className="ai-bubble">

              <div className="ai-message-name">
                OpenEx AI
              </div>

              <div className="ai-typing">
                <span></span>
                <span></span>
                <span></span>
              </div>

            </div>

          </div>
        )}

      </div>

      {/* Suggested questions */}
      <div className="ai-suggestions">

        <button
          onClick={() =>
            askSuggestion(
              "What is my USD wallet balance?"
            )
          }
        >
          💵 USD Balance
        </button>

        <button
          onClick={() =>
            askSuggestion(
              "How much USD do I have available to trade?"
            )
          }
        >
          📊 Available USD
        </button>

        <button
          onClick={() =>
            askSuggestion(
              "What are all my wallet holdings?"
            )
          }
        >
          💼 My Holdings
        </button>

      </div>

      {/* Input */}
      <form
        className="ai-input"
        onSubmit={sendMessage}
      >

        <input
          type="text"
          value={message}
          onChange={(event) =>
            setMessage(event.target.value)
          }
          placeholder="Ask OpenEx AI anything..."
          disabled={loading}
        />

        <button
          type="submit"
          disabled={
            loading ||
            !message.trim()
          }
          className="ai-send"
        >
          ➤
        </button>

      </form>

      <div className="ai-disclaimer">
        AI responses are generated using your OpenEx
        account data.
      </div>

    </section>
  )
}

export default AIAssistant