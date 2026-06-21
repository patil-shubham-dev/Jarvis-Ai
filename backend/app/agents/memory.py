from typing import List, Dict, Any, Optional
from app.database.vector_db import VectorDB
import logging

logger = logging.getLogger(__name__)

class MemoryAgent:
    def __init__(self, db: VectorDB):
        self.db = db

    async def retrieve_context(self, query: str, limit: int = 5) -> str:
        results = await self.db.search_conversations_async(query, n_results=limit)

        context_parts = []
        if results and results.get('documents') and results['documents']:
            docs = results['documents'][0]
            metas = results.get('metadatas', [[]])[0] if results.get('metadatas') else []
            for i, doc in enumerate(docs):
                ts = metas[i].get('timestamp', 'unknown') if i < len(metas) else 'unknown'
                context_parts.append(f"[{ts}]: {doc}")

        return "\n".join(context_parts)

    async def store_memory(self, text: str, metadata: Dict[str, Any], id: str):
        await self.db.add_conversation_async(text, metadata, id)
        logger.info(f"Stored memory: {id}")

    async def search_knowledge(self, query: str, limit: int = 5) -> str:
        results = await self.db.search_documents_async(query, n_results=limit)

        context_parts = []
        if results and results.get('documents') and results['documents']:
            for doc in results['documents'][0]:
                context_parts.append(doc)

        return "\n".join(context_parts)
