"use client";

import React, { createContext, useContext, type ReactNode } from "react";
import { useWebSocket, type WSMessage, type TimelineStep } from "@/hooks/useWebSocket";

interface WebSocketContextValue {
  socket: WebSocket | null;
  isConnected: boolean;
  messages: WSMessage[];
  timelineSteps: TimelineStep[];
  send: (data: any) => void;
  clearMessages: () => void;
  refreshCreds: () => void;
  pendingCount: number;
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null);

export function WebSocketProvider({ children }: { children: ReactNode }) {
  const ws = useWebSocket();
  return (
    <WebSocketContext.Provider value={ws}>
      {children}
    </WebSocketContext.Provider>
  );
}

export function useWS(): WebSocketContextValue {
  const ctx = useContext(WebSocketContext);
  if (!ctx) throw new Error("useWS must be used within WebSocketProvider");
  return ctx;
}
