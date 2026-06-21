import asyncio
import logging
import uuid
from datetime import datetime
from typing import Optional, Dict, Any, List, Callable
from app.config import config
from app.models.agent import AgentState, Plan, PlanStep, AgentContext
from app.models.intent import IntentCategory, IntentClassification
from app.agents.base import BaseAgent
from app.agents.conversation import ConversationAgent
from app.agents.planner import PlannerAgent
from app.agents.memory import MemoryAgent
from app.database.vector_db import VectorDB

logger = logging.getLogger(__name__)

class AgentOrchestrator(BaseAgent):
    def __init__(self, db: VectorDB = None):
        super().__init__("orchestrator")
        self.db = db or VectorDB()
        self.memory_agent = MemoryAgent(self.db)
        self.conversation_agent = ConversationAgent()
        self.planner_agent = PlannerAgent()
        self._event_handlers: Dict[str, List[Callable]] = {}
        self.active_tasks: Dict[str, asyncio.Task] = {}

    def on(self, event_type: str, handler: Callable):
        if event_type not in self._event_handlers:
            self._event_handlers[event_type] = []
        self._event_handlers[event_type].append(handler)

    def _clear_handlers(self):
        self._event_handlers.clear()

    async def _emit(self, event_type: str, data: Dict[str, Any]):
        handlers = self._event_handlers.get(event_type, [])
        for handler in handlers:
            try:
                await handler({"type": event_type, **data})
            except Exception as e:
                logger.error(f"Event handler failed for {event_type}: {e}")

    async def process_message(self, session_id: str, text: str,
                              on_event: Callable = None) -> str:
        try:
            ctx = AgentContext(session_id=session_id)

            if on_event:
                self.on("thought", on_event)
                self.on("plan", on_event)
                self.on("step", on_event)
                self.on("tool", on_event)
                self.on("stream", on_event)

            self.state = AgentState.THINKING
            self.think("Analyzing user intent...")
            await self._emit("thought", {"agent": "orchestrator", "content": "Analyzing intent..."})

            intent = await self._classify_intent(text)
            if intent is None:
                return self._fallback_response(text)

            logger.info(f"Intent: {intent.category} (confidence: {intent.confidence:.2f})")

            plan = None
            if intent.category in [
                IntentCategory.WORKFLOW_EXECUTION,
                IntentCategory.APP_AUTOMATION,
                IntentCategory.DEVICE_CONTROL
            ]:
                plan = await self._create_and_execute_plan(text, intent)

            context = await self._retrieve_memory(text, intent)

            self.state = AgentState.THINKING
            self.think("Generating response...")
            await self._emit("thought", {"agent": "orchestrator", "content": "Generating response..."})

            response = await self.conversation_agent.generate_response(
                text, intent,
                {"context": context, "plan": plan.dict() if plan else None}
            )

            await self._store_memory(text, response, intent)

            self.state = AgentState.COMPLETED
            return response
        finally:
            self._clear_handlers()

    async def process_message_stream(self, session_id: str, text: str,
                                      on_event: Callable = None) -> str:
        full_response = await self.process_message(session_id, text, on_event)
        return full_response

    async def _classify_intent(self, text: str) -> Optional[IntentClassification]:
        try:
            intent = await self.conversation_agent.classify_intent(text)
            return intent
        except Exception as e:
            logger.error(f"Intent classification failed: {e}")
            return IntentClassification(
                category=IntentCategory.CASUAL,
                confidence=1.0,
                reasoning="Fallback due to error"
            )

    async def _create_and_execute_plan(self, text: str,
                                        intent: IntentClassification) -> Optional[Plan]:
        self.state = AgentState.PLANNING
        self.think(f"Creating plan for {intent.category}...")
        await self._emit("plan", {"content": f"Creating plan for: {intent.category}", "steps": []})

        try:
            task_plan = await self.planner_agent.create_plan(text, {"intent": intent.category})

            plan = Plan(
                id=str(uuid.uuid4()),
                goal=text,
                steps=[
                    PlanStep(id=s.id, description=s.description,
                             action_type=s.action_type, params=s.params)
                    for s in task_plan.steps
                ]
            )

            await self._emit("plan", {
                "plan_id": plan.id,
                "goal": plan.goal,
                "steps": [s.dict() for s in plan.steps]
            })

            plan = await self._execute_plan_steps(plan)
            return plan

        except Exception as e:
            logger.error(f"Planning failed: {e}")
            await self._emit("plan", {"error": str(e)})
            return None

    async def _execute_plan_steps(self, plan: Plan) -> Plan:
        self.state = AgentState.EXECUTING
        self.think(f"Executing {len(plan.steps)} steps...")

        for i, step in enumerate(plan.steps):
            step.started_at = datetime.now()
            step.status = "running"

            await self._emit("step", {
                "step_id": step.id,
                "description": step.description,
                "action_type": step.action_type,
                "index": i,
                "total": len(plan.steps),
                "status": "started"
            })

            self.think(f"Step {i + 1}/{len(plan.steps)}: {step.description}")

            try:
                result = await self._execute_step(step)
                step.status = "completed" if result.get("success") else "failed"
                step.result = result.get("output")
                if not result.get("success"):
                    step.error = result.get("error")
            except Exception as e:
                step.status = "failed"
                step.error = str(e)

            step.completed_at = datetime.now()

            await self._emit("step", {
                "step_id": step.id,
                "description": step.description,
                "status": step.status,
                "result": step.result,
                "error": step.error,
                "duration_ms": (
                    (step.completed_at - step.started_at).total_seconds() * 1000
                    if step.started_at and step.completed_at else 0
                )
            })

            if step.status == "failed":
                logger.warning(f"Step {step.id} failed: {step.error}")
                if not await self._should_continue_after_failure(plan, i):
                    plan.status = "failed"
                    plan.error = f"Step {step.id} failed: {step.error}"
                    return plan

        plan.status = "completed"
        plan.completed_at = datetime.now()

        await self._emit("plan", {
            "plan_id": plan.id,
            "status": "completed",
            "steps": [s.dict() for s in plan.steps]
        })

        return plan

    async def _execute_step(self, step: PlanStep) -> Dict[str, Any]:
        from app.tools import ToolRegistry
        registry = ToolRegistry()

        tool_name = step.action_type
        if tool_name in ["android_intent", "app_launch", "device_control"]:
            tool_name = "android_bridge"
        elif tool_name == "reasoning":
            return {"success": True, "output": step.description}

        try:
            result = await registry.execute(tool_name, step.params)
            return {"success": True, "output": str(result)}
        except KeyError:
            return {"success": True, "output": f"Simulated: {step.description}"}
        except Exception as e:
            return {"success": False, "error": str(e)}

    async def _should_continue_after_failure(self, plan: Plan, failed_index: int) -> bool:
        remaining = [s for i, s in enumerate(plan.steps) if i > failed_index]
        return len(remaining) > 0

    async def _retrieve_memory(self, text: str, intent: IntentClassification) -> str:
        memory_types = [
            IntentCategory.MEMORY_RETRIEVAL,
            IntentCategory.QUESTION,
            IntentCategory.FOLLOW_UP,
            IntentCategory.CASUAL
        ]
        if intent.category not in memory_types:
            return ""

        await self._emit("thought", {"agent": "memory", "content": "Recalling memories..."})

        recent = await self.memory_agent.retrieve_context(text, limit=config.memory.top_k_recent)
        semantic = await self.memory_agent.search_knowledge(text, limit=config.memory.top_k_semantic)

        context_parts = []
        if recent:
            context_parts.append("--- Recent ---\n" + recent)
        if semantic:
            context_parts.append("--- Knowledge ---\n" + semantic)

        result = "\n\n".join(context_parts)

        await self._emit("stream", {
            "type": "verification",
            "stage": "memory",
            "sources": len(context_parts),
            "status": "complete"
        })

        return result

    async def _store_memory(self, text: str, response: str, intent: IntentClassification):
        memory_id = str(uuid.uuid4())
        await self.memory_agent.store_memory(
            text=f"User: {text}\nJarvis: {response}",
            metadata={
                "timestamp": datetime.now().isoformat(),
                "category": intent.category,
                "confidence": intent.confidence
            },
            id=memory_id
        )

    def _fallback_response(self, text: str) -> str:
        return "I'm having trouble processing that request. Could you rephrase it?"

    async def shutdown(self):
        for task_id, task in self.active_tasks.items():
            task.cancel()
        self.active_tasks.clear()
