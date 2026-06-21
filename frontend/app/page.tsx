"use client";

import { useState, useEffect, useRef } from "react";
import { Orb } from "@/components/Orb";
import { EdgeGlow } from "@/components/EdgeGlow";
import { StreamMessage } from "@/components/StreamMessage";
import { MemoryExplorer } from "@/components/MemoryExplorer";
import { ActionTimeline } from "@/components/ActionTimeline";
import { AgentThoughtView } from "@/components/AgentThoughtView";
import { ToolCallView } from "@/components/ToolCallView";
import { useWS } from "@/hooks/WebSocketContext";
import { motion, AnimatePresence } from "framer-motion";
import { Send, Mic, Settings, MessageSquare, X } from "lucide-react";
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
function nextId() {
  return `msg_${++msgCounter}_${Date.now()}`;
}

export default function Home() {
  const router = useRouter();
  const { socket, isConnected, messages, timelineSteps, send } = useWS();
  const [displayMessages, setDisplayMessages] = useState<DisplayMessage[]>([]);
  const [input, setInput] = useState("");
  const [state, setState] = useState<"idle" | "thinking" | "listening" | "speaking">("idle");
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [showTimeline, setShowTimeline] = useState(false);
  const [agentThoughts, setAgentThoughts] = useState<AgentThought[]>([]);
  const [toolCalls, setToolCalls] = useState<ToolCall[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const processingRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [displayMessages]);

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

      case "stream_start":
        setDisplayMessages((prev) => [
          ...prev,
          { id: nextId(), role: "assistant", content: "", isStreaming: true, timestamp: lastMsg.timestamp },
        ]);
        setState("speaking");
        break;

      case "stream_end":
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
        setTimeout(() => { setState("idle"); setShowTimeline(false); }, 1000);
        break;

      case "message":
        if (lastMsg.content) {
          setDisplayMessages((prev) => [
            ...prev,
            { id: nextId(), role: "assistant", content: lastMsg.content ?? "", timestamp: lastMsg.timestamp },
          ]);
          setState("speaking");
          if (!isChatOpen) setIsChatOpen(true);
          setTimeout(() => { setState("idle"); setShowTimeline(false); }, 2000);
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
  }, [messages]);

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
  };

  return (
    <main className="relative flex flex-col items-center justify-center min-h-screen bg-background overflow-hidden font-sans text-foreground">
      <EdgeGlow
        active={state !== "idle"}
        type={state === "thinking" ? "thinking" : state === "speaking" ? "executing" : state === "listening" ? "listening" : "thinking"}
      />

      <MemoryExplorer />
      <ActionTimeline steps={timelineSteps} visible={showTimeline} />
      <AgentThoughtView thoughts={agentThoughts} visible={agentThoughts.length > 0} />
      <ToolCallView calls={toolCalls} visible={toolCalls.length > 0} />

      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_50%,rgba(196,149,106,0.04),transparent_70%)] pointer-events-none" />

      <header className="absolute top-0 w-full p-6 flex justify-between items-center z-20">
        <div className="flex items-center gap-3">
          <div className={cn(
            "w-2.5 h-2.5 rounded-full transition-colors",
            isConnected ? "bg-success" : "bg-destructive"
          )} />
          <span className="text-sm font-medium tracking-widest text-muted-foreground uppercase">
            {isConnected ? "Jarvis OS" : "Disconnected"}
          </span>
        </div>
        <button onClick={() => router.push("/settings")} className="p-2 rounded-full bg-card backdrop-blur-md border border-border">
          <Settings className="w-5 h-5 text-muted-foreground" />
        </button>
      </header>

      <div className={cn(
        "transition-all duration-700 ease-in-out",
        isChatOpen ? "scale-75 -translate-y-32" : "scale-100"
      )}>
        <Orb state={state} />
      </div>

      <motion.div
        animate={{ opacity: [0.4, 0.7, 0.4] }}
        transition={{ duration: 3, repeat: Infinity }}
        className="absolute top-[60%] text-center"
      >
        <p className="text-xs tracking-[0.3em] uppercase text-primary font-medium">
          {state === "idle" && "System Ready"}
          {state === "thinking" && "Processing..."}
          {state === "listening" && "Listening..."}
          {state === "speaking" && "Jarvis Speaking"}
        </p>
      </motion.div>

      <div className={cn(
        "absolute bottom-0 w-full transition-transform duration-500 z-30",
        isChatOpen ? "translate-y-full" : "translate-y-0"
      )}>
        <div className="p-8 pb-12 flex justify-center gap-4 items-center bg-gradient-to-t from-background to-transparent">
          <button
            onClick={() => setIsChatOpen(true)}
            className="p-4 rounded-full bg-card border border-border text-muted-foreground hover:text-foreground transition-all"
          >
            <MessageSquare className="w-6 h-6" />
          </button>

          <button
            onClick={() => setState(state === "listening" ? "idle" : "listening")}
            className={cn(
              "p-6 rounded-full shadow-lg transition-all",
              state === "listening" ? "bg-destructive scale-110" : "bg-primary hover:bg-[#DA4800]"
            )}
          >
            <Mic className="w-8 h-8 text-primary-foreground" />
          </button>
        </div>
      </div>

      <AnimatePresence>
        {isChatOpen && (
          <motion.div
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ type: "spring", damping: 25, stiffness: 200 }}
            className="absolute inset-0 z-40 bg-background flex flex-col"
          >
            <div className="p-4 border-b border-border flex justify-between items-center">
              <button onClick={() => setIsChatOpen(false)} className="p-2">
                <X className="w-6 h-6 text-muted-foreground" />
              </button>
              <div className="flex flex-col items-center">
                <div className="w-12 h-1 bg-border rounded-full mb-2" />
                <span className="text-xs font-semibold text-primary uppercase tracking-widest">Conversation</span>
              </div>
              <div className="w-10" />
            </div>

            <div
              ref={scrollRef}
              className="flex-1 overflow-y-auto p-6 space-y-4 custom-scrollbar"
            >
              {displayMessages.length === 0 && (
                <div className="h-full flex flex-col items-center justify-center opacity-40 text-center px-12">
                  <MessageSquare className="w-12 h-12 mb-4 text-muted-foreground" />
                  <p className="text-sm text-muted-foreground">Start a conversation with Jarvis</p>
                  <p className="text-xs text-muted-foreground/60 mt-2">Ask me anything or give me a task</p>
                </div>
              )}
              {displayMessages.map((msg) => (
                <StreamMessage
                  key={msg.id}
                  content={msg.content}
                  role={msg.role}
                  timestamp={msg.timestamp}
                  isStreaming={msg.isStreaming}
                />
              ))}
            </div>

            <div className="p-6 bg-gradient-to-t from-background to-transparent">
              <div className="bg-card border border-border rounded-3xl p-2 flex items-center gap-2 shadow-sm">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleSend()}
                  placeholder={isConnected ? "Ask Jarvis anything..." : "Reconnecting..."}
                  disabled={!isConnected}
                  className="flex-1 bg-transparent border-none outline-none text-sm px-4 text-foreground placeholder:text-muted-foreground/60 disabled:opacity-50"
                />
                <button
                  onClick={handleSend}
                  disabled={!input.trim() || !isConnected}
                  className={cn(
                    "p-3 rounded-2xl transition-all",
                    input.trim() && isConnected
                      ? "bg-primary hover:bg-[#DA4800] shadow-sm text-white"
                      : "bg-muted text-muted-foreground"
                  )}
                >
                  <Send className="w-5 h-5" />
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </main>
  );
}
