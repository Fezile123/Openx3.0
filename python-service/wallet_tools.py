import requests
from langchain.tools import tool


KOTLIN_API_URL = "http://localhost:8080"


@tool
def get_wallet_balances(account_id: str) -> dict:
    """
    Retrieve the wallet balances for a specific OpenEx account.

    Use this tool when the user asks about their wallet balances,
    available funds, reserved funds, or holdings.
    """

    try:
        response = requests.get(
            f"{KOTLIN_API_URL}/wallets",
            params={"accountId": account_id},
            timeout=5
        )

        response.raise_for_status()

        wallets = response.json()

        return {
            "accountId": account_id,
            "wallets": wallets
        }

    except requests.RequestException as exc:
        return {
            "accountId": account_id,
            "error": f"Unable to retrieve wallet balances: {exc}"
        }