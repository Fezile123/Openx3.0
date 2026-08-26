from flask import Flask, jsonify, request
from flask_cors import CORS
import json

from market_simulator import (
    generate_market_data,
    generate_order_book
)
from ai_service import ask_ai


app = Flask(__name__)
CORS(app)


@app.get("/health")
def health():
    return jsonify({
        "status": "UP",
        "service": "openex-python"
    })


@app.get("/api/market-data")
def market_data():

    symbol = request.args.get(
        "symbol",
        "BTC-USD"
    )

    points = request.args.get(
        "points",
        default=100,
        type=int
    )

    points = max(1, min(points, 1000))

    data = generate_market_data(
        symbol=symbol,
        points=points
    )

    # Convert timestamps to strings.
    data["timestamp"] = (
        data["timestamp"]
        .astype(str)
    )

    # Use pandas JSON serialization so NaN values
    # become proper JSON null values.
    records = json.loads(
        data.to_json(
            orient="records"
        )
    )

    return jsonify({
        "symbol": symbol,
        "data": records
    })

@app.get("/api/order-book")
def simulated_order_book():

    symbol = request.args.get(
        "symbol",
        "BTC-USD"
    )

    data = generate_market_data(
        symbol=symbol,
        points=100
    )

    latest_price = float(
        data.iloc[-1]["price"]
    )

    order_book = generate_order_book(
        mid_price=latest_price,
        levels=15
    )

    return jsonify({
        "symbol": symbol,
        "price": latest_price,
        **order_book
    })


@app.post("/api/ai")
def ai():

    body = request.get_json(
        silent=True
    ) or {}

    message = body.get(
        "message",
        ""
    ).strip()

    if not message:
        return jsonify({
            "error": "Message is required"
        }), 400

    try:

        response = ask_ai(message)

        return jsonify({
            "message": message,
            "response": response
        })

    except Exception as exc:

        return jsonify({
            "error": f"AI service error: {exc}"
        }), 500


if __name__ == "__main__":

    app.run(
        host="0.0.0.0",
        port=5000,
        debug=True
    )