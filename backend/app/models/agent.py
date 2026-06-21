from pydantic import BaseModel
from typing import Optional, List, Dict, Any
from datetime import datetime
from enum import Enum

class AgentState(str, Enum):
    IDLE = "idle"
    THINKING = "thinking"
    PLANNING = "planning"
    EXECUTING = "executing"
    VERIFYING = "verifying"
    WAITING_FOR_TOOL = "waiting_for_tool"
    COMPLETED = "completed"
    FAILED = "failed"

class AgentThought(BaseModel):
    agent_name: str
    state: AgentState
    content: str
    timestamp: datetime = None
    metadata: Dict[str, Any] = {}

    def __init__(self, **data):
        if "timestamp" not in data or data.get("timestamp") is None:
            data["timestamp"] = datetime.now()
        super().__init__(**data)

class PlanStep(BaseModel):
    id: str
    description: str
    action_type: str
    params: Dict[str, Any] = {}
    status: str = "pending"
    result: Optional[Any] = None
    error: Optional[str] = None
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None

class Plan(BaseModel):
    id: str
    goal: str
    steps: List[PlanStep] = []
    status: str = "created"
    created_at: datetime = None
    completed_at: Optional[datetime] = None
    error: Optional[str] = None

    def __init__(self, **data):
        if "created_at" not in data or data.get("created_at") is None:
            data["created_at"] = datetime.now()
        super().__init__(**data)

class AgentContext(BaseModel):
    session_id: str
    conversation_history: List[Dict[str, Any]] = []
    memory_context: str = ""
    active_plan: Optional[Plan] = None
    metadata: Dict[str, Any] = {}
