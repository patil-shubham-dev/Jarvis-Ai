"use client";

import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { Brain, Search, Clock, Star, Database, RefreshCw, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";

interface MemoryEntry {
  id: string;
  text: string;
  timestamp: string;
  category: string;
  importance: number;
  module: string;
}

const categories = ["all", "conversation", "action", "preference", "task", "meeting"];

export default function MemoryPage() {
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetch(`/api/memories${searchQuery ? `?query=${encodeURIComponent(searchQuery)}` : ""}`)
      .then((r) => r.json())
      .then((data) => {
        setMemories((data.memories || []).map((m: any) => ({
          ...m,
          importance: m.importance ?? 0.5,
          module: m.module || "chat",
        })));
      })
      .catch((err) => {
        console.error("Failed to fetch memories:", err);
        setMemories([]);
      })
      .finally(() => setLoading(false));
  }, [searchQuery]);

  const filtered = memories.filter((m) => {
    const matchesCategory = selectedCategory === "all" || m.category === selectedCategory;
    return matchesCategory;
  });

  const categoryColors: Record<string, string> = {
    conversation: "bg-[#F86607]/20 text-[#F86607]",
    action: "bg-[#7DAA7D]/20 text-[#7DAA7D]",
    preference: "bg-[#FF8425]/20 text-[#FF8425]",
    task: "bg-[#D4A050]/20 text-[#D4A050]",
    meeting: "bg-[#CC7A7A]/20 text-[#CC7A7A]",
  };

  return (
    <div className="min-h-screen bg-background p-6 pb-24">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
              <Brain className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-foreground">Memory Explorer</h1>
              <p className="text-sm text-muted-foreground">Browse Jarvis&apos;s persistent memory</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => setSearchQuery("")} className="p-2 rounded-xl hover:bg-muted transition-colors" title="Refresh">
              <RefreshCw className="w-4 h-4 text-muted-foreground" />
            </button>
            <button onClick={() => { setMemories([]); setSearchQuery(""); }} className="p-2 rounded-xl hover:bg-muted transition-colors" title="Clear">
              <Trash2 className="w-4 h-4 text-muted-foreground" />
            </button>
          </div>
        </div>

        <div className="relative mb-6">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search all memories..."
            className="w-full pl-12 pr-4 py-3 bg-card rounded-2xl border border-border outline-none focus:border-primary focus:bg-card transition-all text-sm text-foreground placeholder:text-muted-foreground/60"
          />
        </div>

        <div className="flex items-center gap-2 mb-6 overflow-x-auto pb-2">
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={cn(
                "px-3 py-1.5 rounded-lg text-xs font-medium transition-all whitespace-nowrap",
                selectedCategory === cat
                  ? "bg-foreground text-background"
                  : "bg-muted text-muted-foreground hover:bg-muted/80"
              )}
            >
              {cat === "all" ? "All Categories" : cat.charAt(0).toUpperCase() + cat.slice(1)}
            </button>
          ))}
        </div>

        <div className="space-y-3">
          {filtered.map((memory, idx) => (
            <motion.div
              key={memory.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.03 }}
              className="p-4 rounded-2xl bg-card border border-border hover:border-[#FF8425]/30 hover:shadow-sm transition-all cursor-pointer"
            >
              <div className="flex items-start gap-4">
                <div className={cn(
                  "w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0",
                  categoryColors[memory.category] || "bg-muted text-muted-foreground"
                )}>
                  <Brain className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-foreground">{memory.text}</p>
                  <div className="flex items-center gap-3 mt-2">
                    <div className="flex items-center gap-1">
                      <Clock className="w-3 h-3 text-muted-foreground" />
                      <span className="text-[10px] text-muted-foreground">{memory.timestamp}</span>
                    </div>
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-muted text-muted-foreground">
                      {memory.module}
                    </span>
                    <div className="flex items-center gap-0.5">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star
                          key={i}
                          className={cn(
                            "w-2.5 h-2.5",
                            i < Math.round(memory.importance * 5)
                              ? "fill-[#D4A050] text-[#D4A050]"
                              : "text-border"
                          )}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>
          ))}

          {loading && (
            <div className="text-center py-12">
              <RefreshCw className="w-12 h-12 text-muted-foreground/20 mx-auto mb-4 animate-spin" />
              <p className="text-muted-foreground text-sm">Loading memories...</p>
            </div>
          )}
          {!loading && filtered.length === 0 && (
            <div className="text-center py-12">
              <Database className="w-12 h-12 text-muted-foreground/20 mx-auto mb-4" />
              <p className="text-muted-foreground text-sm">No memories found</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
