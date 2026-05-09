"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";

interface OrbProps {
  state?: "idle" | "thinking" | "listening" | "speaking";
  className?: string;
}

export const Orb = ({ state = "idle", className }: OrbProps) => {
  const getColors = () => {
    switch (state) {
      case "thinking":
        return "from-blue-400 via-indigo-500 to-purple-600";
      case "listening":
        return "from-red-400 via-rose-500 to-pink-600";
      case "speaking":
        return "from-cyan-400 via-blue-500 to-indigo-600";
      default:
        return "from-blue-600 via-blue-700 to-indigo-900";
    }
  };

  return (
    <div className={cn("relative flex items-center justify-center", className)}>
      {/* Dynamic Ambient Glow */}
      <motion.div
        animate={{
          scale: state === "idle" ? [1, 1.3, 1] : [1.3, 1.8, 1.3],
          opacity: state === "idle" ? 0.2 : 0.4,
        }}
        transition={{
          duration: 4,
          repeat: Infinity,
          ease: "easeInOut",
        }}
        className={cn(
          "absolute w-48 h-48 md:w-64 md:h-64 rounded-full blur-[60px] md:blur-[80px]",
          state === "listening" ? "bg-red-500/30" : "bg-blue-500/20"
        )}
      />

      {/* Main Intelligent Core */}
      <motion.div
        animate={{
          scale: state === "idle" ? [1, 1.08, 1] : [1.08, 1.2, 1.08],
          rotate: [0, 360],
          boxShadow: state === "idle" 
            ? "0 0 40px rgba(37, 99, 235, 0.2)" 
            : "0 0 60px rgba(37, 99, 235, 0.4)",
        }}
        transition={{
          scale: { duration: 4, repeat: Infinity, ease: "easeInOut" },
          rotate: { duration: 30, repeat: Infinity, ease: "linear" },
          boxShadow: { duration: 2, repeat: Infinity, ease: "easeInOut" }
        }}
        className={cn(
          "relative w-24 h-24 md:w-32 md:h-32 rounded-full bg-gradient-to-tr shadow-2xl overflow-hidden",
          getColors(),
          "border border-white/30 backdrop-blur-md"
        )}
      >
        {/* Procedural Glass Effect */}
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_30%,rgba(255,255,255,0.4),transparent_50%)]" />
        <div className="absolute inset-0 bg-black/10" />
      </motion.div>

      {/* Rotating Mechanical Rings */}
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 10, repeat: Infinity, ease: "linear" }}
        className="absolute w-36 h-36 md:w-48 md:h-48 rounded-full border border-blue-400/10 border-dashed"
      />
      <motion.div
        animate={{ rotate: -360 }}
        transition={{ duration: 20, repeat: Infinity, ease: "linear" }}
        className="absolute w-44 h-44 md:w-56 md:h-56 rounded-full border border-white/5"
      />
    </div>
  );
};
