"use client";

import { useState, useEffect, useRef, useCallback } from "react";

export interface WSMessage {
  type: string;
  content?: string;
  [key: string]: any;
}

export interface TimelineStep {
  id: string;
  description: string;
  status: "pending" | "running" | "success" | "failed";
  timestamp?: string;
}

interface UseWebSocketReturn {
  socket: WebSocket | null;
  isConnected: boolean;
  messages: WSMessage[];
  timelineSteps: TimelineStep[];
  send: (data: any) => void;
  clearMessages: () => void;
}

const MAX_MESSAGES = 500;
const MAX_RECONNECT_DELAY = 30000;
const BASE_RECONNECT_DELAY = 1000;

function jitter(delay: number): number {
  return delay + Math.random() * delay * 0.5;
}

export function useWebSocket(url: string = "ws://localhost:8000/ws/chat"): UseWebSocketReturn {
  const [socket, setSocket] = useState<WebSocket | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [messages, setMessages] = useState<WSMessage[]>([]);
  const [timelineSteps, setTimelineSteps] = useState<TimelineStep[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout>>(undefined as never);
  const retryCount = useRef(0);
  const mountedRef = useRef(true);
  const pendingMessages = useRef<any[]>([]);

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;
    if (!mountedRef.current) return;

    const ws = new WebSocket(url);
    const currentRetry = retryCount.current;
    let pingInterval: ReturnType<typeof setInterval> | undefined;

    ws.onopen = () => {
      if (!mountedRef.current) {
        ws.close();
        return;
      }
      retryCount.current = 0;
      setIsConnected(true);
      wsRef.current = ws;
      setSocket(ws);

      pingInterval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ action: "ping" }));
        }
      }, 25000);

      while (pendingMessages.current.length > 0) {
        const msg = pendingMessages.current.shift();
        ws.send(JSON.stringify(msg));
      }
    };

    ws.onmessage = (event) => {
      if (!mountedRef.current) return;
      try {
        const data = JSON.parse(event.data);
        if (data.type === "pong") return;
        setMessages((prev) => {
          const updated = [...prev, data];
          return updated.length > MAX_MESSAGES
            ? updated.slice(updated.length - MAX_MESSAGES)
            : updated;
        });

        if (data.type === "plan" && data.steps) {
          setTimelineSteps(
            data.steps.map((s: any, i: number) => ({
              id: s.id || `step_${i}`,
              description: s.description || s.step || `Step ${i + 1}`,
              status: "pending" as const,
            }))
          );
        } else if (data.type === "step_started") {
          setTimelineSteps((prev) =>
            prev.map((s) =>
              s.id === data.step_id ? { ...s, status: "running" as const } : s
            )
          );
        } else if (data.type === "step_completed") {
          setTimelineSteps((prev) =>
            prev.map((s) =>
              s.id === data.step_id
                ? { ...s, status: (data.status === "completed" ? "success" : "failed") as "success" | "failed", timestamp: new Date().toLocaleTimeString() }
                : s
            )
          );
        }
      } catch (e) {
        if (mountedRef.current) {
          console.error("[WS] Parse error:", e);
        }
      }
    };

    ws.onclose = () => {
      if (pingInterval) clearInterval(pingInterval);
      if (!mountedRef.current) return;
      setIsConnected(false);
      setSocket(null);
      wsRef.current = null;

      const delay = jitter(Math.min(BASE_RECONNECT_DELAY * Math.pow(2, currentRetry), MAX_RECONNECT_DELAY));
      retryCount.current = currentRetry + 1;
      reconnectTimer.current = setTimeout(connect, delay);
    };

    ws.onerror = () => {
      ws.close();
    };
  }, [url]);

  useEffect(() => {
    mountedRef.current = true;
    connect();
    return () => {
      mountedRef.current = false;
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current);
      }
      if (wsRef.current) {
        const ws = wsRef.current;
        ws.onopen = null;
        ws.onmessage = null;
        ws.onclose = null;
        ws.onerror = null;
        ws.close();
        wsRef.current = null;
      }
      setMessages([]);
      setTimelineSteps([]);
      setIsConnected(false);
    };
  }, [connect]);

  const send = useCallback(
    (data: any) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify(data));
      } else {
        pendingMessages.current.push(data);
      }
    },
    []
  );

  const clearMessages = useCallback(() => {
    setMessages([]);
    setTimelineSteps([]);
  }, []);

  return { socket, isConnected, messages, timelineSteps, send, clearMessages };
}
