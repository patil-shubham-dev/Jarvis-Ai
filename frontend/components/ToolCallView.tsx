"use client";

import { motion, AnimatePresence } from "framer-motion";
import { Wrench, ArrowRight, CheckCircle, XCircle, Loader } from "lucide-react";
import { cn } from "@/lib/utils";

interface ToolCall {
  id: string;
  tool: string;
  status: "pending" | "running" | "success" | "failed";
  args?: string;
  result?: string;
  duration_ms?: number;
}

interface ToolCallViewProps {
  calls: ToolCall[];
  visible: boolean;
}

export function ToolCallView({ calls, visible }: ToolCallViewProps) {
  if (!visible || calls.length === 0) return null;

  return (
    <div className="fixed right-4 bottom-24 z-50 max-w-xs max-sm:hidden" aria-live="polite">
      <AnimatePresence>
        {calls.map((call) => (
          <motion.div
            key={call.id}
            initial={{ opacity: 0, y: 20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.95 }}
            className="mb-2 p-3 rounded-xl bg-card/90 backdrop-blur-lg border border-border shadow-lg"
          >
            <div className="flex items-start gap-3">
              <div className={cn(
                "w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0",
                call.status === "success" ? "bg-success/20" :
                call.status === "failed" ? "bg-destructive/20" :
                call.status === "running" ? "bg-primary/20" : "bg-muted"
              )}>
                {call.status === "success" ? <CheckCircle className="w-4 h-4 text-success" /> :
                 call.status === "failed" ? <XCircle className="w-4 h-4 text-destructive" /> :
                 call.status === "running" ? <Loader className="w-4 h-4 text-primary animate-spin" /> :
                 <Wrench className="w-4 h-4 text-muted-foreground" />}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold text-foreground">{call.tool}</span>
                  {call.duration_ms && (
                    <span className="text-[10px] text-muted-foreground">
                      {(call.duration_ms / 1000).toFixed(1)}s
                    </span>
                  )}
                </div>
                {call.args && (
                  <p className="text-[11px] text-muted-foreground mt-1 truncate">{call.args}</p>
                )}
                {call.result && (
                  <p className="text-[11px] text-foreground mt-1 truncate">
                    <ArrowRight className="w-3 h-3 inline mr-1 text-primary" />
                    {call.result}
                  </p>
                )}
              </div>
            </div>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
