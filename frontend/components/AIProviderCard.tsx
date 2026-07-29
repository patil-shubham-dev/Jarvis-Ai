"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import {
  Key, Eye, EyeOff, Search, ChevronDown, Sparkles,
  RefreshCw, CheckCircle, AlertCircle, Zap, XCircle,
  Loader, Wifi, WifiOff, Server, Shield, ArrowRight,
  Globe, Bot,
} from "lucide-react";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

interface ModelInfo { id: string; name: string; provider: string; }
interface ProviderInfo { id: string; name: string; }

type Step = "connect" | "key" | "ready";

// ─── Provider Visual Identity ───────────────────────────────────
const PROVIDER_BRANDING: Record<string, {
  icon: string;
  ring: string;
  badge: string;
  label: string;
  gradient: string;
  description: string;
}> = {
  openai:    { icon: "⚡", ring: "ring-emerald-500/30", badge: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400", label: "OpenAI", gradient: "from-emerald-500/10 to-emerald-600/5", description: "GPT-4o, GPT-4, o1 models" },
  anthropic: { icon: "🟣", ring: "ring-purple-500/30", badge: "bg-purple-500/10 text-purple-600 dark:text-purple-400", label: "Anthropic", gradient: "from-purple-500/10 to-purple-600/5", description: "Claude Sonnet 4, Haiku 3" },
  google:    { icon: "🔵", ring: "ring-blue-500/30", badge: "bg-blue-500/10 text-blue-600 dark:text-blue-400", label: "Google Gemini", gradient: "from-blue-500/10 to-blue-600/5", description: "Gemini 2.0 Flash, 2.5 Pro" },
  groq:      { icon: "🟢", ring: "ring-green-500/30", badge: "bg-green-500/10 text-green-600 dark:text-green-400", label: "Groq", gradient: "from-green-500/10 to-green-600/5", description: "Llama 3, Mixtral (fast)" },
  mistral:   { icon: "💨", ring: "ring-orange-500/30", badge: "bg-orange-500/10 text-orange-600 dark:text-orange-400", label: "Mistral", gradient: "from-orange-500/10 to-orange-600/5", description: "Mistral Small, Medium" },
  openrouter:{ icon: "🔄", ring: "ring-indigo-500/30", badge: "bg-indigo-500/10 text-indigo-600 dark:text-indigo-400", label: "OpenRouter", gradient: "from-indigo-500/10 to-indigo-600/5", description: "Multi-provider gateway" },
  deepseek:  { icon: "🐋", ring: "ring-cyan-500/30", badge: "bg-cyan-500/10 text-cyan-600 dark:text-cyan-400", label: "DeepSeek", gradient: "from-cyan-500/10 to-cyan-600/5", description: "DeepSeek Chat, Reasoner" },
  nvidia:    { icon: "🟩", ring: "ring-green-600/30", badge: "bg-green-600/10 text-green-700 dark:text-green-400", label: "NVIDIA", gradient: "from-green-600/10 to-green-700/5", description: "Llama 3.1, Mixtral on NIM" },
};

const STEP_META = [
  { id: "connect" as Step, label: "Server", icon: Server },
  { id: "key" as Step, label: "API Key", icon: Shield },
  { id: "ready" as Step, label: "Configured", icon: Bot },
];

// ─── Helpers ────────────────────────────────────────────────────
function autoSaveCreds(key: string, model: string, providerId: string) {
  try {
    sessionStorage.setItem("jarvis_api_key", key);
    sessionStorage.setItem("jarvis_model", model);
    sessionStorage.setItem("jarvis_provider", providerId);
  } catch {}
}

// ─── Props ──────────────────────────────────────────────────────
interface AIProviderCardProps {
  apiKey: string;
  detectedProvider: ProviderInfo | null;
  selectedModel: string;
  models: ModelInfo[];
  onApiKeyChange: (key: string) => void;
  onModelChange: (model: string) => void;
  onModelsFetched?: (models: ModelInfo[]) => void;
}

// ─── Pulse Ring (shared sub-component) ──────────────────────────
function PulseRing({ active, color }: { active: boolean; color: string }) {
  if (!active) return null;
  return (
    <span className="absolute inset-0 rounded-full animate-ping opacity-20" style={{ backgroundColor: color }} />
  );
}

// ══════════════════════════════════════════════════════════════
// MAIN COMPONENT
// ══════════════════════════════════════════════════════════════
export function AIProviderCard({
  apiKey,
  detectedProvider,
  selectedModel,
  models,
  onApiKeyChange,
  onModelChange,
  onModelsFetched,
}: AIProviderCardProps) {
  // ─── State ────────────────────────────────────────────────
  const [showKey, setShowKey] = useState(false);
  const [showKeyInput, setShowKeyInput] = useState(false);
  const [detecting, setDetecting] = useState(false);
  const [detectError, setDetectError] = useState("");
  const [loadingModels, setLoadingModels] = useState(false);
  const [modelSearch, setModelSearch] = useState("");
  const [showModelDropdown, setShowModelDropdown] = useState(false);
  const [testStatus, setTestStatus] = useState<"idle" | "testing" | "success" | "error">("idle");
  const [testMessage, setTestMessage] = useState("");
  const [testElapsed, setTestElapsed] = useState(0);
  const [connectionStatus, setConnectionStatus] = useState<"disconnected" | "connecting" | "connected" | "error">("disconnected");
  const [localModels, setLocalModels] = useState<ModelInfo[]>([]);
  const [currentStep, setCurrentStep] = useState<Step>("connect");

  const modelDropdownRef = useRef<HTMLDivElement>(null);
  const detectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const effectiveModels = models.length > 0 ? models : localModels;
  const providerMeta = detectedProvider ? PROVIDER_BRANDING[detectedProvider.id] : null;

  // ─── Derive current step ─────────────────────────────────
  useEffect(() => {
    if (connectionStatus === "connected" && apiKey && detectedProvider) {
      setCurrentStep("ready");
    } else if (connectionStatus === "connected") {
      setCurrentStep("key");
    } else {
      setCurrentStep("connect");
    }
  }, [connectionStatus, apiKey, detectedProvider]);

  // ─── Click outside to close dropdown ─────────────────────
  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (modelDropdownRef.current && !modelDropdownRef.current.contains(e.target as Node)) {
        setShowModelDropdown(false);
      }
    };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  useEffect(() => { checkConnection(); }, []);

  // ─── Connection check ────────────────────────────────────
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

  // ─── Fetch models from backend ───────────────────────────
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
      setLocalModels(fetched);
      if (onModelsFetched) onModelsFetched(fetched);
      if (fetched.length > 0) onModelChange(fetched[0].id);
      return fetched;
    } catch { return []; }
    finally { setLoadingModels(false); }
  }, [onModelChange, onModelsFetched]);

  // ─── Detect provider from API key ────────────────────────
  const detectProvider = useCallback(async (key: string) => {
    setDetectError("");
    if (!key || key.length < 10) { onApiKeyChange(key); return; }
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
        autoSaveCreds(key, fetched.length > 0 ? fetched[0].id : selectedModel, data.provider);
      } else {
        setDetectError(data.error || "Provider not recognized");
      }
    } catch (e) {
      setDetectError(e instanceof Error ? e.message : "Connection failed");
    } finally { setDetecting(false); }
  }, [fetchModels, onApiKeyChange, selectedModel]);

  // ─── Key input handler (debounced) ───────────────────────
  const handleKeyChange = (val: string) => {
    if (detectTimerRef.current) clearTimeout(detectTimerRef.current);
    setDetectError("");
    setTestStatus("idle");
    onApiKeyChange(val);
    if (val.length > 10) {
      detectTimerRef.current = setTimeout(() => detectProvider(val), 500);
    }
  };

  // ─── Connection test ─────────────────────────────────────
  const testConnection = async () => {
    if (!apiKey) {
      setTestStatus("error");
      setTestMessage("Enter an API key first");
      return;
    }
    setTestStatus("testing");
    setTestMessage("");
    setTestElapsed(0);

    const startTime = Date.now();
    const timer = setInterval(() => setTestElapsed(Math.floor((Date.now() - startTime) / 1000)), 200);

    try {
      const resp = await fetch(`${API_BASE}/api/proxy/test`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ api_key: apiKey }),
      });
      const text = await resp.text();
      let data;
      try { data = JSON.parse(text); } catch { throw new Error(`Server (${resp.status})`); }
      if (!resp.ok) throw new Error(data.error || data.detail || `Error ${resp.status}`);
      if (data.success === false) {
        setTestStatus("error");
        setTestMessage(data.error || "Connection failed");
      } else {
        autoSaveCreds(apiKey, selectedModel, detectedProvider?.id || data.provider || "");
        setTestStatus("success");
        setTestMessage(data.response?.slice(0, 300) || "Connected ✓");
      }
    } catch (e) {
      setTestStatus("error");
      setTestMessage(e instanceof Error ? e.message : "Request failed");
    } finally { clearInterval(timer); }
  };

  // ─── Derived values ──────────────────────────────────────
  const filteredModels = effectiveModels.filter(
    (m) => m.id.toLowerCase().includes(modelSearch.toLowerCase()) ||
          m.name.toLowerCase().includes(modelSearch.toLowerCase())
  );

  // ══════════════════════════════════════════════════════════
  // STEP 1: BACKEND CONNECTION
  // ══════════════════════════════════════════════════════════
  const renderConnectionStep = () => (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-6 sm:p-8"
    >
      {/* Server status visual */}
      <div className="flex flex-col items-center text-center mb-6">
        <div className="relative mb-4">
          <div className={cn(
            "w-20 h-20 rounded-2xl flex items-center justify-center border-2 transition-all duration-500",
            connectionStatus === "connected"
              ? "bg-success/10 border-success/30 shadow-lg shadow-success/10"
              : connectionStatus === "connecting"
              ? "bg-warning/10 border-warning/30"
              : "bg-destructive/5 border-destructive/20"
          )}>
            {connectionStatus === "connected" ? (
              <motion.div animate={{ scale: [1, 1.1, 1] }} transition={{ duration: 2, repeat: Infinity }}>
                <Server className="w-10 h-10 text-success" />
              </motion.div>
            ) : connectionStatus === "connecting" ? (
              <Loader className="w-10 h-10 text-warning animate-spin" />
            ) : (
              <Server className="w-10 h-10 text-destructive/60" />
            )}
          </div>
          {connectionStatus === "connected" && <PulseRing active color="#7DAA7D" />}
        </div>

        <h3 className="text-lg font-semibold text-foreground mb-1">
          {connectionStatus === "connected" ? "Backend Connected" :
           connectionStatus === "connecting" ? "Connecting..." :
           connectionStatus === "error" ? "Connection Failed" : "Not Connected"}
        </h3>
        <p className="text-sm text-muted-foreground max-w-xs">
          {connectionStatus === "connected"
            ? `Server at ${API_BASE} is online and ready`
            : connectionStatus === "connecting"
            ? "Checking server availability..."
            : "Start the backend server to continue"}
        </p>

        {connectionStatus === "error" && (
          <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="mt-4">
            <button
              onClick={checkConnection}
              className="px-5 py-2.5 bg-primary text-primary-foreground rounded-xl text-sm font-medium hover:opacity-90 transition-all shadow-sm flex items-center gap-2"
            >
              <RefreshCw className="w-4 h-4" />
              Retry Connection
            </button>
          </motion.div>
        )}
      </div>

      {/* URL indicator */}
      <div className="bg-muted/50 rounded-xl px-4 py-2.5 border border-border flex items-center justify-between">
        <div className="flex items-center gap-2 min-w-0">
          <Globe className="w-4 h-4 text-muted-foreground flex-shrink-0" />
          <span className="text-xs font-mono text-muted-foreground truncate">{API_BASE}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className={cn(
            "w-2 h-2 rounded-full",
            connectionStatus === "connected" ? "bg-success" : "bg-muted-foreground/30"
          )} />
          <span className="text-[10px] text-muted-foreground">
            {connectionStatus === "connected" ? "Online" : "Offline"}
          </span>
        </div>
      </div>

      {connectionStatus === "connected" && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="mt-6"
        >
          <button
            onClick={() => setShowKeyInput(true)}
            className="w-full py-3 bg-primary text-primary-foreground rounded-xl text-sm font-medium hover:opacity-90 transition-all shadow-sm flex items-center justify-center gap-2"
          >
            <Key className="w-4 h-4" />
            Configure API Key
            <ArrowRight className="w-4 h-4" />
          </button>
        </motion.div>
      )}
    </motion.div>
  );

  // ══════════════════════════════════════════════════════════
  // STEP 2: API KEY INPUT
  // ══════════════════════════════════════════════════════════
  const renderKeyStep = () => (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-6 sm:p-8"
    >
      {/* Header */}
      <div className="mb-6">
        <h3 className="text-lg font-semibold text-foreground mb-1">Add Your API Key</h3>
        <p className="text-sm text-muted-foreground">
          Paste your key below. It&apos;s auto-detected and stored securely for this session.
        </p>
      </div>

      {/* Provider showcase grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-5">
        {Object.entries(PROVIDER_BRANDING).map(([id, meta]) => {
          const isActive = detectedProvider?.id === id;
          return (
            <motion.button
              key={id}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={() => {
                setShowKeyInput(true);
                document.getElementById("api-key-input")?.focus();
              }}
              className={cn(
                "relative flex flex-col items-center gap-1.5 p-3 rounded-xl border text-center transition-all",
                isActive
                  ? "bg-gradient-to-br border-primary/30 shadow-sm " + meta.gradient
                  : "bg-muted/30 border-border hover:border-primary/20 hover:bg-muted/50"
              )}
            >
              <span className="text-xl">{meta.icon}</span>
              <span className={cn(
                "text-[10px] font-semibold leading-tight",
                isActive ? "text-foreground" : "text-muted-foreground"
              )}>
                {meta.label}
              </span>
              {isActive && (
                <motion.div
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  className="absolute -top-1.5 -right-1.5 w-5 h-5 bg-primary rounded-full flex items-center justify-center"
                >
                  <CheckCircle className="w-3 h-3 text-white" />
                </motion.div>
              )}
            </motion.button>
          );
        })}
      </div>

      {/* Key input area */}
      <AnimatePresence>
        {(showKeyInput || apiKey.length > 0) && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden"
          >
            <div className="relative mb-4">
              <input
                id="api-key-input"
                type={showKey ? "text" : "password"}
                value={apiKey}
                onChange={(e) => handleKeyChange(e.target.value)}
                placeholder="sk-... / sk-ant-... / AIza..."
                disabled={connectionStatus === "error"}
                autoFocus
                className={cn(
                  "w-full pr-20 pl-12 py-3.5 text-sm rounded-xl border outline-none transition-all font-mono",
                  connectionStatus === "error"
                    ? "bg-destructive/5 border-destructive/20 text-destructive/60 cursor-not-allowed"
                    : "bg-muted border-border focus:border-primary focus:ring-1 focus:ring-primary/20 placeholder:text-muted-foreground/40"
                )}
              />
              <Key className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground/60" />

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

            {/* Detection error */}
            <AnimatePresence>
              {detectError && (
                <motion.div
                  initial={{ opacity: 0, y: -4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -4 }}
                  className="flex items-start gap-2 mb-4 text-xs text-destructive"
                >
                  <AlertCircle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                  <span>{detectError}</span>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>

      {/* No key entered state */}
      {!showKeyInput && apiKey.length === 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="text-center py-4"
        >
          <p className="text-xs text-muted-foreground mb-3">Tap a provider above or paste your key to get started</p>
          <button
            onClick={() => setShowKeyInput(true)}
            className="text-xs text-primary font-medium hover:underline"
          >
            I have an API key →
          </button>
        </motion.div>
      )}
    </motion.div>
  );

  // ══════════════════════════════════════════════════════════
  // STEP 3: READY — Model Selector + Test
  // ══════════════════════════════════════════════════════════
  const renderReadyStep = () => (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-6 sm:p-8"
    >
      {/* Status header */}
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-3">
          {providerMeta && (
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              className={cn(
                "w-12 h-12 rounded-2xl flex items-center justify-center text-xl border",
                "bg-gradient-to-br " + providerMeta.gradient,
                "border-primary/20"
              )}
            >
              <span>{providerMeta.icon}</span>
            </motion.div>
          )}
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-foreground">{providerMeta?.label || "Provider"}</span>
              <span className={cn("px-1.5 py-0.5 rounded text-[9px] font-semibold uppercase tracking-wider", providerMeta?.badge)}>
                Connected
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground mt-0.5">{providerMeta?.description}</p>
          </div>
        </div>

        <button
          onClick={() => { onApiKeyChange(""); setShowKeyInput(false); }}
          className="p-2 rounded-xl hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
          title="Change key"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {/* Model selector */}
      <div className="space-y-3">
        <div className="relative" ref={modelDropdownRef}>
          <label className="text-[11px] font-medium text-muted-foreground uppercase tracking-wider mb-1.5 block">
            Active Model
          </label>
          <button
            onClick={() => setShowModelDropdown(!showModelDropdown)}
            className="w-full flex items-center justify-between px-4 py-3 text-sm bg-muted/50 rounded-xl border border-border hover:border-primary/30 transition-colors text-left"
          >
            <div className="flex items-center gap-3 min-w-0 flex-1">
              <div className={cn(
                "w-8 h-8 rounded-xl flex items-center justify-center",
                "bg-gradient-to-br from-primary/10 to-accent/10"
              )}>
                <Bot className="w-4 h-4 text-primary" />
              </div>
              <div className="min-w-0">
                <div className="truncate text-sm font-medium text-foreground">
                  {effectiveModels.find(m => m.id === selectedModel)?.name || selectedModel || "Select a model"}
                </div>
                {selectedModel && (
                  <div className="truncate text-[10px] font-mono text-muted-foreground">{selectedModel}</div>
                )}
              </div>
            </div>
            <ChevronDown className={cn(
              "w-4 h-4 text-muted-foreground transition-transform flex-shrink-0 ml-2",
              showModelDropdown && "rotate-180"
            )} />
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
                  {loadingModels ? (
                    <div className="p-8 flex items-center justify-center">
                      <Loader className="w-5 h-5 text-primary animate-spin" />
                    </div>
                  ) : filteredModels.length === 0 ? (
                    <div className="p-6 text-center">
                      <p className="text-xs text-muted-foreground">
                        {modelSearch ? `No models match "${modelSearch}"` : "No models loaded"}
                      </p>
                    </div>
                  ) : (
                    filteredModels.map((model) => (
                      <button
                        key={model.id}
                        onClick={() => { onModelChange(model.id); setShowModelDropdown(false); setModelSearch(""); }}
                        className={cn(
                          "w-full flex items-center gap-3 px-3 py-3 rounded-lg text-sm transition-colors text-left border-b border-border/50 last:border-0",
                          selectedModel === model.id
                            ? "bg-primary/5 text-primary"
                            : "hover:bg-muted text-foreground"
                        )}
                      >
                        <Sparkles className={cn(
                          "w-4 h-4 flex-shrink-0",
                          selectedModel === model.id ? "text-primary" : "text-muted-foreground"
                        )} />
                        <div className="min-w-0 flex-1">
                          <div className="truncate font-medium">{model.name}</div>
                          <div className="truncate text-[10px] text-muted-foreground font-mono">{model.id}</div>
                        </div>
                        {selectedModel === model.id && (
                          <CheckCircle className="w-4 h-4 text-primary flex-shrink-0" />
                        )}
                      </button>
                    ))
                  )}
                </div>

                <div className="p-2 border-t border-border flex justify-between text-[10px] text-muted-foreground px-3">
                  <span>{effectiveModels.length} models available</span>
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

        {/* Test connection */}
        <div>
          <button
            onClick={testConnection}
            disabled={testStatus === "testing"}
            className={cn(
              "w-full flex items-center justify-between px-4 py-3.5 rounded-xl text-sm transition-all border",
              testStatus === "success"
                ? "bg-success/5 border-success/20"
                : testStatus === "error"
                ? "bg-destructive/5 border-destructive/20"
                : "bg-muted/30 border-border hover:border-primary/30 hover:bg-muted/50"
            )}
          >
            <div className="flex items-center gap-3">
              <div className={cn(
                "w-8 h-8 rounded-xl flex items-center justify-center transition-colors",
                testStatus === "testing" ? "bg-primary/10" :
                testStatus === "success" ? "bg-success/10" :
                testStatus === "error" ? "bg-destructive/10" :
                "bg-muted"
              )}>
                {testStatus === "testing" ? (
                  <Loader className="w-4 h-4 text-primary animate-spin" />
                ) : testStatus === "success" ? (
                  <CheckCircle className="w-4 h-4 text-success" />
                ) : testStatus === "error" ? (
                  <XCircle className="w-4 h-4 text-destructive" />
                ) : (
                  <Zap className="w-4 h-4 text-muted-foreground" />
                )}
              </div>
              <div className="text-left">
                <span className={cn(
                  "text-xs font-medium block",
                  testStatus === "success" ? "text-success" :
                  testStatus === "error" ? "text-destructive" :
                  "text-foreground"
                )}>
                  {testStatus === "testing" ? "Testing..." :
                   testStatus === "success" ? "Connected" :
                   testStatus === "error" ? "Failed" :
                   "Test Connection"}
                </span>
                {testStatus === "idle" && (
                  <span className="text-[10px] text-muted-foreground">Verify API key and provider</span>
                )}
                {testStatus === "testing" && (
                  <span className="text-[10px] text-primary/60">{testElapsed}s</span>
                )}
              </div>
            </div>
            {testStatus === "idle" && (
              <div className="flex items-center gap-1 text-[10px] text-muted-foreground">
                <span>{selectedModel || "auto"}</span>
                <ArrowRight className="w-3 h-3" />
              </div>
            )}
          </button>

          <AnimatePresence>
            {testMessage && testStatus !== "idle" && (
              <motion.div
                initial={{ opacity: 0, y: -6, height: 0 }}
                animate={{ opacity: 1, y: 0, height: "auto" }}
                exit={{ opacity: 0, y: -6, height: 0 }}
                className="overflow-hidden"
              >
                <div className={cn(
                  "mt-2 p-3 rounded-xl text-xs leading-relaxed border max-h-32 overflow-y-auto",
                  testStatus === "success"
                    ? "bg-success/5 border-success/20 text-foreground/80"
                    : "bg-destructive/5 border-destructive/20 text-destructive"
                )}>
                  <div className="flex items-center gap-2 mb-1.5">
                    <div className={cn(
                      "w-1.5 h-1.5 rounded-full",
                      testStatus === "success" ? "bg-success" : "bg-destructive"
                    )} />
                    <span className={cn(
                      "text-[10px] font-semibold uppercase tracking-wider",
                      testStatus === "success" ? "text-success" : "text-destructive"
                    )}>
                      {testStatus === "success" ? "Response" : "Error"}
                    </span>
                  </div>
                  {testMessage}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </motion.div>
  );

  // ══════════════════════════════════════════════════════════
  // RENDER
  // ══════════════════════════════════════════════════════════
  return (
    <div className="space-y-5">
      {/* Stepper indicator */}
      <div className="flex items-center gap-0 bg-card rounded-2xl border border-border p-1">
        {STEP_META.map((step, idx) => {
          const isActive = currentStep === step.id;
          const isPast = STEP_META.findIndex(s => s.id === currentStep) > idx;
          const Icon = step.icon;
          return (
            <div key={step.id} className="flex-1 flex items-center justify-center">
              <div className={cn(
                "flex items-center gap-2 px-3 py-2 rounded-xl transition-all",
                isActive ? "bg-primary/5 text-primary" :
                isPast ? "text-success" : "text-muted-foreground/40"
              )}>
                <div className={cn(
                  "w-7 h-7 rounded-lg flex items-center justify-center transition-all",
                  isActive ? "bg-primary text-primary-foreground shadow-sm" :
                  isPast ? "bg-success/10 text-success" :
                  "bg-muted text-muted-foreground/40"
                )}>
                  {isPast ? (
                    <CheckCircle className="w-3.5 h-3.5" />
                  ) : (
                    <Icon className="w-3.5 h-3.5" />
                  )}
                </div>
                <span className={cn(
                  "text-[11px] font-medium transition-all hidden sm:block",
                  isActive ? "text-foreground" :
                  isPast ? "text-success" : "text-muted-foreground/60"
                )}>
                  {step.label}
                </span>
              </div>
              {idx < STEP_META.length - 1 && (
                <div className={cn(
                  "h-px flex-1 mx-1 transition-colors",
                  isPast ? "bg-success/30" : "bg-border"
                )} />
              )}
            </div>
          );
        })}
      </div>

      {/* Main card */}
      <div className={cn(
        "rounded-2xl border transition-all duration-500 overflow-hidden",
        "bg-card",
        connectionStatus === "error" ? "border-destructive/20" : "border-border",
        detectedProvider && providerMeta && `ring-1 ${providerMeta.ring}`
      )}>
        {currentStep === "connect" && renderConnectionStep()}
        {currentStep === "key" && renderKeyStep()}
        {currentStep === "ready" && renderReadyStep()}
      </div>

      {/* Quick status summary when configured */}
      {currentStep === "ready" && providerMeta && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex items-center justify-between px-4 py-3 bg-card rounded-xl border border-border"
        >
          <div className="flex items-center gap-2.5">
            <span className="text-lg">{providerMeta.icon}</span>
            <div>
              <span className="text-xs font-medium text-foreground">{providerMeta.label}</span>
              <span className="text-[10px] text-muted-foreground ml-2">{selectedModel}</span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Wifi className="w-3.5 h-3.5 text-success" />
            <span className="text-[10px] text-success font-medium">Active</span>
          </div>
        </motion.div>
      )}

      {/* Help text */}
      {currentStep === "key" && !apiKey && (
        <p className="text-[11px] text-muted-foreground text-center px-4 leading-relaxed">
          Your API key is stored in your browser for this session only.
          <br />It is never saved to our servers.
        </p>
      )}
    </div>
  );
}

// ══════════════════════════════════════════════════════════════
// PROVIDER BADGE (standalone export)
// ══════════════════════════════════════════════════════════════
export function ProviderBadge({ providerId, size = "sm" }: { providerId: string; size?: "sm" | "md" | "lg" }) {
  const meta = PROVIDER_BRANDING[providerId];
  if (!meta) return null;
  const sizeClass = size === "lg" ? "px-3 py-1.5 text-sm" : size === "md" ? "px-2.5 py-1 text-xs" : "px-2 py-0.5 text-[10px]";
  return (
    <span className={cn("rounded-lg font-medium flex items-center gap-1.5", sizeClass, meta.badge)}>
      <span>{meta.icon}</span>
      {meta.label}
    </span>
  );
}
