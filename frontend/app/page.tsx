"use client";

import { useState, useEffect, useRef } from "react";
import { Orb } from "@/components/Orb";
import { EdgeGlow } from "@/components/EdgeGlow";
import { motion, AnimatePresence } from "framer-motion";
import { Send, Mic, Settings, MessageSquare, Brain, Activity, X, ChevronUp } from "lucide-react";
import { cn } from "@/lib/utils";

export default function Home() {
  const [messages, setMessages] = useState<{ role: string; content: string; type?: string }[]>([]);
  const [input, setInput] = useState("");
  const [state, setState] = useState<"idle" | "thinking" | "listening" | "speaking">("idle");
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [socket, setSocket] = useState<WebSocket | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let ws: WebSocket;
    let reconnectInterval: any;

    const connect = () => {
      ws = new WebSocket("ws://localhost:8000/ws/chat");
      
      ws.onopen = () => {
        console.log("Connected to Jarvis Backend");
        setSocket(ws);
      };

      ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        if (data.type === "thought") {
          setState("thinking");
        } else if (data.type === "action" && data.action === "vision_scan") {
          setState("thinking");
        } else if (data.type === "message") {
          setState("speaking");
          setMessages((prev) => [...prev, { role: "assistant", content: data.content }]);
          if (!isChatOpen) setIsChatOpen(true);
          setTimeout(() => setState("idle"), 3000);
        }
      };

      ws.onclose = () => {
        console.log("WebSocket disconnected. Retrying in 3s...");
        setSocket(null);
        reconnectInterval = setTimeout(connect, 3000);
      };
    };

    connect();
    return () => {
      if (ws) ws.close();
      if (reconnectInterval) clearTimeout(reconnectInterval);
    };
  }, [isChatOpen]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = () => {
    if (!input.trim() || !socket) return;
    const userMsg = { role: "user", content: input };
    setMessages((prev) => [...prev, userMsg]);
    socket.send(JSON.stringify({ text: input }));
    setInput("");
    setState("thinking");
  };

  return (
    <main className="relative flex flex-col items-center justify-center min-h-screen bg-white overflow-hidden font-sans text-gray-900">
      <EdgeGlow 
        active={state !== "idle"} 
        type={state === "thinking" ? "thinking" : state === "speaking" ? "executing" : state === "listening" ? "listening" : "thinking"} 
      />
      
      {/* Premium Ambient Background */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_50%,rgba(0,122,255,0.03),transparent_70%)] pointer-events-none" />
      
      {/* Top Status Bar (Mobile Style) */}
      <header className="absolute top-0 w-full p-6 flex justify-between items-center z-20">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-blue-600/10 backdrop-blur-md border border-blue-600/20 flex items-center justify-center">
            <span className="text-blue-600 font-bold text-xs">J</span>
          </div>
          <span className="text-sm font-medium tracking-widest text-gray-400 uppercase">Jarvis OS</span>
        </div>
        <button className="p-2 rounded-full bg-black/5 backdrop-blur-md border border-black/5">
          <Settings className="w-5 h-5 text-gray-600" />
        </button>
      </header>

      {/* Central Living Orb */}
      <div className={cn(
        "transition-all duration-700 ease-in-out",
        isChatOpen ? "scale-75 -translate-y-32" : "scale-100"
      )}>
        <Orb state={state} />
      </div>

      {/* Interaction State Label */}
      <motion.div 
        animate={{ opacity: [0.4, 0.7, 0.4] }}
        transition={{ duration: 3, repeat: Infinity }}
        className="absolute top-[60%] text-center"
      >
        <p className="text-xs tracking-[0.3em] uppercase text-blue-600 font-medium">
          {state === "idle" && "System Ready"}
          {state === "thinking" && "Processing..."}
          {state === "listening" && "Listening..."}
          {state === "speaking" && "Jarvis Speaking"}
        </p>
      </motion.div>

      {/* Bottom Controls */}
      <div className={cn(
        "absolute bottom-0 w-full transition-transform duration-500 z-30",
        isChatOpen ? "translate-y-full" : "translate-y-0"
      )}>
        <div className="p-8 pb-12 flex justify-center gap-8 items-center bg-gradient-to-t from-white to-transparent">
          <button className="p-4 rounded-full bg-black/5 border border-black/5 text-gray-400 hover:text-black transition-all">
            <Brain className="w-6 h-6" />
          </button>
          
          <button 
            onClick={() => setState(state === "listening" ? "idle" : "listening")}
            className={cn(
              "p-6 rounded-full shadow-[0_10px_30px_rgba(59,130,246,0.3)] transition-all",
              state === "listening" ? "bg-red-500 scale-110" : "bg-blue-600 hover:bg-blue-500"
            )}
          >
            <Mic className="w-8 h-8 text-white" />
          </button>
 
          <button 
            onClick={() => setIsChatOpen(true)}
            className="p-4 rounded-full bg-black/5 border border-black/5 text-gray-400 hover:text-black transition-all"
          >
            <MessageSquare className="w-6 h-6" />
          </button>
        </div>
      </div>

      {/* Bottom Sheet Chat */}
      <AnimatePresence>
        {isChatOpen && (
          <motion.div
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ type: "spring", damping: 25, stiffness: 200 }}
            className="absolute inset-0 z-40 bg-white flex flex-col"
          >
            {/* Sheet Header */}
            <div className="p-4 border-b border-gray-100 flex justify-between items-center">
              <button onClick={() => setIsChatOpen(false)} className="p-2">
                <X className="w-6 h-6 text-gray-400" />
              </button>
              <div className="flex flex-col items-center">
                <div className="w-12 h-1 bg-gray-100 rounded-full mb-2" />
                <span className="text-xs font-semibold text-blue-600 uppercase tracking-widest">Conversation</span>
              </div>
              <div className="w-10" /> {/* Spacer */}
            </div>

            {/* Chat Content */}
            <div 
              ref={scrollRef}
              className="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar"
            >
              {messages.length === 0 && (
                <div className="h-full flex flex-col items-center justify-center opacity-30 text-center px-12">
                  <MessageSquare className="w-12 h-12 mb-4" />
                  <p className="text-sm">No recent messages. Start a conversation with Jarvis.</p>
                </div>
              )}
              {messages.map((msg, idx) => (
                <motion.div
                  key={idx}
                  initial={{ opacity: 0, x: msg.role === "user" ? 20 : -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  className={cn(
                    "flex flex-col max-w-[85%]",
                    msg.role === "user" ? "ml-auto items-end" : "mr-auto items-start"
                  )}
                >
                  <div className={cn(
                    "p-4 rounded-2xl text-sm leading-relaxed",
                    msg.role === "user" 
                      ? "bg-blue-600 text-white rounded-tr-none shadow-[0_5px_15px_rgba(37,99,235,0.2)]" 
                      : "bg-gray-100 text-gray-800 rounded-tl-none"
                  )}>
                    {msg.content}
                  </div>
                  <span className="text-[10px] uppercase tracking-tighter text-gray-400 mt-2">
                    {msg.role === "user" ? "You" : "Jarvis"}
                  </span>
                </motion.div>
              ))}
            </div>

            {/* Sticky Chat Input */}
            <div className="p-6 bg-gradient-to-t from-white to-transparent">
              <div className="bg-gray-100 border border-gray-200 rounded-3xl p-2 flex items-center gap-2">
                <input 
                  type="text" 
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleSend()}
                  placeholder="Type a message..."
                  className="flex-1 bg-transparent border-none outline-none text-sm px-4 text-gray-900 placeholder:text-gray-400"
                />
                <button 
                  onClick={handleSend}
                  className="p-3 bg-blue-600 rounded-2xl hover:bg-blue-500 transition-colors shadow-lg shadow-blue-600/20"
                >
                  <Send className="w-5 h-5 text-white" />
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </main>
  );
}
