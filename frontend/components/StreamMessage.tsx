"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";
import { AlertCircle, RefreshCw, X, Clock, Copy, Check } from "lucide-react";

interface StreamMessageProps {
  content: string;
  role: "user" | "assistant" | "system";
  timestamp?: string;
  isStreaming?: boolean;
  error?: string | null;
  onRetry?: () => void;
  onDismiss?: () => void;
  streamElapsed?: number;
}

function formatTimestamp(ts: string): string {
  try {
    const d = new Date(ts);
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  } catch {
    return ts;
  }
}

function formatElapsed(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}m ${s}s`;
}

const components: Components = {
  pre: ({ children }) => (
    <pre className="bg-[#2C2C2C] text-[#E5E0D8] p-3 rounded-xl overflow-x-auto my-2 text-xs leading-relaxed">
      {children}
    </pre>
  ),
  code: ({ className, children }) => {
    const isInline = !className;
    if (isInline) {
      return (
        <code className="bg-[#F0EBE5] text-[#F86607] px-1.5 py-0.5 rounded-md text-xs font-mono">
          {children}
        </code>
      );
    }
    return (
      <code className={className}>
        {children}
      </code>
    );
  },
  h2: ({ children }) => (
    <h2 className="text-base font-semibold mt-4 mb-2">{children}</h2>
  ),
  h3: ({ children }) => (
    <h3 className="text-sm font-semibold mt-3 mb-1.5">{children}</h3>
  ),
  blockquote: ({ children }) => (
    <blockquote className="border-l-3 border-[#FF8425] pl-3 italic my-2 opacity-80">
      {children}
    </blockquote>
  ),
  strong: ({ children }) => (
    <strong className="font-semibold">{children}</strong>
  ),
  img: ({ src, alt }) => (
    <img src={src} alt={alt} className="max-w-full rounded-xl my-2" />
  ),
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer" className="text-[#F86607] underline">
      {children}
    </a>
  ),
  table: ({ children }) => (
    <table className="w-full my-2 border-collapse">{children}</table>
  ),
  td: ({ children }) => (
    <td className="border border-[#E5E0D8] px-3 py-1.5 text-xs">{children}</td>
  ),
  th: ({ children }) => (
    <th className="border border-[#E5E0D8] px-3 py-1.5 text-xs font-semibold">{children}</th>
  ),
  li: ({ children }) => <li className="ml-4 text-sm">{children}</li>,
  ol: ({ children }) => <ol className="list-decimal ml-4 my-1">{children}</ol>,
  ul: ({ children }) => <ul className="list-disc ml-4 my-1">{children}</ul>,
  p: ({ children }) => <p className="my-1">{children}</p>,
};

/** Animated typing dots: three bouncing dots */
function TypingDots() {
  return (
    <span className="inline-flex items-center gap-1 ml-1 align-middle">
      {[0, 1, 2].map((i) => (
        <motion.span
          key={i}
          className="w-1.5 h-1.5 rounded-full bg-[#FF8425]"
          animate={{
            y: [0, -4, 0],
            opacity: [0.4, 1, 0.4],
          }}
          transition={{
            duration: 0.8,
            repeat: Infinity,
            delay: i * 0.15,
            ease: "easeInOut",
          }}
        />
      ))}
    </span>
  );
}

export function StreamMessage({
  content,
  role,
  timestamp,
  isStreaming,
  error,
  onRetry,
  onDismiss,
  streamElapsed,
}: StreamMessageProps) {
  const [copied, setCopied] = useState(false);
  const [showActions, setShowActions] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {}
  };

  // Error state
  if (error) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 10, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        className="flex flex-col max-w-[90%] mr-auto items-start"
      >
        <div className="p-4 rounded-2xl text-sm leading-relaxed bg-destructive/5 border border-destructive/20 rounded-tl-none w-full">
          <div className="flex items-start gap-3">
            <div className="w-8 h-8 rounded-xl bg-destructive/10 flex items-center justify-center flex-shrink-0">
              <AlertCircle className="w-4 h-4 text-destructive" />
            </div>
            <div className="min-w-0 flex-1">
              <span className="text-xs font-semibold text-destructive block mb-1">Error</span>
              <p className="text-xs text-destructive/80">{error}</p>
              <div className="flex items-center gap-2 mt-3">
                {onRetry && (
                  <button
                    onClick={onRetry}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-destructive/10 hover:bg-destructive/20 text-destructive text-[11px] font-medium transition-colors"
                  >
                    <RefreshCw className="w-3 h-3" />
                    Retry
                  </button>
                )}
                {onDismiss && (
                  <button
                    onClick={onDismiss}
                    className="px-3 py-1.5 rounded-xl hover:bg-muted text-muted-foreground text-[11px] transition-colors"
                  >
                    Dismiss
                  </button>
                )}
              </div>
            </div>
            {onDismiss && (
              <button onClick={onDismiss} className="p-1 rounded-lg hover:bg-destructive/10 transition-colors flex-shrink-0">
                <X className="w-3.5 h-3.5 text-destructive/60" />
              </button>
            )}
          </div>
        </div>
      </motion.div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className={cn(
        "flex flex-col max-w-[85%]",
        role === "user" ? "ml-auto items-end" : "mr-auto items-start"
      )}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => setShowActions(false)}
    >
      <div
        className={cn(
          "p-4 rounded-2xl text-sm leading-relaxed relative group",
          role === "user"
            ? "bg-[#F86607] text-white rounded-tr-none shadow-sm"
            : role === "system"
            ? "bg-[#F5F2EC] text-[#6B6560] border border-[#E5E0D8] rounded-tl-none"
            : "bg-white text-[#2C2C2C] border border-[#E5E0D8] rounded-tl-none shadow-sm"
        )}
      >
        {role === "user" ? (
          <p>{content}</p>
        ) : (
          <ReactMarkdown components={components} remarkPlugins={[remarkGfm]}>
            {content || (isStreaming ? "" : "")}
          </ReactMarkdown>
        )}

        {/* Copy button — appears on hover */}
        {role === "assistant" && !isStreaming && content && (
          <AnimatePresence>
            {showActions && (
              <motion.button
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.8 }}
                onClick={handleCopy}
                className="absolute -top-2 -right-2 p-1.5 rounded-lg bg-card border border-border shadow-sm hover:bg-muted transition-colors"
                title="Copy message"
              >
                {copied ? (
                  <Check className="w-3.5 h-3.5 text-success" />
                ) : (
                  <Copy className="w-3.5 h-3.5 text-muted-foreground" />
                )}
              </motion.button>
            )}
          </AnimatePresence>
        )}

        {/* Streaming indicator — animated typing dots */}
        {isStreaming && (
          <span className="inline-flex items-center gap-1">
            <TypingDots />
          </span>
        )}
      </div>

      {/* Footer: timestamp + streaming info */}
      <div className="flex items-center gap-2 mt-1.5">
        {timestamp && (
          <span className="text-[10px] uppercase tracking-tighter text-[#9A9490]">
            {role === "user" ? "You" : role === "system" ? "System" : "Jarvis"}
            {" · "}
            {formatTimestamp(timestamp)}
          </span>
        )}

        {/* Streaming elapsed time */}
        {isStreaming && streamElapsed !== undefined && streamElapsed > 2 && (
          <motion.span
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="flex items-center gap-1 text-[10px] text-primary/60"
          >
            <Clock className="w-3 h-3" />
            {formatElapsed(streamElapsed)}
          </motion.span>
        )}
      </div>
    </motion.div>
  );
}
