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
        return "from-[#FF8425] via-[#F86607] to-[#DA4800]";
      case "listening":
        return "from-[#DA4800] via-[#F86607] to-[#FF8425]";
      case "speaking":
        return "from-[#F86607] via-[#FF8425] to-[#E0C090]";
      default:
        return "from-[#F86607] via-[#DA4800] to-[#A07050]";
    }
  };

  const glowColor = state === "listening" ? "rgba(204, 122, 122, 0.3)" : "rgba(248, 102, 7, 0.25)";
  const shadowColor = state === "idle" ? "rgba(248, 102, 7, 0.2)" : "rgba(248, 102, 7, 0.4)";

  return (
    <div className={cn("relative flex items-center justify-center", className)}>
      <motion.div
        animate={{
          scale: state === "idle" ? [1, 1.3, 1] : [1.3, 1.8, 1.3],
          opacity: state === "idle" ? 0.15 : 0.35,
        }}
        transition={{
          duration: 4,
          repeat: Infinity,
          ease: "easeInOut",
        }}
        className="absolute w-48 h-48 md:w-64 md:h-64 rounded-full blur-[60px] md:blur-[80px]"
        style={{ backgroundColor: glowColor }}
      />

      <motion.div
        animate={{
          scale: state === "idle" ? [1, 1.08, 1] : [1.08, 1.2, 1.08],
          rotate: [0, 360],
          boxShadow: state === "idle"
            ? `0 0 40px ${shadowColor}`
            : `0 0 60px ${shadowColor}`,
        }}
        transition={{
          scale: { duration: 4, repeat: Infinity, ease: "easeInOut" },
          rotate: { duration: 30, repeat: Infinity, ease: "linear" },
          boxShadow: { duration: 2, repeat: Infinity, ease: "easeInOut" }
        }}
        className={cn(
          "relative w-24 h-24 md:w-32 md:h-32 rounded-full bg-gradient-to-tr shadow-2xl overflow-hidden",
          getColors(),
          "border border-white/20 backdrop-blur-md"
        )}
      >
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_30%,rgba(255,255,255,0.3),transparent_50%)]" />
        <div className="absolute inset-0 bg-black/5" />
      </motion.div>

      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 10, repeat: Infinity, ease: "linear" }}
        className="absolute w-36 h-36 md:w-48 md:h-48 rounded-full border border-[#FF8425]/10 border-dashed"
      />
      <motion.div
        animate={{ rotate: -360 }}
        transition={{ duration: 20, repeat: Infinity, ease: "linear" }}
        className="absolute w-44 h-44 md:w-56 md:h-56 rounded-full border border-[#F86607]/5"
      />
    </div>
  );
};
