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

  const reducedMotion = typeof window !== "undefined"
    ? window.matchMedia("(prefers-reduced-motion: reduce)").matches
    : false;

  return (
    <div className={cn("relative flex items-center justify-center", className)}>
      {!reducedMotion && (
        <motion.div
          animate={{
            scale: state === "idle" ? [1, 1.3, 1] : [1.3, 1.8, 1.3],
            opacity: state === "idle" ? 0.15 : 0.35,
          }}
          transition={{
            duration: 4,
            repeat: Infinity,
            ease: [0.4, 0, 0.2, 1],
          }}
          className="absolute w-48 h-48 md:w-64 md:h-64 rounded-full blur-[40px] md:blur-[60px]"
          style={{ backgroundColor: glowColor }}
        />
      )}

      <motion.div
        animate={{
          scale: state === "idle" ? (reducedMotion ? 1 : [1, 1.08, 1]) : (reducedMotion ? 1 : [1.08, 1.2, 1.08]),
          rotate: reducedMotion ? 0 : 360,
        }}
        transition={{
          scale: reducedMotion ? {} : { duration: 4, repeat: Infinity, ease: [0.4, 0, 0.2, 1] },
          rotate: reducedMotion ? {} : { duration: 30, repeat: Infinity, ease: "linear" },
        }}
        className={cn(
          "relative w-24 h-24 md:w-32 md:h-32 rounded-full bg-gradient-to-tr shadow-2xl overflow-hidden will-change-transform",
          getColors(),
          "border border-white/20",
          state === "idle" ? "shadow-[0_0_40px_var(--orb-shadow)]" : "shadow-[0_0_60px_var(--orb-shadow)]"
        )}
        style={{ "--orb-shadow": shadowColor } as React.CSSProperties}
      >
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_30%,rgba(255,255,255,0.3),transparent_50%)]" />
        <div className="absolute inset-0 bg-black/5" />
      </motion.div>

      {!reducedMotion && (
        <>
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
        </>
      )}
    </div>
  );
};
