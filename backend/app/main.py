from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import logging
import json
import asyncio
from datetime import datetime
import uuid

from app.agents.conversation import ConversationAgent
from app.agents.memory import MemoryAgent
from app.database.vector_db import VectorDB
from app.models.intent import IntentCategory

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Jarvis AI OS Backend")

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize Core Services
db = VectorDB()
memory_agent = MemoryAgent(db)
conversation_agent = ConversationAgent()

@app.get("/")
async def root():
    return {"message": "Jarvis AI OS Backend is running"}

@app.websocket("/ws/chat")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    logger.info("WebSocket connection established")
    try:
        while True:
            data = await websocket.receive_text()
            message_data = json.loads(data)
            text = message_data.get("text", "")
            
            logger.info(f"Received message: {text}")
            
            # 1. Notify UI: Thinking state
            await websocket.send_text(json.dumps({"type": "thought", "content": "Analyzing intent..."}))
            
            # 2. Intent Classification
            try:
                intent = await conversation_agent.classify_intent(text)
                logger.info(f"Classified intent: {intent.category}")
            except Exception as e:
                logger.error(f"Intent classification failed: {e}")
                intent = IntentClassification(category=IntentCategory.CASUAL, confidence=1.0, reasoning="Fallback")

            # 3. Decision: Use Vision?
            use_vision = intent.category in [IntentCategory.SCREEN_UNDERSTANDING, IntentCategory.DOCUMENT_ANALYSIS, IntentCategory.WORKFLOW_EXECUTION]
            
            if use_vision:
                await websocket.send_text(json.dumps({"type": "thought", "content": "Scanning screen with MobileNet V3..."}))
                # Trigger vision via Android bridge (simulated here)
                # In real scenario, we send a command to Android client to run VisionSkill
                await websocket.send_text(json.dumps({"type": "action", "action": "vision_scan"}))

            # 4. Memory Retrieval (if relevant)
            context = ""
            if intent.category in [IntentCategory.MEMORY_RETRIEVAL, IntentCategory.QUESTION, IntentCategory.FOLLOW_UP]:
                await websocket.send_text(json.dumps({"type": "thought", "content": "Recalling memories..."}))
                context = await memory_agent.retrieve_context(text)

            # 5. Generate Response
            await websocket.send_text(json.dumps({"type": "thought", "content": "Generating response..."}))
            response = await conversation_agent.generate_response(text, intent, {"context": context})
            
            # 5. Store in Memory
            memory_id = str(uuid.uuid4())
            await memory_agent.store_memory(
                text=f"User: {text}\nJarvis: {response}",
                metadata={"timestamp": datetime.now().isoformat(), "category": intent.category},
                id=memory_id
            )
            
            # 6. Send Final Message
            await websocket.send_text(json.dumps({
                "type": "message",
                "content": response,
                "intent": intent.category
            }))
            
    except WebSocketDisconnect:
        logger.info("WebSocket disconnected")
    except Exception as e:
        logger.error(f"Error in WebSocket: {e}")
        if not websocket.client_state.DISCONNECTED:
            await websocket.close()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
