from pydantic import BaseModel, Field
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
    timestamp: datetime = Field(default_factory=datetime.now)
    metadata: Dict[str, Any] = {}

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

    def model_dump(self, **kwargs):
        data = super().model_dump(**kwargs)
        if self.result is not None:
            try:
                data["result"] = str(self.result)
            except Exception:
                data["result"] = None
        return data

class Plan(BaseModel):
    id: str
    goal: str
    steps: List[PlanStep] = []
    status: str = "created"
    created_at: datetime = Field(default_factory=datetime.now)
    completed_at: Optional[datetime] = None
    error: Optional[str] = None

class AgentContext(BaseModel):
    session_id: str
    conversation_history: List[Dict[str, Any]] = []
    memory_context: str = ""
    active_plan: Optional[Plan] = None
    metadata: Dict[str, Any] = {}
