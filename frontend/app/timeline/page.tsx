"use client";

import { useState, useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ListChecks, CheckCircle, Circle, Loader, XCircle, ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { useWS } from "@/hooks/WebSocketContext";

interface TimelineEntry {
  id: string;
  type: "plan" | "step" | "tool" | "thought" | "message";
  description: string;
  status: "pending" | "running" | "success" | "failed";
  timestamp: string;
  agent?: string;
  duration_ms?: number;
}

function toEntry(s: any): TimelineEntry {
  return {
    id: s.id,
    type: s.type || "step",
    description: s.description || s.step || s.content || "",
    status: s.status || "pending",
    timestamp: s.timestamp || "",
    agent: s.agent,
    duration_ms: s.duration_ms,
  };
}

function wsToTimeline(messages: any[]): TimelineEntry[] {
  const result: TimelineEntry[] = [];
  for (const m of messages) {
    if (m.type === "plan" && m.steps) {
      m.steps.forEach((s: any, i: number) => {
        result.push(toEntry({ ...s, id: s.id || `step_${i}`, timestamp: m.timestamp, type: "step" }));
      });
    } else if (m.type === "thought") {
      result.push(toEntry({ id: `thought_${result.length}`, description: m.content, agent: m.agent || "orchestrator", status: "success", timestamp: m.timestamp, type: "thought" }));
    } else if (m.type === "tool") {
      result.push(toEntry({ id: `tool_${result.length}`, description: `${m.tool}${m.args ? `(${String(m.args).slice(0, 60)})` : ""}`, agent: "executor", status: m.status || "success", timestamp: m.timestamp, duration_ms: m.duration_ms, type: "tool" }));
    } else if (m.type === "step_started") {
      result.push(toEntry({ id: m.step_id, description: m.content || "Step started", status: "running", timestamp: m.timestamp, type: "step" }));
    } else if (m.type === "step_completed") {
      result.push(toEntry({ id: m.step_id, description: m.content || "Step completed", status: m.status === "completed" ? "success" : "failed", timestamp: m.timestamp, type: "step" }));
    } else if (m.type === "stream_start") {
      result.push(toEntry({ id: `msg_${result.length}`, description: "Assistant response started", status: "running", timestamp: m.timestamp, type: "message" }));
    } else if (m.type === "stream_end") {
      result.push(toEntry({ id: `msg_${result.length}`, description: "Response complete", status: "success", timestamp: m.timestamp, type: "message" }));
    }
  }
  return result;
}

const typeConfig: Record<string, { icon: any; color: string; bg: string }> = {
  plan: { icon: ListChecks, color: "text-[#F86607]", bg: "bg-[#F86607]/10" },
  step: { icon: ArrowRight, color: "text-primary", bg: "bg-primary/10" },
  tool: { icon: Loader, color: "text-success", bg: "bg-success/10" },
  thought: { icon: Circle, color: "text-[#D4A050]", bg: "bg-[#D4A050]/10" },
  message: { icon: CheckCircle, color: "text-[#F86607]", bg: "bg-[#F86607]/10" },
};

const statusIcons: Record<string, any> = {
  pending: Circle,
  running: Loader,
  success: CheckCircle,
  failed: XCircle,
};

const statusColors: Record<string, string> = {
  pending: "text-muted-foreground/40",
  running: "text-primary",
  success: "text-success",
  failed: "text-destructive",
};

export default function TimelinePage() {
  const { messages } = useWS();
  const [filterType, setFilterType] = useState<string>("all");

  const allEntries = useMemo(() => wsToTimeline(messages), [messages]);

  const filtered = filterType === "all"
    ? allEntries
    : allEntries.filter((e) => e.type === filterType);

  return (
    <div className="min-h-screen bg-background p-6 pb-24">
      <div className="max-w-3xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-success/10 flex items-center justify-center">
              <ListChecks className="w-5 h-5 text-success" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-foreground">Action Timeline</h1>
              <p className="text-sm text-muted-foreground">Real-time agent execution trace</p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2 mb-6 overflow-x-auto pb-2">
          {["all", "plan", "step", "tool", "thought"].map((type) => (
            <button
              key={type}
              onClick={() => setFilterType(type)}
              className={cn(
                "px-3 py-1.5 rounded-lg text-xs font-medium transition-all whitespace-nowrap flex items-center gap-1.5",
                filterType === type
                  ? "bg-foreground text-background"
                  : "bg-muted text-muted-foreground hover:bg-muted/80"
              )}
            >
              {type !== "all" && typeConfig[type as keyof typeof typeConfig] && (
                <span className={cn("w-2 h-2 rounded-full", "bg-current opacity-50")} />
              )}
              {type.charAt(0).toUpperCase() + type.slice(1)}
            </button>
          ))}
        </div>

        <div className="relative">
          <div className="absolute left-[23px] top-0 bottom-0 w-px bg-border" />

          <AnimatePresence>
            {filtered.map((entry, idx) => {
              const Icon = statusIcons[entry.status];
              const TypeIcon = typeConfig[entry.type]?.icon;

              return (
                <motion.div
                  key={entry.id}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  className="relative flex items-start gap-4 pb-6 pl-0"
                >
                  <div className={cn(
                    "relative z-10 w-[46px] h-[46px] rounded-xl flex items-center justify-center flex-shrink-0",
                    typeConfig[entry.type]?.bg || "bg-muted"
                  )}>
                    {TypeIcon && <TypeIcon className={cn("w-5 h-5", typeConfig[entry.type]?.color)} />}
                  </div>

                  <div className="flex-1 min-w-0 pt-2">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-medium text-foreground">{entry.description}</span>
                      <Icon className={cn("w-4 h-4 flex-shrink-0", statusColors[entry.status], entry.status === "running" && "animate-spin")} />
                    </div>
                    <div className="flex items-center gap-3 text-[10px] text-muted-foreground">
                      <span>{entry.timestamp}</span>
                      {entry.agent && (
                        <span className="px-1.5 py-0.5 rounded bg-muted">{entry.agent}</span>
                      )}
                      {entry.duration_ms && (
                        <span>{(entry.duration_ms / 1000).toFixed(1)}s</span>
                      )}
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </AnimatePresence>

          {filtered.length === 0 && (
            <div className="text-center py-12">
              <ListChecks className="w-12 h-12 text-muted-foreground/20 mx-auto mb-4" />
              <p className="text-muted-foreground text-sm">No timeline entries</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
