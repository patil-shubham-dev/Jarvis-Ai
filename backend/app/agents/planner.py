from typing import List, Dict, Any
from pydantic import BaseModel
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain.output_parsers import PydanticOutputParser
import logging

logger = logging.getLogger(__name__)

class TaskStep(BaseModel):
    id: str
    description: str
    action_type: str # "android_intent", "system_call", "app_launch", "reasoning"
    params: Dict[str, Any]
    status: str = "pending"

class TaskPlan(BaseModel):
    goal: str
    steps: List[TaskStep]
    estimated_time: str

class PlannerAgent:
    def __init__(self, provider: str = "openai", model: str = "gpt-4o-mini"):
        self.provider = provider
        self.model_name = model
        self.llm = ChatOpenAI(model=self.model_name, temperature=0)
        self.parser = PydanticOutputParser(pydantic_object=TaskPlan)

    async def create_plan(self, goal: str, context: Dict[str, Any] = {}) -> TaskPlan:
        """Decomposes a complex mobile-centric goal into executable steps."""
        prompt = ChatPromptTemplate.from_template(
            "You are the Jarvis Task Planner for a Mobile AI Operating System. "
            "Decompose the user's goal into specific executable steps for an Android device.\n\n"
            "Action Types:\n"
            "- app_launch: Launching a specific app (e.g., Spotify, WhatsApp).\n"
            "- android_intent: System-level actions (e.g., Set volume, WiFi on/off).\n"
            "- reasoning: Processing information or generating content.\n"
            "- workflow: Chained actions across multiple apps.\n\n"
            "Goal: {goal}\n"
            "Context: {context}\n\n"
            "{format_instructions}\n"
            "Plan:"
        )
        
        input_data = {
            "goal": goal,
            "context": context,
            "format_instructions": self.parser.get_format_instructions()
        }
        
        chain = prompt | self.llm | self.parser
        plan = await chain.ainvoke(input_data)
        logger.info(f"Generated mobile plan for goal: {goal}")
        return plan
