from typing import List, Dict, Any, Optional
from app.database.vector_db import VectorDB
import logging

logger = logging.getLogger(__name__)

class MemoryAgent:
    def __init__(self, db: VectorDB):
        self.db = db

    async def retrieve_context(self, query: str, limit: int = 5) -> str:
        """Retrieves relevant context from vector memory."""
        results = self.db.search_conversations(query, n_results=limit)
        
        context_parts = []
        if results and results['documents']:
            for doc, meta in zip(results['documents'][0], results['metadatas'][0]):
                context_parts.append(f"[{meta.get('timestamp', 'unknown')}]: {doc}")
        
        return "\n".join(context_parts)

    async def store_memory(self, text: str, metadata: Dict[str, Any], id: str):
        """Stores a new memory entry."""
        self.db.add_conversation(text, metadata, id)
        logger.info(f"Stored memory: {id}")

    async def search_knowledge(self, query: str, limit: int = 5) -> str:
        """Searches documents and knowledge base."""
        results = self.db.search_documents(query, n_results=limit)
        
        context_parts = []
        if results and results['documents']:
            for doc in results['documents'][0]:
                context_parts.append(doc)
        
        return "\n".join(context_parts)
