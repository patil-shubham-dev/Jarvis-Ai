"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Key, Eye, EyeOff, Search, ChevronDown, Sparkles,
  RefreshCw, CheckCircle, AlertCircle, Zap, XCircle,
  Loader, Wifi, WifiOff,
} from "lucide-react";
import { cn } from "@/lib/utils";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

interface ModelInfo { id: string; name: string; provider: string; }
interface ProviderInfo { id: string; name: string; }

type ConnectionStatus = "disconnected" | "connecting" | "connected" | "error";
type TestStatus = "idle" | "testing" | "success" | "error";

const PROVIDER_META: Record<string, { icon: string; ring: string; badge: string; label: string }> = {
  openai:    { icon: "⚡", ring: "ring-emerald-500/30", badge: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400", label: "OpenAI" },
  anthropic: { icon: "🟣", ring: "ring-purple-500/30", badge: "bg-purple-500/10 text-purple-600 dark:text-purple-400", label: "Anthropic" },
  google:    { icon: "🔵", ring: "ring-blue-500/30", badge: "bg-blue-500/10 text-blue-600 dark:text-blue-400", label: "Google Gemini" },
  groq:      { icon: "🟢", ring: "ring-green-500/30", badge: "bg-green-500/10 text-green-600 dark:text-green-400", label: "Groq" },
  mistral:   { icon: "💨", ring: "ring-orange-500/30", badge: "bg-orange-500/10 text-orange-600 dark:text-orange-400", label: "Mistral" },
  openrouter:{ icon: "🔄", ring: "ring-indigo-500/30", badge: "bg-indigo-500/10 text-indigo-600 dark:text-indigo-400", label: "OpenRouter" },
  deepseek:  { icon: "🐋", ring: "ring-cyan-500/30", badge: "bg-cyan-500/10 text-cyan-600 dark:text-cyan-400", label: "DeepSeek" },
  nvidia:    { icon: "🟩", ring: "ring-green-600/30", badge: "bg-green-600/10 text-green-700 dark:text-green-400", label: "NVIDIA" },
};

function autoSaveCreds(key: string, model: string, providerId: string) {
  try {
    sessionStorage.setItem("jarvis_api_key", key);
    sessionStorage.setItem("jarvis_model", model);
    sessionStorage.setItem("jarvis_provider", providerId);
  } catch {}
}

export function AIProviderCard({
  apiKey,
  detectedProvider,
  selectedModel,
  models,
  onApiKeyChange,
  onModelChange,
  onModelsFetched,
}: {
  apiKey: string;
  detectedProvider: ProviderInfo | null;
  selectedModel: string;
  models: ModelInfo[];
  onApiKeyChange: (key: string) => void;
  onModelChange: (model: string) => void;
  onModelsFetched?: (models: ModelInfo[]) => void;
}) {
  const [showKey, setShowKey] = useState(false);
  const [detecting, setDetecting] = useState(false);
  const [detectError, setDetectError] = useState("");
  const [loadingModels, setLoadingModels] = useState(false);
  const [modelSearch, setModelSearch] = useState("");
  const [showModelDropdown, setShowModelDropdown] = useState(false);
  const [testStatus, setTestStatus] = useState<TestStatus>("idle");
  const [testMessage, setTestMessage] = useState("");
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("disconnected");
  const [localModels, setLocalModels] = useState<ModelInfo[]>([]);
  const modelDropdownRef = useRef<HTMLDivElement>(null);
  const detectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Merge external models prop with local models — external takes priority
  const effectiveModels = models.length > 0 ? models : localModels;

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (modelDropdownRef.current && !modelDropdownRef.current.contains(e.target as Node)) {
        setShowModelDropdown(false);
      }
    };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  useEffect(() => {
    checkConnection();
  }, []);

  const checkConnection = async () => {
    setConnectionStatus("connecting");
    try {
      const resp = await fetch(`${API_BASE}/api/health`);
      if (resp.ok) setConnectionStatus("connected");
      else setConnectionStatus("error");
    } catch {
      setConnectionStatus("error");
    }
  };

  const fetchModels = useCallback(async (key: string, provider: string) => {
    setLoadingModels(true);
    try {
      const resp = await fetch(`${API_BASE}/api/providers/models`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ api_key: key, provider }),
      });
      if (!resp.ok) throw new Error(`Server error (${resp.status})`);
      const data = await resp.json();
      const fetched: ModelInfo[] = data.models || [];
      // Store locally so the UI always has models even if parent doesn't track them
      setLocalModels(fetched);
      if (onModelsFetched) onModelsFetched(fetched);
      if (fetched.length > 0) {
        onModelChange(fetched[0].id);
      }
      return fetched;
    } catch (e) {
      return [];
    } finally {
      setLoadingModels(false);
    }
  }, [onModelChange, onModelsFetched]);

  const detectProvider = useCallback(async (key: string) => {
    setDetectError("");
    if (!key || key.length < 10) {
      onApiKeyChange(key);
      return;
    }
    setDetecting(true);
    try {
      const resp = await fetch(`${API_BASE}/api/providers/detect`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ api_key: key }),
      });
      if (!resp.ok) throw new Error(`Server error (${resp.status})`);
      const data = await resp.json();
      if (data.provider) {
        onApiKeyChange(key);
        const fetched = await fetchModels(key, data.provider);
        // Auto-save on detection so chat can immediately use the key
        const firstModel = fetched.length > 0 ? fetched[0].id : selectedModel;
        autoSaveCreds(key, firstModel, data.provider);
      } else {
        setDetectError(data.error || "Provider not recognized");
      }
    } catch (e) {
      setDetectError(e instanceof Error ? e.message : "Connection failed — is the backend running?");
    } finally {
      setDetecting(false);
    }
  }, [fetchModels, onApiKeyChange, selectedModel]);

  const handleKeyChange = (val: string) => {
    if (detectTimerRef.current !== null) clearTimeout(detectTimerRef.current);
    setDetectError("");
    setTestStatus("idle");
    onApiKeyChange(val);
    if (val.length > 10) {
      detectTimerRef.current = setTimeout(() => detectProvider(val), 400);
    }
  };

  const testConnection = async () => {
    if (!apiKey || !selectedModel) {
      // If no model selected yet, use provider default
      if (!selectedModel && effectiveModels.length > 0) {
        onModelChange(effectiveModels[0].id);
      }
      if (!apiKey) {
        setTestStatus("error");
        setTestMessage("Please enter an API key first");
        return;
      }
    }
    setTestStatus("testing");
    setTestMessage("");
    try {
      const resp = await fetch(`${API_BASE}/api/proxy/test`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          api_key: apiKey,
        }),
      });
      const text = await resp.text();
      let data;
      try { data = JSON.parse(text); } catch { throw new Error(`Server returned ${resp.status}: ${text.slice(0, 100)}`); }
      if (!resp.ok) {
        throw new Error(data.error || data.detail || `Server error (${resp.status})`);
      }
      if (data.success === false) {
        setTestStatus("error");
        setTestMessage(data.error || "Connection failed");
      } else {
        // Auto-save on success — key is verified
        autoSaveCreds(apiKey, selectedModel, detectedProvider?.id || data.provider || "");
        const model = data.model || selectedModel;
        if (model && model !== selectedModel) onModelChange(model);
        setTestStatus("success");
        setTestMessage(data.response || "Connected successfully");
      }
    } catch (e) {
      setTestStatus("error");
      setTestMessage(e instanceof Error ? e.message : "Request failed");
    }
  };

  const providerMeta = detectedProvider ? PROVIDER_META[detectedProvider.id] : null;
  const filteredModels = effectiveModels.filter(
    (m) => m.id.toLowerCase().includes(modelSearch.toLowerCase()) ||
          m.name.toLowerCase().includes(modelSearch.toLowerCase())
  );

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between mb-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-foreground">Backend Server</span>
          <button onClick={checkConnection} className="p-0.5 rounded hover:bg-muted transition-colors">
            <RefreshCw className="w-3 h-3 text-muted-foreground" />
          </button>
        </div>
        <div className="flex items-center gap-1.5">
          <span className={cn(
            "w-2 h-2 rounded-full transition-colors",
            connectionStatus === "connected" ? "bg-success animate-pulse" :
            connectionStatus === "connecting" ? "bg-warning animate-pulse" :
            connectionStatus === "error" ? "bg-destructive" : "bg-muted-foreground/30"
          )} />
          <span className="text-[10px] text-muted-foreground">
            {connectionStatus === "connected" ? "Connected" :
             connectionStatus === "connecting" ? "Checking..." :
             connectionStatus === "error" ? "Offline" : "Unknown"}
          </span>
          {connectionStatus === "connected" && <Wifi className="w-3 h-3 text-success" />}
          {connectionStatus === "error" && <WifiOff className="w-3 h-3 text-destructive" />}
        </div>
      </div>

      <div className={cn(
        "rounded-2xl border transition-all duration-300",
        "bg-card",
        connectionStatus === "error" ? "border-destructive/30" : "border-border",
        detectedProvider && providerMeta && `ring-1 ${providerMeta.ring}`
      )}>
        <div className="p-5">
          <div className="flex items-center justify-between mb-4">
            <label className="text-sm font-medium text-foreground flex items-center gap-2">
              <Key className="w-4 h-4 text-muted-foreground" />
              API Key
            </label>
            {detectedProvider && providerMeta && (
              <motion.span
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                className={cn("px-2.5 py-1 rounded-lg text-[10px] font-semibold flex items-center gap-1.5", providerMeta.badge)}
              >
                <span>{providerMeta.icon}</span>
                {providerMeta.label}
                {effectiveModels.length > 0 && <span className="opacity-60">· {effectiveModels.length}</span>}
              </motion.span>
            )}
          </div>

          <div className="relative">
            <input
              type={showKey ? "text" : "password"}
              value={apiKey}
              onChange={(e) => handleKeyChange(e.target.value)}
              placeholder={
                connectionStatus === "error"
                  ? "Backend offline — start the server first"
                  : "Paste your API key (sk-..., sk-ant-..., AIza...)"
              }
              disabled={connectionStatus === "error"}
              className={cn(
                "w-full pr-20 pl-4 py-3 text-sm rounded-xl border outline-none transition-colors font-mono",
                connectionStatus === "error"
                  ? "bg-destructive/5 border-destructive/20 text-destructive/60 cursor-not-allowed"
                  : "bg-muted border-border focus:border-primary placeholder:text-muted-foreground/40"
              )}
            />
            <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1">
              {detecting ? (
                <RefreshCw className="w-4 h-4 text-primary animate-spin" />
              ) : (
                <button onClick={() => setShowKey(!showKey)} className="p-1.5 rounded-lg hover:bg-muted transition-colors">
                  {showKey ? <EyeOff className="w-4 h-4 text-muted-foreground" /> : <Eye className="w-4 h-4 text-muted-foreground" />}
                </button>
              )}
            </div>
          </div>

          <AnimatePresence>
            {detectError && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                exit={{ opacity: 0, height: 0 }}
                className="flex items-start gap-2 mt-3 text-xs text-destructive overflow-hidden"
              >
                <AlertCircle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                <span>{detectError}</span>
              </motion.div>
            )}

            {detectedProvider && !detecting && !detectError && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                exit={{ opacity: 0, height: 0 }}
                className="overflow-hidden"
              >
                {loadingModels ? (
                  <div className="flex items-center gap-2 mt-3 text-xs text-muted-foreground">
                    <RefreshCw className="w-3 h-3 animate-spin" />
                    Loading models...
                  </div>
                ) : (
                  <div className="mt-3 space-y-3">
                    {/* Model selector — always shown when provider is detected */}
                    <div className="relative" ref={modelDropdownRef}>
                      <button
                        onClick={() => setShowModelDropdown(!showModelDropdown)}
                        className="w-full flex items-center justify-between px-4 py-2.5 text-sm bg-muted rounded-xl border border-border outline-none focus:border-primary transition-colors text-left"
                      >
                        <div className="flex items-center gap-2 min-w-0 flex-1">
                          <Sparkles className="w-4 h-4 text-primary flex-shrink-0" />
                          <span className="truncate">
                            {effectiveModels.find(m => m.id === selectedModel)?.name || selectedModel || "Select a model"}
                          </span>
                          {selectedModel && (
                            <span className="text-[10px] text-muted-foreground font-mono truncate hidden sm:inline">({selectedModel})</span>
                          )}
                        </div>
                        <ChevronDown className={cn("w-4 h-4 text-muted-foreground transition-transform flex-shrink-0 ml-2", showModelDropdown && "rotate-180")} />
                      </button>

                      <AnimatePresence>
                        {showModelDropdown && (
                          <motion.div
                            initial={{ opacity: 0, y: -4, scale: 0.98 }}
                            animate={{ opacity: 1, y: 0, scale: 1 }}
                            exit={{ opacity: 0, y: -4, scale: 0.98 }}
                            className="absolute z-50 mt-1 w-full bg-card border border-border rounded-xl shadow-2xl overflow-hidden"
                            style={{ maxHeight: "min(24rem, calc(100vh - 300px))" }}
                          >
                            <div className="p-2 border-b border-border">
                              <div className="relative">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                  type="text"
                                  value={modelSearch}
                                  onChange={(e) => setModelSearch(e.target.value)}
                                  placeholder="Search models..."
                                  className="w-full pl-9 pr-3 py-2 text-sm bg-muted rounded-lg border border-border outline-none focus:border-primary transition-colors"
                                  autoFocus
                                />
                              </div>
                            </div>
                            <div className="overflow-y-auto" style={{ maxHeight: "16rem" }}>
                              {filteredModels.length === 0 ? (
                                <div className="p-4 text-center text-xs text-muted-foreground">
                                  {modelSearch ? "No matching models" : effectiveModels.length === 0 ? "No models loaded — try refreshing" : "No models loaded"}
                                </div>
                              ) : (
                                filteredModels.map((model) => (
                                  <button
                                    key={model.id}
                                    onClick={() => { onModelChange(model.id); setShowModelDropdown(false); setModelSearch(""); }}
                                    className={cn(
                                      "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors text-left",
                                      selectedModel === model.id
                                        ? "bg-primary/10 text-primary"
                                        : "hover:bg-muted text-foreground"
                                    )}
                                  >
                                    <Sparkles className={cn("w-4 h-4 flex-shrink-0", selectedModel === model.id ? "text-primary" : "text-muted-foreground")} />
                                    <div className="min-w-0 flex-1">
                                      <div className="truncate font-medium">{model.name}</div>
                                      <div className="truncate text-[10px] text-muted-foreground font-mono">{model.id}</div>
                                    </div>
                                    {selectedModel === model.id && <CheckCircle className="w-4 h-4 text-primary flex-shrink-0" />}
                                  </button>
                                ))
                              )}
                            </div>
                            <div className="p-2 border-t border-border flex justify-between text-[10px] text-muted-foreground px-3">
                              <span>{effectiveModels.length} models</span>
                              <button
                                onClick={() => { if (detectedProvider) fetchModels(apiKey, detectedProvider.id); }}
                                className="hover:text-foreground transition-colors flex items-center gap-1"
                              >
                                <RefreshCw className="w-3 h-3" /> Refresh
                              </button>
                            </div>
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </div>

                    {/* Test connection button — always shown when provider is detected */}
                    <button
                      onClick={testConnection}
                      disabled={testStatus === "testing"}
                      className={cn(
                        "w-full flex items-center justify-between px-4 py-3 rounded-xl text-sm transition-all border",
                        testStatus === "success" ? "bg-success/5 border-success/20" :
                        testStatus === "error" ? "bg-destructive/5 border-destructive/20" :
                        "bg-muted/50 border-border hover:border-primary/30 hover:bg-muted"
                      )}
                    >
                      <div className="flex items-center gap-2.5">
                        {testStatus === "testing" ? (
                          <Loader className="w-4 h-4 text-primary animate-spin" />
                        ) : testStatus === "success" ? (
                          <CheckCircle className="w-4 h-4 text-success" />
                        ) : testStatus === "error" ? (
                          <XCircle className="w-4 h-4 text-destructive" />
                        ) : (
                          <Zap className="w-4 h-4 text-muted-foreground" />
                        )}
                        <span className={cn(
                          "text-xs font-medium",
                          testStatus === "success" ? "text-success" :
                          testStatus === "error" ? "text-destructive" :
                          "text-foreground"
                        )}>
                          {testStatus === "testing" ? "Testing connection..." :
                           testStatus === "success" ? "Connection successful" :
                           testStatus === "error" ? "Connection failed" :
                           "Test connection"}
                        </span>
                      </div>
                      {testStatus === "idle" && <Zap className="w-3.5 h-3.5 text-muted-foreground" />}
                    </button>

                    <AnimatePresence>
                      {testMessage && testStatus !== "idle" && (
                        <motion.div
                          initial={{ opacity: 0, y: -8 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: -8 }}
                          className={cn(
                            "p-3 rounded-xl text-xs leading-relaxed border max-h-32 overflow-y-auto",
                            testStatus === "success"
                              ? "bg-success/5 border-success/20 text-foreground/80"
                              : "bg-destructive/5 border-destructive/20 text-destructive"
                          )}
                        >
                          <span className={cn(
                            "font-medium block mb-1",
                            testStatus === "success" ? "text-success" : "text-destructive"
                          )}>
                            {testStatus === "success" ? "Response:" : "Error:"}
                          </span>
                          {testMessage}
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}

export function ProviderBadge({ providerId, size = "sm" }: { providerId: string; size?: "sm" | "md" | "lg" }) {
  const meta = PROVIDER_META[providerId];
  if (!meta) return null;
  const sizeClass = size === "lg" ? "px-3 py-1.5 text-sm" : size === "md" ? "px-2.5 py-1 text-xs" : "px-2 py-0.5 text-[10px]";
  return (
    <span className={cn("rounded-lg font-medium flex items-center gap-1.5", sizeClass, meta.badge)}>
      <span>{meta.icon}</span>
      {meta.label}
    </span>
  );
}