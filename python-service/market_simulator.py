import numpy as np
import pandas as pd


def generate_market_data(
    symbol="BTC-USD",
    points=100,
    start_price=4000.0,
    drift=0.0002,
    volatility=0.01
):
    """
    Generate simulated market prices using a random walk with drift.
    """

    returns = np.random.normal(
        loc=drift,
        scale=volatility,
        size=points
    )

    prices = start_price * np.exp(
        np.cumsum(returns)
    )

    timestamps = pd.date_range(
        end=pd.Timestamp.now(),
        periods=points,
        freq="1min"
    )

    df = pd.DataFrame({
        "timestamp": timestamps,
        "symbol": symbol,
        "price": prices
    })

    df["movingAverage20"] = (
        df["price"]
        .rolling(window=20)
        .mean()
    )

    df["movingAverage50"] = (
        df["price"]
        .rolling(window=50)
        .mean()
    )

    return df


def generate_order_book(
    mid_price,
    levels=15,
    spread=0.0015
):
    """
    Generate a simulated multi-level BTC-USD order book
    around the current simulated market price.
    """

    mid_price = float(mid_price)

    half_spread = mid_price * spread / 2

    best_bid = mid_price - half_spread
    best_ask = mid_price + half_spread

    bids = []
    asks = []

    for level in range(levels):

        bid_price = best_bid - (
            level * mid_price * 0.001
        )

        ask_price = best_ask + (
            level * mid_price * 0.001
        )

        bid_quantity = np.random.uniform(
            0.05,
            0.50
        )

        ask_quantity = np.random.uniform(
            0.05,
            0.50
        )

        bids.append({
            "price": round(bid_price, 2),
            "quantity": round(bid_quantity, 4)
        })

        asks.append({
            "price": round(ask_price, 2),
            "quantity": round(ask_quantity, 4)
        })

    return {
        "bids": bids,
        "asks": asks,
        "midPrice": round(mid_price, 2),
        "bestBid": bids[0],
        "bestAsk": asks[0],
        "spread": round(
            asks[0]["price"] - bids[0]["price"],
            2
        )
    }
