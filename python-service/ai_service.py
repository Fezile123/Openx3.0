from langchain_ollama import ChatOllama
from langchain_core.messages import HumanMessage, SystemMessage, ToolMessage

from wallet_tools import get_wallet_balances


# Alice's test account from the OpenEx seed data
DEFAULT_ACCOUNT_ID = "11111111-1111-1111-1111-111111111111"


SYSTEM_PROMPT = """
You are the OpenEx AI trading assistant.

You help users understand their OpenEx account, wallets,
orders, trading concepts, and market information.

IMPORTANT WALLET RULES:

1. Each wallet asset is completely independent.
2. Never add balances from different assets together.
3. If the user asks about BTC, report ONLY the BTC wallet.
4. If the user asks about USD, report ONLY the USD wallet.
5. PARTIAL, E2ECOIN, BTC, USD, and other assets must never
   be combined unless the user explicitly asks for a total
   across different assets.
6. Always distinguish between:
   - balance
   - reserved
   - available
7. Available balance = balance - reserved.
8. Never invent wallet information.
9. When wallet information is required, use the wallet tool.
10. Treat the wallet tool response as the source of truth.

Be concise, clear, and accurate.
"""


llm = ChatOllama(
    model="llama3.2:3b",
    temperature=0
)

tools = [
    get_wallet_balances
]

llm_with_tools = llm.bind_tools(tools)


def ask_ai(message: str) -> str:
    """
    Ask the local Ollama model a question.

    The model can use OpenEx tools when it needs
    real wallet information.
    """

    messages = [
        SystemMessage(content=SYSTEM_PROMPT),
        HumanMessage(content=message)
    ]

    response = llm_with_tools.invoke(messages)

    if response.tool_calls:

        messages.append(response)

        for tool_call in response.tool_calls:

            if tool_call["name"] == "get_wallet_balances":

                tool_result = get_wallet_balances.invoke({
                    "account_id": DEFAULT_ACCOUNT_ID
                })

                messages.append(
                    ToolMessage(
                        content=str(tool_result),
                        tool_call_id=tool_call["id"]
                    )
                )

        final_response = llm_with_tools.invoke(messages)

        return final_response.content

    return response.content