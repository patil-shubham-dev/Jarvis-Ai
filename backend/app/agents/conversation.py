import json
import logging
from typing import List, Dict, Any, Optional
from langchain.prompts import ChatPromptTemplate
from langchain.output_parsers import PydanticOutputParser
from app.models.intent import IntentClassification, IntentCategory
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are Jarvis, a premium AI operating system assistant. You have access to memory, planning, and execution capabilities.

Response Guidelines:
- Format responses using Markdown for readability
- Use **bold** for emphasis on key terms
- Use bullet points for lists and steps
- Use `code` for technical terms, file names, commands
- Use ```language blocks for code examples with language annotation
- Use > blockquotes for summaries or key takeaways
- Keep responses concise and scannable
- When listing steps, number them (1., 2., 3.)
- For explanations, use headers (##) sparingly for structure
- End with a brief actionable summary or next step when appropriate
- If you execute a plan, explain what was done in a clear format
- For errors, explain what went wrong and potential fixes

Intent Categories you handle:
- casual: Natural conversation, greetings, small talk
- question: Factual questions with thorough answers
- memory: Memory storage and retrieval summaries
- task: Task/reminder/calendar creation confirmations
- workflow: Multi-step automation with step-by-step output
- device: Device control with confirmation of action taken
- app: App automation with results
- meeting: Meeting transcripts and summaries
- document: Document/file analysis results
- screen: Screen content descriptions
- follow_up: Continuation referencing previous context
"""

class ConversationAgent:
    def __init__(self, provider: str = "openai", model: str = "gpt-4o-mini"):
        self.provider = provider
        self.model_name = model
        self.parser = PydanticOutputParser(pydantic_object=IntentClassification)
        self.llm = None
        self._init_llm()

    def _init_llm(self):
        providers = [
            (self.provider, self.model_name),
        ]
        if self.provider != "openai":
            providers.append(("openai", "gpt-4o-mini"))
        if self.provider != "anthropic":
            providers.append(("anthropic", "claude-3-haiku-20240307"))
        if self.provider != "google":
            providers.append(("google", "gemini-1.5-flash"))
        providers.append(("ollama", "llama3"))

        for prov, model_name in providers:
            try:
                self.llm = self._create_llm(prov, model_name)
                if self.llm is not None:
                    self.provider = prov
                    self.model_name = model_name
                    logger.info(f"Using provider: {prov} with model: {model_name}")
                    return
            except Exception as e:
                logger.warning(f"Provider {prov} failed: {e}")
                continue

        raise RuntimeError("No LLM provider available. Check API keys and network connectivity.")

    def _create_llm(self, provider: str, model: str) -> Optional[Any]:
        if provider == "ollama":
            from langchain_community.chat_models import ChatOllama
            return ChatOllama(model=model, temperature=0)
        elif provider == "openai":
            from langchain_openai import ChatOpenAI
            return ChatOpenAI(model=model, temperature=0)
        elif provider == "anthropic":
            from langchain_anthropic import ChatAnthropic
            return ChatAnthropic(model=model, temperature=0)
        elif provider == "google":
            from langchain_google_genai import ChatGoogleGenerativeAI
            return ChatGoogleGenerativeAI(model=model, temperature=0)
        return None

    async def classify_intent(self, text: str, history: List[Dict[str, str]] = None) -> IntentClassification:
        history = history or []
        prompt = ChatPromptTemplate.from_template(
            "You are Jarvis, a highly intelligent AI Operating System. "
            "Your task is to classify the user's intent based on their message and conversation history.\n\n"
            "Categories:\n"
            "- casual conversation: Small talk, greetings, etc.\n"
            "- question answering: Factual questions, general knowledge.\n"
            "- memory retrieval: Asking to remember something, summarizing past events.\n"
            "- task creation: Creating reminders, todos, or calendar events.\n"
            "- workflow execution: Multi-step automation requests.\n"
            "- device control: Controlling hardware (WiFi, volume, etc.).\n"
            "- app automation: Opening apps, searching within apps.\n"
            "- meeting assistant: Live transcription, meeting summaries.\n"
            "- document analysis: Queries about uploaded files or screenshots.\n"
            "- screen understanding: Asking what is currently on the screen.\n"
            "- follow-up continuation: Continuing a previous thread.\n"
            "- voice interaction: Explicit voice-related commands.\n\n"
            "Current Message: {text}\n"
            "History: {history}\n\n"
            "{format_instructions}\n"
            "Classification:"
        )

        input_data = {
            "text": text,
            "history": history,
            "format_instructions": self.parser.get_format_instructions()
        }

        chain = prompt | self.llm | self.parser
        return await chain.ainvoke(input_data)

    async def generate_response(self, text: str, intent: IntentClassification, context: Dict[str, Any] = None) -> str:
        context = context or {}
        memory_context = context.get("context", "")
        plan_info = context.get("plan", None)

        if intent.category == IntentCategory.CASUAL:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text})
            return response.content

        if intent.category == IntentCategory.QUESTION:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nAnswer the user's question thoroughly using provided context.\nContext:\n{context}\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text, "context": memory_context})
            return response.content

        if intent.category == IntentCategory.MEMORY_RETRIEVAL:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nRecall and summarize relevant memories from context.\nContext:\n{context}\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text, "context": memory_context})
            return response.content

        if intent.category == IntentCategory.FOLLOW_UP:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nThis is a follow-up. Reference previous context.\nContext:\n{context}\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text, "context": memory_context})
            return response.content

        if intent.category in [IntentCategory.WORKFLOW_EXECUTION, IntentCategory.APP_AUTOMATION, IntentCategory.DEVICE_CONTROL]:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nSummarize the plan execution results.\nPlan:\n{plan}\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text, "plan": json.dumps(plan_info, indent=2) if plan_info else "No plan data"})
            return response.content

        if intent.category == IntentCategory.TASK_CREATION:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nConfirm task creation with details.\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text})
            return response.content

        if intent.category == IntentCategory.MEETING_ASSISTANT:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nProvide meeting assistance, transcription, or summary.\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text})
            return response.content

        if intent.category == IntentCategory.DOCUMENT_ANALYSIS:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nAnalyze the document or content referenced.\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text})
            return response.content

        if intent.category == IntentCategory.SCREEN_UNDERSTANDING:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nDescribe what is on the user's screen.\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text})
            return response.content

        if intent.category == IntentCategory.VOICE_INTERACTION:
            prompt = ChatPromptTemplate.from_template(
                SYSTEM_PROMPT + "\n\nHandle voice interaction.\n\nUser: {text}\nResponse:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text})
            return response.content

        prompt = ChatPromptTemplate.from_template(
            SYSTEM_PROMPT + "\n\nUser: {text}\nResponse:"
        )
        chain = prompt | self.llm
        response = await chain.ainvoke({"text": text})
        return response.content
