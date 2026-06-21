import logging
from datetime import datetime
from typing import Dict, Any, List, Optional
from app.models.agent import AgentState, AgentThought

logger = logging.getLogger(__name__)

class BaseAgent:
    def __init__(self, name: str):
        self.name = name
        self.state = AgentState.IDLE
        self._thoughts: List[AgentThought] = []

    def think(self, content: str, metadata: Optional[Dict[str, Any]] = None) -> AgentThought:
        thought = AgentThought(
            agent_name=self.name,
            state=self.state,
            content=content,
            timestamp=datetime.now(),
            metadata=metadata or {}
        )
        self._thoughts.append(thought)
        logger.debug("[%s] %s", self.name, content)
        return thought

    def get_recent_thoughts(self, n: int = 5) -> List[AgentThought]:
        return self._thoughts[-n:]

    def clear_thoughts(self):
        self._thoughts.clear()
