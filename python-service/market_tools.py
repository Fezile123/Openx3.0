import requests
from langchain.tools import tool


KOTLIN_API_URL = "http://localhost:8080"


@tool
def get_order_book(symbol: str = "BTC-USD") -> dict:
    """
    Retrieve the current OpenEx order book for a trading symbol.

    Use this tool when the user asks about:
    - current buy orders
    - current sell orders
    - bids
    - asks
    - order book
    - best bid
    - best ask
    - market depth
    - spread
    """

    try:
        response = requests.get(
            f"{KOTLIN_API_URL}/orderbook",
            params={"symbol": symbol},
            timeout=5
        )

        response.raise_for_status()

        order_book = response.json()

        bids = order_book.get("bids", [])
        asks = order_book.get("asks", [])

        # Highest buy price = best bid
        best_bid = (
            max(
                bids,
                key=lambda level: float(level["price"])
            )
            if bids
            else None
        )

        # Lowest sell price = best ask
        best_ask = (
            min(
                asks,
                key=lambda level: float(level["price"])
            )
            if asks
            else None
        )

        spread = None

        if best_bid and best_ask:
            spread = (
                float(best_ask["price"])
                - float(best_bid["price"])
            )

        return {
            "symbol": symbol,
            "bids": bids,
            "asks": asks,
            "bestBid": best_bid,
            "bestAsk": best_ask,
            "spread": spread
        }

    except requests.RequestException as exc:
        return {
            "symbol": symbol,
            "error": f"Unable to retrieve order book: {exc}"
        }


@tool
def get_recent_trades(symbol: str = "BTC-USD") -> dict:
    """
    Retrieve recent executed trades for a trading symbol.

    Use this tool when the user asks about:
    - recent trades
    - latest trades
    - executions
    - trading activity
    - recent execution prices

    IMPORTANT:
    The OpenEx trade API provides price, quantity and execution time.
    It does NOT provide buy/sell side information.
    Never infer or invent a BUY or SELL side.
    """

    try:
        response = requests.get(
            f"{KOTLIN_API_URL}/trades",
            params={"symbol": symbol},
            timeout=5
        )

        response.raise_for_status()

        trades = response.json()

        # Ensure newest trades are first.
        trades = sorted(
            trades,
            key=lambda trade: trade.get("executedAt", ""),
            reverse=True
        )

        # Keep the response manageable for the AI.
        recent_trades = trades[:10]

        return {
            "symbol": symbol,
            "tradeCount": len(trades),
            "trades": recent_trades
        }

    except requests.RequestException as exc:
        return {
            "symbol": symbol,
            "error": f"Unable to retrieve recent trades: {exc}"
        }


@tool
def get_open_orders(
    account_id: str,
    symbol: str = "BTC-USD"
) -> dict:
    """
    Retrieve active orders belonging to an OpenEx account.

    Use this tool when the user asks about:
    - open orders
    - active orders
    - pending orders
    - partially filled orders
    - their orders
    """

    try:
        response = requests.get(
            f"{KOTLIN_API_URL}/orders",
            params={"accountId": account_id},
            timeout=5
        )

        response.raise_for_status()

        orders = response.json()

        open_orders = [
            order
            for order in orders
            if order.get("symbol") == symbol
            and order.get("status") in (
                "OPEN",
                "PARTIALLY_FILLED"
            )
        ]

        return {
            "accountId": account_id,
            "symbol": symbol,
            "orders": open_orders
        }

    except requests.RequestException as exc:
        return {
            "accountId": account_id,
            "symbol": symbol,
            "error": f"Unable to retrieve orders: {exc}"
        }


@tool
def get_market_summary(symbol: str = "BTC-USD") -> dict:
    """
    Retrieve a concise summary of the current OpenEx market.

    Use this tool when the user asks for:
    - current market situation
    - market summary
    - current market status
    - BTC-USD overview
    """

    try:
        order_book_response = requests.get(
            f"{KOTLIN_API_URL}/orderbook",
            params={"symbol": symbol},
            timeout=5
        )

        trades_response = requests.get(
            f"{KOTLIN_API_URL}/trades",
            params={"symbol": symbol},
            timeout=5
        )

        order_book_response.raise_for_status()
        trades_response.raise_for_status()

        order_book = order_book_response.json()
        trades = trades_response.json()

        bids = order_book.get("bids", [])
        asks = order_book.get("asks", [])

        best_bid = (
            max(
                bids,
                key=lambda level: float(level["price"])
            )
            if bids
            else None
        )

        best_ask = (
            min(
                asks,
                key=lambda level: float(level["price"])
            )
            if asks
            else None
        )

        spread = None

        if best_bid and best_ask:
            spread = (
                float(best_ask["price"])
                - float(best_bid["price"])
            )

        trades = sorted(
            trades,
            key=lambda trade: trade.get("executedAt", ""),
            reverse=True
        )

        latest_trade = (
            trades[0]
            if trades
            else None
        )

        return {
            "symbol": symbol,
            "bestBid": best_bid,
            "bestAsk": best_ask,
            "spread": spread,
            "latestTrade": latest_trade,
            "bidLevels": len(bids),
            "askLevels": len(asks),
            "recentTradeCount": len(trades)
        }

    except requests.RequestException as exc:
        return {
            "symbol": symbol,
            "error": f"Unable to retrieve market summary: {exc}"
        }