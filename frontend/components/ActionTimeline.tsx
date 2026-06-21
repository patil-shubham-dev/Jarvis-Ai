"use client";

import { motion, AnimatePresence } from "framer-motion";
import { CheckCircle, Circle, Loader, XCircle, ArrowRight, Clock } from "lucide-react";
import { cn } from "@/lib/utils";

interface TimelineStep {
  id: string;
  description: string;
  status: "pending" | "running" | "success" | "failed";
  timestamp?: string;
}

interface ActionTimelineProps {
  steps: TimelineStep[];
  visible: boolean;
}

const statusIcons: Record<string, React.ComponentType<{ className?: string }>> = {
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

export function ActionTimeline({ steps, visible }: ActionTimelineProps) {
  if (!visible || steps.length === 0) return null;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 20 }}
      className="fixed right-4 top-20 w-72 z-50"
    >
      <div className="bg-card/95 backdrop-blur-xl border border-border rounded-2xl shadow-2xl overflow-hidden">
        <div className="p-4 border-b border-border">
          <div className="flex items-center gap-2">
            <ArrowRight className="w-4 h-4 text-primary" />
            <span className="text-sm font-semibold text-foreground">Action Timeline</span>
          </div>
        </div>

        <div className="p-3 space-y-1 max-h-96 overflow-y-auto">
          <AnimatePresence>
            {steps.map((step, idx) => {
              const Icon = statusIcons[step.status] || Circle;
              const statusColor = statusColors[step.status] || "text-muted-foreground/40";
              const isLast = idx === steps.length - 1;

              return (
                <motion.div
                  key={step.id}
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: idx * 0.1 }}
                  className="relative flex items-start gap-3 p-2 rounded-xl hover:bg-muted transition-colors"
                >
                  {!isLast && (
                    <div className="absolute left-[15px] top-8 bottom-0 w-px bg-border" />
                  )}

                  <div className={cn(
                    "w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 bg-muted",
                    step.status === "running" && "bg-primary/10",
                    step.status === "success" && "bg-success/10",
                    step.status === "failed" && "bg-destructive/10",
                  )}>
                    <Icon className={cn(
                      "w-4 h-4",
                      statusColor,
                      step.status === "running" && "animate-spin"
                    )} />
                  </div>

                  <div className="flex-1 min-w-0 pt-1">
                    <p className="text-sm text-foreground truncate" title={step.description}>{step.description}</p>
                    {step.timestamp && (
                      <div className="flex items-center gap-1 mt-0.5">
                        <Clock className="w-3 h-3 text-muted-foreground" />
                        <span className="text-[10px] text-muted-foreground">{step.timestamp}</span>
                      </div>
                    )}
                  </div>
                </motion.div>
              );
            })}
          </AnimatePresence>
        </div>
      </div>
    </motion.div>
  );
}
