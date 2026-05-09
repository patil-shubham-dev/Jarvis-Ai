import os
import chromadb
from chromadb.config import Settings
from typing import List, Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)

class VectorDB:
    def __init__(self, persist_directory: str = "./chroma_db"):
        self.persist_directory = persist_directory
        self.client = chromadb.PersistentClient(path=self.persist_directory)
        self._init_collections()

    def _init_collections(self):
        # Collections for different memory types
        self.conversations = self.client.get_or_create_collection(name="conversations")
        self.documents = self.client.get_or_create_collection(name="documents")
        self.tasks = self.client.get_or_create_collection(name="tasks")
        logger.info("ChromaDB collections initialized")

    def add_conversation(self, text: str, metadata: Dict[str, Any], id: str):
        self.conversations.add(
            documents=[text],
            metadatas=[metadata],
            ids=[id]
        )

    def search_conversations(self, query: str, n_results: int = 5) -> Dict[str, Any]:
        return self.conversations.query(
            query_texts=[query],
            n_results=n_results
        )

    def add_document(self, text: str, metadata: Dict[str, Any], id: str):
        self.documents.add(
            documents=[text],
            metadatas=[metadata],
            ids=[id]
        )

    def search_documents(self, query: str, n_results: int = 5) -> Dict[str, Any]:
        return self.documents.query(
            query_texts=[query],
            n_results=n_results
        )
