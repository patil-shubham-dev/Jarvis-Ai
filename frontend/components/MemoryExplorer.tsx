"use client";

import { useState, useEffect, useRef, useCallback, startTransition } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Brain, Search, Clock, Star, Database, Loader, AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils";

interface Memory {
  id: string;
  text: string;
  timestamp: string;
  category: string;
  importance: number;
}

export function MemoryExplorer() {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [memories, setMemories] = useState<Memory[]>([]);
  const [loadingMemories, setLoadingMemories] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const filtered = memories.filter(m =>
    m.text.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const loadMemories = useCallback(() => {
    setLoadingMemories(true);
    setLoadError(false);
    fetch(`/api/memories?limit=10`)
      .then((r) => r.json())
      .then((data) => {
        setMemories((data.memories || []).map((m: { id: string; text: string; timestamp: string; category: string; importance: number }) => ({
          id: m.id,
          text: m.text,
          timestamp: m.timestamp ? new Date(m.timestamp).toLocaleString() : "recent",
          category: m.category || "conversation",
          importance: m.importance ?? 0.5,
        })));
      })
      .catch(() => { setLoadError(true); })
      .finally(() => setLoadingMemories(false));
  }, []);

  useEffect(() => {
    if (!isOpen || memories.length > 0) return;
    startTransition(() => { loadMemories(); });
  }, [isOpen, loadMemories, memories.length]);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => searchInputRef.current?.focus(), 100);
    }
  }, [isOpen]);

  const close = useCallback(() => {
    setIsOpen(false);
    setTimeout(() => triggerRef.current?.focus(), 100);
  }, []);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === "Escape") close();
  }, [close]);

  return (
    <div className="fixed left-4 top-20 z-50">
      <motion.button
        ref={triggerRef}
        whileHover={{ scale: 1.05 }}
        onClick={() => setIsOpen(!isOpen)}
        className="p-3 rounded-xl bg-card/90 backdrop-blur-md border border-border shadow-lg"
        aria-label={isOpen ? "Close memory explorer" : "Open memory explorer"}
        aria-expanded={isOpen}
      >
        <Database className="w-5 h-5 text-primary" />
      </motion.button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, x: -20, scale: 0.95 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: -20, scale: 0.95 }}
            onKeyDown={handleKeyDown}
            className="absolute left-14 top-0 w-80 bg-card/95 backdrop-blur-lg border border-border rounded-2xl shadow-2xl overflow-hidden"
            role="dialog"
            aria-label="Memory explorer"
          >
            <div className="p-4 border-b border-border">
              <div className="flex items-center gap-2 mb-3">
                <Brain className="w-4 h-4 text-primary" />
                <span className="text-sm font-semibold text-foreground">Memory Explorer</span>
              </div>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <input
                  ref={searchInputRef}
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search memories..."
                  className="w-full pl-9 pr-3 py-2 text-sm bg-muted rounded-xl border border-border outline-none focus:border-primary transition-colors text-foreground placeholder:text-muted-foreground/60"
                />
              </div>
            </div>

            <div className="max-h-80 overflow-y-auto p-2">
              {loadingMemories && (
                <div className="flex items-center justify-center py-6">
                  <Loader className="w-5 h-5 text-muted-foreground animate-spin" />
                </div>
              )}
              {!loadingMemories && loadError && (
                <div className="text-center py-6">
                  <AlertCircle className="w-6 h-6 text-destructive mx-auto mb-2" />
                  <p className="text-xs text-muted-foreground mb-2">Failed to load memories</p>
                  <button onClick={loadMemories} className="text-xs text-primary underline">Retry</button>
                </div>
              )}
              {!loadingMemories && !loadError && filtered.length === 0 && (
                <div className="text-center py-6">
                  <p className="text-xs text-muted-foreground">{searchQuery ? "No matching memories" : "No memories yet"}</p>
                </div>
              )}
              {filtered.map((memory, idx) => (
                <motion.div
                  key={memory.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  className="p-3 rounded-xl hover:bg-muted transition-colors cursor-pointer"
                >
                  <div className="flex items-start gap-3">
                    <div className={cn(
                      "w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0",
                      memory.category === "action" ? "bg-[#7DAA7D]/20" :
                      memory.category === "preference" ? "bg-[#FF8425]/20" : "bg-[#F86607]/20"
                    )}>
                      <Clock className={cn(
                        "w-4 h-4",
                        memory.category === "action" ? "text-success" :
                        memory.category === "preference" ? "text-accent" : "text-primary"
                      )} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-foreground truncate">{memory.text}</p>
                      <div className="flex items-center gap-2 mt-1">
                        <span className="text-[10px] text-muted-foreground">{memory.timestamp}</span>
                        <div className="flex items-center gap-0.5">
                          {Array.from({ length: Math.round(memory.importance * 5) }).map((_, i) => (
                            <Star key={i} className="w-2.5 h-2.5 fill-[#D4A050] text-[#D4A050]" />
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                </motion.div>
              ))}
            </div>

            <div className="p-3 border-t border-border flex justify-between text-[10px] text-muted-foreground uppercase tracking-wider">
              <span>{filtered.length} memories</span>
              <span>api: backend</span>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
