from flask import Flask, jsonify, request
from flask_cors import CORS

from market_simulator import generate_market_data

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

    records = data.copy()

    records["timestamp"] = (
        records["timestamp"]
        .astype(str)
    )

    records = records.where(
        records.notna(),
        None
    )

    return jsonify({
        "symbol": symbol,
        "data": records.to_dict(
            orient="records"
        )
    })


if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=5000,
        debug=True
    )