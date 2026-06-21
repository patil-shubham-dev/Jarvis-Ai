"use client";

import { useMemo } from "react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import DOMPurify from "isomorphic-dompurify";

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

function MarkdownContent({ content }: { content: string }) {
  const html = useMemo(() => {
    let result = content
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");

    result = result.replace(/```(\w*)\n?([\s\S]*?)```/g, (_, lang, code) => {
      const langClass = lang ? ` class="language-${lang}"` : "";
      return `<pre class="bg-[#2C2C2C] text-[#E5E0D8] p-3 rounded-xl overflow-x-auto my-2 text-xs leading-relaxed"><code${langClass}>${code.trim()}</code></pre>`;
    });

    result = result.replace(/`([^`]+)`/g, '<code class="bg-[#F0EBE5] text-[#F86607] px-1.5 py-0.5 rounded-md text-xs font-mono">$1</code>');

    result = result.replace(/^### (.+)$/gm, '<h3 class="text-sm font-semibold mt-3 mb-1.5">$1</h3>');
    result = result.replace(/^## (.+)$/gm, '<h2 class="text-base font-semibold mt-4 mb-2">$1</h2>');

    result = result.replace(/^> (.+)$/gm, '<blockquote class="border-l-3 border-[#FF8425] pl-3 italic my-2 opacity-80">$1</blockquote>');

    result = result.replace(/\*\*(.+?)\*\*/g, '<strong class="font-semibold">$1</strong>');
    result = result.replace(/\*(.+?)\*/g, '<em>$1</em>');

    result = result.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1" class="max-w-full rounded-xl my-2" />');
    result = result.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer" class="text-[#F86607] underline">$1</a>');

    result = result.replace(/^\|(.+)\|$/gm, (line: string) => {
      if (line.match(/^\|[ :-]+\|$/)) return "";
      const cells = line.split("|").map(c => c.trim()).filter(Boolean);
      const cellHtml = cells.map(c => `<td class="border border-[#E5E0D8] px-3 py-1.5 text-xs">${c.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/\*(.+?)\*/g, '<em>$1</em>')}</td>`).join("");
      return `<tr>${cellHtml}</tr>`;
    });
    result = result.replace(/(<tr>.*?<\/tr>\n?)+/gs, (match: string) => `<table class="w-full my-2 border-collapse">${match}</table>`);

    result = result.replace(/^- (.+)$/gm, '<li class="ml-4 list-disc text-sm">$1</li>');
    result = result.replace(/^(\d+)\. (.+)$/gm, '<li class="ml-4 list-decimal text-sm">$2</li>');

    result = result.replace(/\n\n/g, '<br/><br/>');
    result = result.replace(/\n/g, '<br/>');

    return result;
  }, [content]);

  return <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(html) }} />;
}

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
          <MarkdownContent content={content} />
        )}
        {isStreaming && (
          <span className="inline-block w-2 h-4 ml-1 bg-[#FF8425] animate-pulse rounded-sm" />
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
