"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";

interface StreamMessageProps {
  content: string;
  role: "user" | "assistant" | "system";
  timestamp?: string;
  isStreaming?: boolean;
}

function formatTimestamp(ts: string): string {
  try {
    const d = new Date(ts);
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  } catch {
    return ts;
  }
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

export function StreamMessage({ content, role, timestamp, isStreaming }: StreamMessageProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className={cn(
        "flex flex-col max-w-[85%]",
        role === "user" ? "ml-auto items-end" : "mr-auto items-start"
      )}
    >
      <div
        className={cn(
          "p-4 rounded-2xl text-sm leading-relaxed",
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
            {content}
          </ReactMarkdown>
        )}
        {isStreaming && (
          <span className="inline-block w-2 h-4 ml-1 bg-[#FF8425] animate-pulse rounded-[2px]" />
        )}
      </div>
      {timestamp && (
        <span className="text-[10px] uppercase tracking-tighter text-[#9A9490] mt-1.5">
          {role === "user" ? "You" : role === "system" ? "System" : "Jarvis"}
          {" · "}
          {formatTimestamp(timestamp)}
        </span>
      )}
    </motion.div>
  );
}
