"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";

interface EdgeGlowProps {
  active: boolean;
  type?: "thinking" | "executing" | "listening" | "error" | "idle";
}

export const EdgeGlow = ({ active, type = "thinking" }: EdgeGlowProps) => {
  const colors = {
    thinking: "rgba(248, 102, 7, 0.5)",
    executing: "rgba(125, 170, 125, 0.5)",
    listening: "rgba(204, 122, 122, 0.5)",
    error: "rgba(212, 160, 80, 0.5)",
    idle: "rgba(248, 102, 7, 0.1)",
  };

  const color = colors[type] || colors.thinking;
  return (
    <div className={cn(
      "fixed inset-0 pointer-events-none z-[100] transition-opacity duration-1000",
      active ? "opacity-100" : "opacity-0"
    )}>
      <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-b from-[var(--glow-color)] to-transparent" style={{ "--glow-color": color } as any} />
      <div className="absolute bottom-0 left-0 right-0 h-1 bg-gradient-to-t from-[var(--glow-color)] to-transparent" style={{ "--glow-color": color } as any} />
      <div className="absolute top-0 left-0 bottom-0 w-1 bg-gradient-to-r from-[var(--glow-color)] to-transparent" style={{ "--glow-color": color } as any} />
      <div className="absolute top-0 right-0 bottom-0 w-1 bg-gradient-to-l from-[var(--glow-color)] to-transparent" style={{ "--glow-color": color } as any} />

      <motion.div
        animate={{
          boxShadow: active
            ? [`0 0 20px ${color}`, `0 0 40px ${color}`, `0 0 20px ${color}`]
            : "none"
        }}
        transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
        className="absolute inset-0 rounded-[2rem] border-2 border-transparent"
        style={{ borderColor: active ? color.replace("0.5", "0.15") : "transparent" }}
      />
    </div>
  );
};
