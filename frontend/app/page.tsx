"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { Orb } from "@/components/Orb";
import { EdgeGlow } from "@/components/EdgeGlow";
import { StreamMessage } from "@/components/StreamMessage";
import { MemoryExplorer } from "@/components/MemoryExplorer";
import { ActionTimeline } from "@/components/ActionTimeline";
import { AgentThoughtView } from "@/components/AgentThoughtView";
import { ToolCallView } from "@/components/ToolCallView";
import { useWS } from "@/hooks/WebSocketContext";
import { motion, AnimatePresence } from "framer-motion";
import {
  Send, Mic, Settings, MessageSquare, Zap,
  Trash2, ChevronDown,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { cn } from "@/lib/utils";

interface AgentThought {
  agent: string;
  content: string;
  type?: string;
}

interface ToolCall {
  id: string;
  tool: string;
  status: "pending" | "running" | "success" | "failed";
  args?: string;
  result?: string;
  duration_ms?: number;
}

interface DisplayMessage {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  timestamp?: string;
  isStreaming?: boolean;
}

let msgCounter = 0;
function nextId() { return `msg_${++msgCounter}_${Date.now()}`; }

export default function Home() {
  const router = useRouter();
  const { socket, isConnected, messages, timelineSteps, send, clearMessages } = useWS();
  const [displayMessages, setDisplayMessages] = useState<DisplayMessage[]>([]);
  const [input, setInput] = useState("");
  const [state, setState] = useState<"idle" | "thinking" | "listening" | "speaking">("idle");
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [showTimeline, setShowTimeline] = useState(false);
  const [agentThoughts, setAgentThoughts] = useState<AgentThought[]>([]);
  const [toolCalls, setToolCalls] = useState<ToolCall[]>([]);
  const [showScrollBtn, setShowScrollBtn] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const processingRef = useRef<Set<string>>(new Set());
  const streamBufferRef = useRef<string>("");

  const scrollToBottom = useCallback((force = false) => {
    if (scrollRef.current) {
      const el = scrollRef.current;
      const dist = el.scrollHeight - el.scrollTop - el.clientHeight;
      if (force || dist < 120) {
        el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
      }
    }
  }, []);

  useEffect(() => { scrollToBottom(); }, [displayMessages]);

  const handleScroll = useCallback(() => {
    if (scrollRef.current) {
      const el = scrollRef.current;
      const dist = el.scrollHeight - el.scrollTop - el.clientHeight;
      setShowScrollBtn(dist > 150);
    }
  }, []);

  useEffect(() => {
    if (messages.length === 0) return;
    const lastMsg = messages[messages.length - 1];
    const msgKey = `${lastMsg.type}_${lastMsg.content}_${messages.length}`;
    if (processingRef.current.has(msgKey)) return;
    processingRef.current.add(msgKey);
    if (processingRef.current.size > 200) {
      processingRef.current = new Set([...processingRef.current].slice(-100));
    }

    switch (lastMsg.type) {
      case "user_message":
        setDisplayMessages((prev) => [
          ...prev,
          { id: nextId(), role: "user", content: lastMsg.content || "", timestamp: lastMsg.timestamp },
        ]);
        break;

      case "thought":
        setState("thinking");
        setAgentThoughts((prev) => [
          ...prev,
          { agent: lastMsg.agent || "orchestrator", content: lastMsg.content || "", type: "reasoning" },
        ]);
        break;

      case "plan":
        setShowTimeline(true);
        break;

      case "step_started":
        setState("thinking");
        break;

      case "tool":
        if (lastMsg.tool) {
          setToolCalls((prev) => [
            ...prev,
            {
              id: `tool_${Date.now()}`,
              tool: lastMsg.tool,
              status: lastMsg.status || "success",
              args: lastMsg.args ? JSON.stringify(lastMsg.args).slice(0, 100) : undefined,
              result: lastMsg.result ? String(lastMsg.result).slice(0, 100) : undefined,
              duration_ms: lastMsg.duration_ms,
            },
          ]);
        }
        break;

      case "stream_token":
        streamBufferRef.current += lastMsg.content || "";
        setDisplayMessages((prev) => {
          if (prev.length > 0 && prev[prev.length - 1].isStreaming) {
            const updated = [...prev];
            const last = updated[updated.length - 1];
            updated[updated.length - 1] = {
              ...last,
              content: last.content + (lastMsg.content || ""),
            };
            return updated;
          }
          return prev;
        });
        break;

      case "stream_start": {
        streamBufferRef.current = "";
        const hasAssistant = displayMessages.some(m => m.isStreaming);
        if (!hasAssistant) {
          setDisplayMessages((prev) => [
            ...prev,
            { id: nextId(), role: "assistant", content: "", isStreaming: true, timestamp: lastMsg.timestamp },
          ]);
        }
        setState("speaking");
        if (!isChatOpen) setIsChatOpen(true);
        break;
      }

      case "stream_end":
        streamBufferRef.current = "";
        setDisplayMessages((prev) => {
          if (prev.length > 0 && prev[prev.length - 1].isStreaming) {
            const updated = [...prev];
            const last = updated[updated.length - 1];
            updated[updated.length - 1] = {
              ...last,
              isStreaming: false,
              timestamp: lastMsg.timestamp || last.timestamp,
            };
            return updated;
          }
          return prev;
        });
        setTimeout(() => { setState("idle"); setShowTimeline(false); }, 500);
        break;

      case "message":
        if (lastMsg.content) {
          setDisplayMessages((prev) => [
            ...prev,
            { id: nextId(), role: "assistant", content: lastMsg.content ?? "", timestamp: lastMsg.timestamp },
          ]);
          setState("speaking");
          if (!isChatOpen) setIsChatOpen(true);
          setTimeout(() => { setState("idle"); setShowTimeline(false); }, 1500);
        }
        break;

      case "error":
        setDisplayMessages((prev) => [
          ...prev,
          { id: nextId(), role: "system", content: lastMsg.content || "An error occurred" },
        ]);
        setState("idle");
        setShowTimeline(false);
        break;
    }
  }, [messages, isChatOpen, displayMessages]);

  const handleSend = () => {
    const text = input.trim();
    if (!text) return;

    if (!isConnected || !socket) {
      setDisplayMessages((prev) => [
        ...prev,
        { id: nextId(), role: "system", content: "Cannot send message: not connected to server" },
      ]);
      return;
    }

    setDisplayMessages((prev) => [
      ...prev,
      { id: nextId(), role: "user", content: text, timestamp: new Date().toISOString() },
    ]);
    send({ text });
    setInput("");
    setState("thinking");
    if (!isChatOpen) setIsChatOpen(true);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <main className="relative flex flex-col items-center justify-center min-h-dvh bg-background overflow-hidden font-sans text-foreground safe-area">
      <EdgeGlow
        active={state !== "idle"}
        type={state === "thinking" ? "thinking" : state === "speaking" ? "executing" : state === "listening" ? "listening" : "idle"}
      />

      <MemoryExplorer />
      <ActionTimeline steps={timelineSteps} visible={showTimeline} />
      <AgentThoughtView thoughts={agentThoughts} visible={agentThoughts.length > 0} />
      <ToolCallView calls={toolCalls} visible={toolCalls.length > 0} />

      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_50%,rgba(196,149,106,0.04),transparent_70%)] pointer-events-none" />

      <header className="absolute top-0 w-full p-4 sm:p-6 flex justify-between items-center z-20">
        <div className="flex items-center gap-2.5">
          <div className={cn(
            "w-2.5 h-2.5 rounded-full transition-all duration-500",
            isConnected ? "bg-success shadow-[0_0_8px_rgba(125,170,125,0.5)]" : "bg-destructive"
          )} />
          <span className="text-xs sm:text-sm font-medium tracking-widest text-muted-foreground uppercase">
            {isConnected ? "Jarvis OS" : "Disconnected"}
          </span>
          {isConnected && (
            <span className="text-[10px] text-muted-foreground/50 hidden sm:inline">v4.1</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {displayMessages.length > 0 && (
            <button
              onClick={() => { setDisplayMessages([]); clearMessages(); }}
              className="p-2 rounded-full bg-card/80 backdrop-blur-md border border-border text-muted-foreground hover:text-foreground transition-all"
              title="Clear chat"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
          <button
            onClick={() => router.push("/settings")}
            className="p-2 rounded-full bg-card/80 backdrop-blur-md border border-border text-muted-foreground hover:text-foreground transition-all"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </header>

      <div className={cn(
        "transition-[transform,translate] duration-700 ease-[cubic-bezier(0.34,1.56,0.64,1)]",
        isChatOpen ? "scale-[0.6] sm:scale-75 -translate-y-24 sm:-translate-y-32" : "scale-100"
      )}>
        <Orb state={state} />
      </div>

      <div className="absolute top-[60%] text-center">
        <p className="text-[10px] sm:text-xs tracking-[0.3em] uppercase text-primary font-medium opacity-60">
          {state === "idle" && (isConnected ? "System Ready" : "Awaiting Connection")}
          {state === "thinking" && "Processing..."}
          {state === "listening" && "Listening..."}
          {state === "speaking" && "Jarvis Speaking"}
        </p>
      </div>

      {!isChatOpen && (
        <div className="absolute bottom-0 w-full transition-all duration-500 z-30">
          <div className="p-4 sm:p-8 pb-8 sm:pb-12 flex justify-center gap-3 sm:gap-4 items-center bg-gradient-to-t from-background/90 to-transparent">
            <button
              onClick={() => setIsChatOpen(true)}
              className="p-3 sm:p-4 rounded-full bg-card/90 backdrop-blur-md border border-border text-muted-foreground hover:text-foreground transition-all shadow-lg hover:shadow-xl"
            >
              <MessageSquare className="w-5 h-5 sm:w-6 sm:h-6" />
            </button>

            <button
              onClick={() => setState(state === "listening" ? "idle" : "listening")}
              className={cn(
                "p-5 sm:p-6 rounded-full shadow-xl transition-all",
                state === "listening" ? "bg-destructive scale-110" : "bg-primary hover:bg-[#DA4800]"
              )}
            >
              <Mic className="w-6 h-6 sm:w-8 sm:h-8 text-primary-foreground" />
            </button>
          </div>
        </div>
      )}

      <AnimatePresence>
        {isChatOpen && (
          <motion.div
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ type: "spring", damping: 28, stiffness: 200 }}
            className="absolute inset-0 z-40 bg-background flex flex-col"
          >
            <div className="px-3 py-2.5 sm:px-4 sm:py-3 border-b border-border flex justify-between items-center">
              <div className="flex items-center gap-2">
                <button onClick={() => setIsChatOpen(false)} className="p-1.5 rounded-lg hover:bg-muted transition-colors">
                  <ChevronDown className="w-5 h-5 text-muted-foreground" />
                </button>
                <div className={cn(
                  "w-2 h-2 rounded-full transition-colors",
                  isConnected ? "bg-success" : "bg-destructive"
                )} />
              </div>
              <span className="text-[10px] font-semibold text-primary uppercase tracking-widest">Jarvis AI</span>
              <button onClick={() => { setDisplayMessages([]); clearMessages(); }} className="p-1.5 rounded-lg hover:bg-muted transition-colors">
                <Trash2 className="w-4 h-4 text-muted-foreground" />
              </button>
            </div>

            <div
              ref={scrollRef}
              onScroll={handleScroll}
              className="flex-1 overflow-y-auto px-3 sm:px-6 py-4 sm:py-6 space-y-3 sm:space-y-4 custom-scrollbar"
            >
              {displayMessages.length === 0 && (
                <div className="h-full flex flex-col items-center justify-center opacity-30 text-center px-8">
                  <Zap className="w-10 h-10 sm:w-12 sm:h-12 mb-3 sm:mb-4 text-muted-foreground" />
                  <p className="text-sm text-muted-foreground">Start a conversation with Jarvis</p>
                  <p className="text-[11px] text-muted-foreground/60 mt-1.5">Ask me anything or give me a task</p>
                  <div className="mt-6 flex flex-wrap gap-2 justify-center">
                    {["Hello!", "What can you do?", "Search the web", "Set a reminder"].map((s) => (
                      <button
                        key={s}
                        onClick={() => { setInput(s); inputRef.current?.focus(); }}
                        className="px-3 py-1.5 text-[11px] bg-muted rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted/80 transition-colors"
                      >
                        {s}
                      </button>
                    ))}
                  </div>
                </div>
              )}
              <AnimatePresence>
                {displayMessages.map((msg) => (
                  <StreamMessage
                    key={msg.id}
                    content={msg.content}
                    role={msg.role}
                    timestamp={msg.timestamp}
                    isStreaming={msg.isStreaming}
                  />
                ))}
              </AnimatePresence>
            </div>

            <AnimatePresence>
              {showScrollBtn && (
                <motion.button
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 10 }}
                  onClick={() => scrollToBottom(true)}
                  className="absolute bottom-24 right-6 p-2 bg-card/90 backdrop-blur-md border border-border rounded-full shadow-lg text-muted-foreground hover:text-foreground z-10"
                >
                  <ChevronDown className="w-4 h-4" />
                </motion.button>
              )}
            </AnimatePresence>

            <div className="px-3 sm:px-6 py-3 sm:py-4 bg-gradient-to-t from-background via-background to-transparent">
              <div className="bg-card border border-border rounded-2xl sm:rounded-3xl p-1.5 sm:p-2 flex items-center gap-1.5 sm:gap-2 shadow-sm focus-within:border-primary/50 transition-colors">
                <input
                  ref={inputRef}
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder={isConnected ? "Ask Jarvis anything..." : "Reconnecting..."}
                  disabled={!isConnected}
                  className="flex-1 bg-transparent border-none outline-none text-sm px-3 sm:px-4 py-2 text-foreground placeholder:text-muted-foreground/50 disabled:opacity-40"
                />
                <button
                  onClick={() => setState(state === "listening" ? "idle" : "listening")}
                  className={cn(
                    "p-2 rounded-xl transition-all flex-shrink-0",
                    state === "listening" ? "bg-destructive/20 text-destructive" : "text-muted-foreground hover:text-foreground"
                  )}
                >
                  <Mic className="w-4 h-4" />
                </button>
                <button
                  onClick={handleSend}
                  disabled={!input.trim() || !isConnected}
                  className={cn(
                    "p-2.5 rounded-xl transition-all flex-shrink-0",
                    input.trim() && isConnected
                      ? "bg-primary hover:bg-[#DA4800] text-white shadow-sm"
                      : "bg-muted text-muted-foreground"
                  )}
                >
                  <Send className="w-4 h-4" />
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </main>
  );
}
