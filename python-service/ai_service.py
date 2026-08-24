from langchain_ollama import ChatOllama
from langchain_core.messages import HumanMessage, SystemMessage, ToolMessage

from wallet_tools import get_wallet_balances
from market_tools import (
    get_order_book,
    get_recent_trades,
    get_open_orders,
    get_market_summary,
)

DEFAULT_ACCOUNT_ID = "11111111-1111-1111-1111-111111111111"

SYSTEM_PROMPT = """
You are the OpenEx AI trading assistant.

You help users understand their OpenEx account, wallets, orders,
trading concepts, and market information.

You have access to real OpenEx data through tools.

Never invent financial or market data. When a tool is available,
use it and treat its response as the source of truth.

WALLET RULES
- Each asset is independent. Never combine assets unless explicitly requested.
- If the user asks about BTC, report only BTC.
- If the user asks about USD, report only USD.
- Distinguish balance, reserved, and available.
- Available = balance - reserved.
- Use get_wallet_balances for wallet questions.

MARKET RULES
- Current market situation/overview -> get_market_summary.
- Order book, bids, asks, best bid, best ask, spread, market depth -> get_order_book.
- Recent/latest trades or executions -> get_recent_trades.
- Never invent a bid or ask.
- bids are buy orders; asks are sell orders.
- Best bid = highest bid price.
- Best ask = lowest ask price.
- Empty bids means there is no current bid.
- Empty asks means there is no current ask.

TRADE RULES
- Use only data returned by get_recent_trades.
- Trade data contains price, quantity, and execution time.
- Trade data does not contain BUY/SELL side unless explicitly provided.
- Never infer a BUY or SELL side.
- Preserve the meaning of price and quantity.

ORDER RULES
- Active/open/pending/partially-filled/user orders -> get_open_orders.
- Use the default test account for account-specific queries.
- Default account: 11111111-1111-1111-1111-111111111111.
- Default symbol for order questions: BTC-USD.
- Never invent orders or order details.
- Empty results mean there are no matching records.

RESPONSE RULES
- Be concise, clear, and accurate.
- Preserve numerical accuracy.
- Use bullets for multiple records.
- Never contradict tool results.
- If information is unavailable, say so.
- Never begin the response with "assistant".
"""

llm = ChatOllama(
    model="llama3.2:3b",
    temperature=0,
)

tools = [
    get_wallet_balances,
    get_order_book,
    get_recent_trades,
    get_open_orders,
    get_market_summary,
]

llm_with_tools = llm.bind_tools(tools)


def _symbol_asset(symbol: str) -> str:
    return symbol.split("-", 1)[0] if "-" in symbol else symbol


def _format_number(value, decimals=8) -> str:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return str(value)

    return f"{number:,.{decimals}f}".rstrip("0").rstrip(".")


def format_order_book_response(result: dict, user_message: str) -> str:
    """Format order-book answers directly from API data."""
    symbol = result.get("symbol", "BTC-USD")
    bids = result.get("bids") or []
    asks = result.get("asks") or []
    asset = _symbol_asset(symbol)
    message = user_message.lower()

    if "best bid" in message:
        if not bids:
            return f"There is currently no bid for {symbol}."

        best_bid = max(bids, key=lambda level: float(level["price"]))

        return (
            f"The best bid for {symbol} is "
            f"${float(best_bid['price']):,.2f} "
            f"for {_format_number(best_bid['quantity'])} {asset}."
        )

    if "best ask" in message:
        if not asks:
            return f"There is currently no ask for {symbol}."

        best_ask = min(asks, key=lambda level: float(level["price"]))

        return (
            f"The best ask for {symbol} is "
            f"${float(best_ask['price']):,.2f} "
            f"for {_format_number(best_ask['quantity'])} {asset}."
        )

    lines = [f"Current {symbol} order book:", "", "Bids:"]

    if bids:
        sorted_bids = sorted(
            bids,
            key=lambda level: float(level["price"]),
            reverse=True,
        )
        for bid in sorted_bids:
            lines.append(
                f"- ${float(bid['price']):,.2f} × "
                f"{_format_number(bid['quantity'])} {asset}"
            )
    else:
        lines.append("- None")

    lines.extend(["", "Asks:"])

    if asks:
        sorted_asks = sorted(
            asks,
            key=lambda level: float(level["price"]),
        )
        for ask in sorted_asks:
            lines.append(
                f"- ${float(ask['price']):,.2f} × "
                f"{_format_number(ask['quantity'])} {asset}"
            )
    else:
        lines.append("- None")

    return "\n".join(lines)


def clean_response(response) -> str:
    """Remove accidental Ollama role prefixes."""
    if response is None:
        return ""

    response = str(response).strip()

    if response.lower().startswith("assistant"):
        response = response[len("assistant"):].lstrip(" :\n")

    return response


def _execute_tool(tool_name: str, tool_args: dict):
    """Execute one registered OpenEx tool."""
    tool_map = {
        "get_wallet_balances": get_wallet_balances,
        "get_order_book": get_order_book,
        "get_recent_trades": get_recent_trades,
        "get_open_orders": get_open_orders,
        "get_market_summary": get_market_summary,
    }

    tool = tool_map.get(tool_name)

    if tool is None:
        return {"error": f"Unknown tool: {tool_name}"}

    return tool.invoke(tool_args)


def ask_ai(message: str) -> str:
    """
    Ask Ollama a question and allow it to call OpenEx tools.

    Order-book responses are formatted directly from API data so
    the model cannot hallucinate bids or asks.
    """
    messages = [
        SystemMessage(content=SYSTEM_PROMPT),
        HumanMessage(content=message),
    ]

    response = llm_with_tools.invoke(messages)

    if not response.tool_calls:
        return clean_response(response.content)

    messages.append(response)
    tool_results = []

    for tool_call in response.tool_calls:
        tool_name = tool_call["name"]
        tool_args = (tool_call.get("args") or {}).copy()

        if tool_name in {
            "get_wallet_balances",
            "get_open_orders",
        }:
            tool_args["account_id"] = DEFAULT_ACCOUNT_ID

        tool_result = _execute_tool(tool_name, tool_args)

        tool_results.append(
            (tool_name, tool_args, tool_result)
        )

        messages.append(
            ToolMessage(
                content=str(tool_result),
                tool_call_id=tool_call["id"],
            )
        )

    # Order-book answers are deterministic and never regenerated by Ollama.
    for tool_name, _, tool_result in tool_results:
        if tool_name == "get_order_book":
            if isinstance(tool_result, dict) and "error" in tool_result:
                symbol = tool_result.get("symbol", "BTC-USD")
                return (
                    f"Unable to retrieve the {symbol} order book: "
                    f"{tool_result['error']}"
                )

            if isinstance(tool_result, dict):
                return format_order_book_response(
                    tool_result,
                    message,
                )

    final_response = llm_with_tools.invoke(messages)
    return clean_response(final_response.content)