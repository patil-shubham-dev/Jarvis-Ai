from pydantic import BaseModel
from enum import Enum
from typing import Optional, List, Dict, Any

class IntentCategory(str, Enum):
    CASUAL = "casual conversation"
    QUESTION = "question answering"
    MEMORY_RETRIEVAL = "memory retrieval"
    TASK_CREATION = "task creation"
    WORKFLOW_EXECUTION = "workflow execution"
    DEVICE_CONTROL = "device control"
    APP_AUTOMATION = "app automation"
    MEETING_ASSISTANT = "meeting assistant"
    DOCUMENT_ANALYSIS = "document analysis"
    SCREEN_UNDERSTANDING = "screen understanding"
    FOLLOW_UP = "follow-up continuation"
    VOICE_INTERACTION = "voice interaction"

class IntentClassification(BaseModel):
    category: IntentCategory
    confidence: float
    reasoning: str
    extracted_entities: Dict[str, Any] = {}
    structured_output: Optional[Dict[str, Any]] = None
