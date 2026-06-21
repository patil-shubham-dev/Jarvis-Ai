import asyncio
import functools
import chromadb
from typing import List, Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)

class VectorDB:
    def __init__(self, persist_directory: str = "./chroma_db"):
        self.persist_directory = persist_directory
        self.client = chromadb.PersistentClient(path=self.persist_directory)
        self._loop = asyncio.get_event_loop()
        self._executor = None
        self._init_collections()

    def _init_collections(self):
        self.conversations = self.client.get_or_create_collection(name="conversations")
        self.documents = self.client.get_or_create_collection(name="documents")
        logger.info("ChromaDB collections initialized")

    async def _run_sync(self, fn, *args, **kwargs):
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(self._executor, functools.partial(fn, *args, **kwargs))

    async def add_conversation_async(self, text: str, metadata: Dict[str, Any], id: str):
        try:
            await self._run_sync(
                self.conversations.add,
                documents=[text], metadatas=[metadata], ids=[id]
            )
        except Exception as e:
            logger.exception(f"Failed to add conversation to ChromaDB: {e}")

    async def search_conversations_async(self, query: str, n_results: int = 5) -> Dict[str, Any]:
        try:
            return await self._run_sync(
                self.conversations.query,
                query_texts=[query], n_results=n_results
            )
        except Exception as e:
            logger.exception(f"ChromaDB search failed: {e}")
            return {"ids": [], "documents": [], "metadatas": [], "distances": []}

    async def add_document_async(self, text: str, metadata: Dict[str, Any], id: str):
        try:
            await self._run_sync(
                self.documents.add,
                documents=[text], metadatas=[metadata], ids=[id]
            )
        except Exception as e:
            logger.exception(f"Failed to add document to ChromaDB: {e}")

    async def search_documents_async(self, query: str, n_results: int = 5) -> Dict[str, Any]:
        try:
            return await self._run_sync(
                self.documents.query,
                query_texts=[query], n_results=n_results
            )
        except Exception as e:
            logger.exception(f"ChromaDB document search failed: {e}")
            return {"ids": [], "documents": [], "metadatas": [], "distances": []}

    def add_conversation(self, text: str, metadata: Dict[str, Any], id: str):
        try:
            self.conversations.add(documents=[text], metadatas=[metadata], ids=[id])
        except Exception as e:
            logger.exception(f"Failed to add conversation to ChromaDB: {e}")

    def search_conversations(self, query: str, n_results: int = 5) -> Dict[str, Any]:
        try:
            return self.conversations.query(query_texts=[query], n_results=n_results)
        except Exception as e:
            logger.exception(f"ChromaDB search failed: {e}")
            return {"ids": [], "documents": [], "metadatas": [], "distances": []}

    def add_document(self, text: str, metadata: Dict[str, Any], id: str):
        try:
            self.documents.add(documents=[text], metadatas=[metadata], ids=[id])
        except Exception as e:
            logger.exception(f"Failed to add document to ChromaDB: {e}")

    def search_documents(self, query: str, n_results: int = 5) -> Dict[str, Any]:
        try:
            return self.documents.query(query_texts=[query], n_results=n_results)
        except Exception as e:
            logger.exception(f"ChromaDB document search failed: {e}")
            return {"ids": [], "documents": [], "metadatas": [], "distances": []}
