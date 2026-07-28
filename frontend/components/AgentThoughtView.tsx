"use client";

import { motion, AnimatePresence } from "framer-motion";
import { Brain } from "lucide-react";

interface AgentThought {
  agent: string;
  content: string;
  type?: string;
}

interface AgentThoughtViewProps {
  thoughts: AgentThought[];
  visible: boolean;
}

const agentColors: Record<string, string> = {
  orchestrator: "bg-[#F5F2EC] border-[#E5E0D8] text-foreground",
  planner: "bg-[#F5F2EC] border-[#FF8425] text-foreground",
  executor: "bg-[#F5F2EC] border-[#7DAA7D] text-foreground",
  verifier: "bg-[#F5F2EC] border-[#D4A050] text-foreground",
  memory: "bg-[#F5F2EC] border-[#F86607] text-foreground",
};

export function AgentThoughtView({ thoughts, visible }: AgentThoughtViewProps) {
  if (!visible || thoughts.length === 0) return null;

  const latest = thoughts.slice(-3);

  return (
    <div className="fixed left-4 bottom-24 z-50 max-w-xs max-sm:hidden" aria-live="polite">
      <AnimatePresence>
        {latest.map((thought, idx) => (
          <motion.div
            key={`${thought.agent}-${idx}-${thought.content.slice(0, 20)}`}
            initial={{ opacity: 0, x: -20, scale: 0.95 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: -20, scale: 0.95 }}
            transition={{ delay: idx * 0.1 }}
            className={`mb-2 p-3 rounded-xl border ${agentColors[thought.agent] || "bg-card border-border text-foreground"} backdrop-blur-sm`}
          >
            <div className="flex items-start gap-2">
              <Brain className="w-4 h-4 mt-0.5 flex-shrink-0 text-primary" />
              <div className="min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {thought.agent}
                  </span>
                  <div className="w-2 h-2 rounded-full bg-primary/60" />
                </div>
                <p className="text-xs leading-relaxed text-foreground">{thought.content}</p>
              </div>
            </div>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
