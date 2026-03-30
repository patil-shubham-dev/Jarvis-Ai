package com.jarvisai.app.agents

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer B: Multi-Agent Orchestration - Communication Agent
 * Formats the final "Jarvis" personality response.
 * Responses are concise, proactive, and deeply contextualized.
 */
@Singleton
class CommunicationAgent @Inject constructor() {
    /**
     * Refines the raw AI output into Jarvis persona style.
     */
    fun refineResponse(rawMessage: String): String {
        // Concisely, proactive, and deeply contextualized logic
        // For now, ensured it's trimmed and starts/ends with appropriate markers
        return rawMessage.trim()
    }

    /**
     * Prepares the system prompt with memory context for the next interaction.
     */
    fun buildSystemPrompt(recalledMemory: String, currentContext: String): String {
        return """
            Role: You are JARVIS, a high-intelligence personal OS.
            Objective: Manage a 16-module Persistent Memory System. Autonomously observe, analyze, and update.
            Personality: Professional, sophisticated, minimalist, concise, and proactive.
            
            Current Recalled Context (semantic):
            $recalledMemory
            
            Current Device Context:
            $currentContext
            
            User Interaction Memory is stored across 16 JSON folders (CORE_IDENTITY to SYSTEM_LOGS).
            If you need to update a memory, output a JSON block with: {"action": "update_memory", "module": "NAME", "file": "FILENAME", "content": { ... }}
        """.trimIndent()
    }
}
