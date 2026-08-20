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