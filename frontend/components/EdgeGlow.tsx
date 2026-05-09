"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";

interface EdgeGlowProps {
  active: boolean;
  type?: "thinking" | "executing" | "listening" | "error" | "idle";
}

export const EdgeGlow = ({ active, type = "thinking" }: EdgeGlowProps) => {
  const colors = {
    thinking: "rgba(59, 130, 246, 0.5)", // Blue
    executing: "rgba(34, 197, 94, 0.5)", // Green
    listening: "rgba(239, 68, 68, 0.5)", // Red
    error: "rgba(234, 179, 8, 0.5)",     // Yellow
    idle: "rgba(59, 130, 246, 0.1)",
  };

  const color = colors[type] || colors.thinking;
  return (
    <div className={cn(
      "fixed inset-0 pointer-events-none z-[100] transition-opacity duration-1000",
      active ? "opacity-100" : "opacity-0"
    )}>
      {/* Top Edge */}
      <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-b from-[var(--glow-color)] to-transparent" style={{ "--glow-color": color } as any} />
      {/* Bottom Edge */}
      <div className="absolute bottom-0 left-0 right-0 h-1 bg-gradient-to-t from-[var(--glow-color)] to-transparent" style={{ "--glow-color": color } as any} />
      {/* Left Edge */}
      <div className="absolute top-0 left-0 bottom-0 w-1 bg-gradient-to-r from-[var(--glow-color)] to-transparent" style={{ "--glow-color": color } as any} />
      {/* Right Edge */}
      <div className="absolute top-0 right-0 bottom-0 w-1 bg-gradient-to-l from-[var(--glow-color)] to-transparent" style={{ "--glow-color": color } as any} />

      {/* Pulsing Corners */}
      <motion.div
        animate={{
          boxShadow: active 
            ? [`0 0 20px ${color}`, `0 0 40px ${color}`, `0 0 20px ${color}`] 
            : "none"
        }}
        transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
        className="absolute inset-0 rounded-[2rem] border-2 border-transparent"
      />
    </div>
  );
};
