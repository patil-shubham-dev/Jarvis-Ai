import os
from typing import List, Dict, Any
from langchain_openai import ChatOpenAI
from langchain_anthropic import ChatAnthropic
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain.prompts import ChatPromptTemplate
from langchain.output_parsers import PydanticOutputParser
from app.models.intent import IntentClassification, IntentCategory
from dotenv import load_dotenv

load_dotenv()

class ConversationAgent:
    def __init__(self, provider: str = "openai", model: str = "gpt-4o-mini"):
        self.provider = provider
        self.model_name = model
        self._init_llm()
        self.parser = PydanticOutputParser(pydantic_object=IntentClassification)
        
    def _init_llm(self):
        if self.provider == "openai":
            self.llm = ChatOpenAI(model=self.model_name, temperature=0)
        elif self.provider == "anthropic":
            self.llm = ChatAnthropic(model=self.model_name, temperature=0)
        elif self.provider == "google":
            self.llm = ChatGoogleGenerativeAI(model=self.model_name, temperature=0)
        else:
            raise ValueError(f"Unsupported provider: {self.provider}")

    async def classify_intent(self, text: str, history: List[Dict[str, str]] = []) -> IntentClassification:
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

    async def generate_response(self, text: str, intent: IntentClassification, context: Dict[str, Any] = {}) -> str:
        # TODO: Implement full response generation based on intent and context
        if intent.category == IntentCategory.CASUAL:
            prompt = ChatPromptTemplate.from_template(
                "You are Jarvis, a premium AI OS assistant. Respond to the user's casual message naturally and helpfully.\n\n"
                "User: {text}\n"
                "Response:"
            )
            chain = prompt | self.llm
            response = await chain.ainvoke({"text": text})
            return response.content
        
        return f"I've identified your intent as {intent.category}. I am still learning how to handle this specifically."
