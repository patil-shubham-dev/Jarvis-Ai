"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Settings, Key, Globe, Smartphone, Save,
  CheckCircle, Eye, EyeOff, Search, ChevronDown, Sparkles,
  RefreshCw, Bell, Brain, AlertCircle, Zap,
} from "lucide-react";
import { cn } from "@/lib/utils";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";
const PROVIDER_META: Record<string, { icon: string; color: string; label: string }> = {
  openai:    { icon: "⚡", color: "text-emerald-600 dark:text-emerald-400", label: "OpenAI" },
  anthropic: { icon: "🟣", color: "text-purple-600 dark:text-purple-400", label: "Anthropic" },
  google:    { icon: "🔵", color: "text-blue-600 dark:text-blue-400", label: "Google Gemini" },
  groq:      { icon: "🟢", color: "text-green-600 dark:text-green-400", label: "Groq" },
  mistral:   { icon: "💨", color: "text-orange-600 dark:text-orange-400", label: "Mistral" },
  openrouter:{ icon: "🔄", color: "text-indigo-600 dark:text-indigo-400", label: "OpenRouter" },
  deepseek:  { icon: "🐋", color: "text-cyan-600 dark:text-cyan-400", label: "DeepSeek" },
};

interface ModelInfo { id: string; name: string; provider: string; }
interface ProviderInfo { id: string; name: string; }

type TestStatus = "idle" | "testing" | "success" | "error";

export default function SettingsPage() {
  const [activeSection, setActiveSection] = useState("api");
  const [saved, setSaved] = useState(false);
  const [apiKey, setApiKey] = useState(() => { try { return sessionStorage.getItem("jarvis_api_key") || ""; } catch { return ""; } });
  const [showKey, setShowKey] = useState(false);
  const [detectedProvider, setDetectedProvider] = useState<ProviderInfo | null>(() => {
    try {
      const provider = sessionStorage.getItem("jarvis_provider");
      if (provider && provider !== "null") return { id: provider, name: provider.charAt(0).toUpperCase() + provider.slice(1) };
    } catch {}
    return null;
  });
  const [detecting, setDetecting] = useState(false);
  const [detectError, setDetectError] = useState("");
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [loadingModels, setLoadingModels] = useState(false);
  const [selectedModel, setSelectedModel] = useState(() => { try { return sessionStorage.getItem("jarvis_model") || ""; } catch { return ""; } });
  const [modelSearch, setModelSearch] = useState("");
  const [showModelDropdown, setShowModelDropdown] = useState(false);
  const [testStatus, setTestStatus] = useState<TestStatus>("idle");
  const [testMessage, setTestMessage] = useState("");
  const modelDropdownRef = useRef<HTMLDivElement>(null);

  const defaultSettings = {
    theme: "light", auto_connect: true, voice_wake: false,
    screen_capture: false, memory_enabled: true, analytics: false, notifications: true,
  };
  const [settings, setSettings] = useState<typeof defaultSettings>(() => {
    try {
      const stored = localStorage.getItem("jarvis_settings");
      if (stored) {
        const parsed = JSON.parse(stored);
        if (parsed.theme === "dark") document.documentElement.classList.add("dark");
        return { ...defaultSettings, ...parsed };
      }
    } catch (e) { console.warn("Failed to parse stored settings", e); }
    return defaultSettings;
  });
  const [showSaveIndicator, setShowSaveIndicator] = useState(false);
  const detectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (modelDropdownRef.current && !modelDropdownRef.current.contains(e.target as Node)) {
        setShowModelDropdown(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const fetchModels = useCallback(async (key: string, provider: string) => {
    setLoadingModels(true);
    try {
      const resp = await fetch(`${API_BASE}/api/providers/models`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ api_key: key, provider }),
      });
      if (!resp.ok) throw new Error(`Server returned ${resp.status}`);
      const data = await resp.json();
      if (data.models && data.models.length > 0) {
        setModels(data.models);
        const current = sessionStorage.getItem("jarvis_model");
        if (current && data.models.some((m: ModelInfo) => m.id === current)) {
          setSelectedModel(current);
        } else {
          setSelectedModel(data.models[0].id);
        }
      } else {
        setModels([]);
        if (data.error) setDetectError(data.error);
      }
    } catch (e) {
      setModels([]);
      setDetectError(e instanceof Error ? e.message : "Failed to load models");
    } finally {
      setLoadingModels(false);
    }
  }, []);

  const detectProvider = useCallback(async (key: string) => {
    setDetectError("");
    if (!key || key.length < 10) {
      setDetectedProvider(null); setModels([]); setSelectedModel(""); return;
    }
    setDetecting(true);
    try {
      const resp = await fetch(`${API_BASE}/api/providers/detect`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ api_key: key }),
      });
      if (!resp.ok) throw new Error(`Server returned ${resp.status}`);
      const data = await resp.json();
      if (data.provider) {
        setDetectedProvider({ id: data.provider, name: data.name || data.provider });
        fetchModels(key, data.provider);
      } else {
        setDetectedProvider(null); setModels([]);
        setDetectError(data.error || "Provider not recognized");
      }
    } catch (e) {
      setDetectedProvider(null); setModels([]);
      setDetectError(e instanceof Error ? e.message : "Connection failed — is the backend running?");
    } finally {
      setDetecting(false);
    }
  }, [fetchModels]);

  const testConnection = async () => {
    if (!apiKey || !selectedModel) return;
    setTestStatus("testing");
    setTestMessage("");
    try {
      const resp = await fetch(`${API_BASE}/api/proxy/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          api_key: apiKey,
          model: selectedModel,
          messages: [{ role: "user", content: "Reply with exactly: Connection successful" }],
        }),
      });
      const data = await resp.json();
      if (data.error) {
        setTestStatus("error");
        setTestMessage(data.error);
      } else {
        const content = data.choices?.[0]?.message?.content || data.content?.[0]?.text || JSON.stringify(data).slice(0, 200);
        setTestStatus("success");
        setTestMessage(content);
      }
    } catch (e) {
      setTestStatus("error");
      setTestMessage(e instanceof Error ? e.message : "Request failed");
    }
  };

  const handleKeyChange = (val: string) => {
    setApiKey(val);
    setDetectError("");
    setTestStatus("idle");
    if (detectTimerRef.current !== null) clearTimeout(detectTimerRef.current);
    if (val.length > 10) {
      detectTimerRef.current = setTimeout(() => detectProvider(val), 400);
    } else {
      setDetectedProvider(null); setModels([]);
    }
  };

  const handleSave = () => {
    const safe = { ...settings };
    localStorage.setItem("jarvis_settings", JSON.stringify(safe));
    sessionStorage.setItem("jarvis_api_key", apiKey);
    sessionStorage.setItem("jarvis_model", selectedModel);
    sessionStorage.setItem("jarvis_provider", detectedProvider?.id || "");
    if (settings.theme === "dark") document.documentElement.classList.add("dark");
    else document.documentElement.classList.remove("dark");
    setSaved(true);
    setShowSaveIndicator(true);
    setTimeout(() => { setSaved(false); setShowSaveIndicator(false); }, 2500);
  };

  const filteredModels = models.filter(
    (m) => m.id.toLowerCase().includes(modelSearch.toLowerCase()) ||
          m.name.toLowerCase().includes(modelSearch.toLowerCase())
  );

  const providerMeta = detectedProvider ? PROVIDER_META[detectedProvider.id] : null;

  const apiSection = (
    <div className="space-y-5">
      <div className="p-5 rounded-2xl bg-card border border-border">
        <label className="text-sm font-medium text-foreground block mb-2">AI Provider API Key</label>
        <div className="relative">
          <input
            type={showKey ? "text" : "password"}
            value={apiKey}
            onChange={(e) => handleKeyChange(e.target.value)}
            placeholder="Paste your API key (sk-... , sk-ant-... , AIza...)"
            className="w-full pr-20 pl-4 py-3 text-sm bg-muted rounded-xl border border-border outline-none focus:border-primary transition-colors placeholder:text-muted-foreground/40 font-mono"
          />
          <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1">
            <button onClick={() => setShowKey(!showKey)} className="p-1.5 rounded-lg hover:bg-muted transition-colors">
              {showKey ? <EyeOff className="w-4 h-4 text-muted-foreground" /> : <Eye className="w-4 h-4 text-muted-foreground" />}
            </button>
          </div>
        </div>

        {detecting && (
          <div className="flex items-center gap-2 mt-3 text-xs text-muted-foreground">
            <RefreshCw className="w-3 h-3 animate-spin" />
            Detecting provider...
          </div>
        )}

        {detectError && !detecting && (
          <div className="flex items-center gap-2 mt-3 text-xs text-destructive">
            <AlertCircle className="w-3 h-3 flex-shrink-0" />
            {detectError}
          </div>
        )}

        {detectedProvider && !detecting && providerMeta && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            className="mt-3 flex items-center gap-2 flex-wrap"
          >
            <span className={cn("px-2.5 py-1 rounded-lg bg-muted text-xs font-medium flex items-center gap-1.5", providerMeta.color)}>
              <span>{providerMeta.icon}</span>
              {providerMeta.label}
            </span>
            <span className="text-[10px] text-muted-foreground">auto-detected</span>
            {models.length > 0 && (
              <span className="text-[10px] text-muted-foreground/60">{models.length} models</span>
            )}
          </motion.div>
        )}
      </div>

      {detectedProvider && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="p-5 rounded-2xl bg-card border border-border"
        >
          <label className="text-sm font-medium text-foreground block mb-2">Model</label>
          <div className="relative" ref={modelDropdownRef}>
            <button
              onClick={() => setShowModelDropdown(!showModelDropdown)}
              className="w-full flex items-center justify-between px-4 py-3 text-sm bg-muted rounded-xl border border-border outline-none focus:border-primary transition-colors text-left"
            >
              <div className="flex items-center gap-2 min-w-0 flex-1">
                {loadingModels ? (
                  <span className="text-muted-foreground flex items-center gap-2">
                    <RefreshCw className="w-3 h-3 animate-spin" />
                    Loading models...
                  </span>
                ) : selectedModel ? (
                  <>
                    <Sparkles className="w-4 h-4 text-primary flex-shrink-0" />
                    <span className="truncate">{models.find(m => m.id === selectedModel)?.name || selectedModel}</span>
                    <span className="text-[10px] text-muted-foreground font-mono truncate hidden sm:inline">({selectedModel})</span>
                  </>
                ) : (
                  <span className="text-muted-foreground">No models available</span>
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
                        {modelSearch ? "No matching models" : "No models loaded"}
                      </div>
                    ) : (
                      filteredModels.map((model) => (
                        <button
                          key={model.id}
                          onClick={() => { setSelectedModel(model.id); setShowModelDropdown(false); setModelSearch(""); }}
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
                    <span>{models.length} models</span>
                    <button onClick={() => fetchModels(apiKey, detectedProvider?.id || "")} className="hover:text-foreground transition-colors flex items-center gap-1">
                      <RefreshCw className="w-3 h-3" /> Refresh
                    </button>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </motion.div>
      )}

      {detectedProvider && selectedModel && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="p-5 rounded-2xl bg-card border border-border"
        >
          <div className="flex items-center justify-between mb-3">
            <label className="text-sm font-medium text-foreground">Test Connection</label>
            <button
              onClick={testConnection}
              disabled={testStatus === "testing"}
              className={cn(
                "flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-medium transition-all",
                testStatus === "success" ? "bg-success/20 text-success" :
                testStatus === "error" ? "bg-destructive/20 text-destructive" :
                "bg-primary text-primary-foreground hover:opacity-90",
                testStatus === "testing" && "opacity-50 cursor-not-allowed"
              )}
            >
              {testStatus === "testing" ? (
                <><RefreshCw className="w-3.5 h-3.5 animate-spin" /> Testing...</>
              ) : testStatus === "success" ? (
                <><CheckCircle className="w-3.5 h-3.5" /> Connected</>
              ) : testStatus === "error" ? (
                <><AlertCircle className="w-3.5 h-3.5" /> Failed</>
              ) : (
                <><Zap className="w-3.5 h-3.5" /> Test</>
              )}
            </button>
          </div>
          {testStatus === "success" && testMessage && (
            <div className="p-3 rounded-xl bg-success/5 border border-success/20 text-xs text-foreground/80 leading-relaxed max-h-24 overflow-y-auto">
              <span className="text-success font-medium block mb-1">Response:</span>
              {testMessage}
            </div>
          )}
          {testStatus === "error" && testMessage && (
            <div className="p-3 rounded-xl bg-destructive/5 border border-destructive/20 text-xs text-destructive leading-relaxed">
              {testMessage}
            </div>
          )}
        </motion.div>
      )}
    </div>
  );

  const sections = [
    { id: "api", title: "API Key", icon: Key, desc: "Bring Your Own Key — auto-detects provider", content: apiSection },
    {
      id: "model", title: "Preferences", icon: Globe, desc: "Theme and behavior settings",
      content: (
        <div className="space-y-3">
          <SettingCard label="Theme" desc="Color scheme" control={
            <select value={settings.theme} onChange={(e) => setSettings((s) => ({ ...s, theme: e.target.value }))}
              className="px-3 py-2 text-xs bg-muted rounded-xl border border-border outline-none focus:border-primary transition-colors text-foreground">
              <option value="light">Light</option>
              <option value="dark">Dark</option>
              <option value="system">System</option>
            </select>
          } />
        </div>
      ),
    },
    {
      id: "android", title: "Android", icon: Smartphone, desc: "Device integration",
      content: (
        <div className="space-y-3">
          <ToggleCard label="Auto-connect to device" desc="Pair with Android device" checked={settings.auto_connect} onChange={(v) => setSettings((s) => ({ ...s, auto_connect: v }))} />
          <ToggleCard label="Wake word detection" desc="'Hey Jarvis' wake word" checked={settings.voice_wake} onChange={(v) => setSettings((s) => ({ ...s, voice_wake: v }))} />
          <ToggleCard label="Screen capture" desc="Allow vision tasks" checked={settings.screen_capture} onChange={(v) => setSettings((s) => ({ ...s, screen_capture: v }))} />
        </div>
      ),
    },
    {
      id: "notifications", title: "Notifications", icon: Bell, desc: "Alert and reminder preferences",
      content: (
        <div className="space-y-3">
          <ToggleCard label="Notifications" desc="Receive alerts from Jarvis" checked={settings.notifications} onChange={(v) => setSettings((s) => ({ ...s, notifications: v }))} />
          <div className="p-4 rounded-2xl bg-card border border-border">
            <p className="text-xs text-muted-foreground">Notifications are managed by the backend service. Enable this to receive proactive alerts, reminders, and memory summaries.</p>
          </div>
        </div>
      ),
    },
    {
      id: "memory", title: "Memory", icon: Brain, desc: "Persistent storage",
      content: (
        <div className="space-y-3">
          <ToggleCard label="Memory enabled" desc="Allow Jarvis to remember conversations" checked={settings.memory_enabled} onChange={(v) => setSettings((s) => ({ ...s, memory_enabled: v }))} />
          <ToggleCard label="Usage analytics" desc="Help improve Jarvis" checked={settings.analytics} onChange={(v) => setSettings((s) => ({ ...s, analytics: v }))} />
          <button
            onClick={async () => { try { await fetch(`${API_BASE}/api/memories`, { method: "DELETE" }); } catch {} }}
            className="w-full p-4 rounded-2xl bg-destructive/5 border border-destructive/20 hover:bg-destructive/10 transition-colors text-left"
          >
            <div className="flex items-center justify-between">
              <div>
                <label className="text-sm font-medium text-destructive">Clear all memory</label>
                <p className="text-xs text-muted-foreground mt-0.5">Delete all stored memories</p>
              </div>
              <span className="px-3 py-1.5 text-xs font-medium text-destructive bg-destructive/10 rounded-xl">Clear</span>
            </div>
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="min-h-screen bg-background p-4 sm:p-6 pb-28">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-6 sm:mb-8">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-card flex items-center justify-center border border-border">
              <Settings className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-foreground">Settings</h1>
              <p className="text-sm text-muted-foreground hidden sm:block">Configure Jarvis AI OS</p>
            </div>
          </div>
          <button
            onClick={handleSave}
            className={cn(
              "flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all shadow-sm",
              saved ? "bg-success/20 text-success" : "bg-primary text-primary-foreground hover:opacity-90"
            )}
          >
            {saved ? <><CheckCircle className="w-4 h-4" /> Saved</> : <><Save className="w-4 h-4" /> Save</>}
          </button>
        </div>

        <div className="flex flex-col sm:flex-row gap-4 sm:gap-6">
          <div className="sm:w-48 flex-shrink-0">
            <div className="flex sm:flex-col gap-1 overflow-x-auto sm:overflow-visible pb-2 sm:pb-0 sticky sm:top-6">
              {sections.map((s) => {
                const Icon = s.icon;
                return (
                  <button
                    key={s.id}
                    onClick={() => setActiveSection(s.id)}
                    className={cn(
                      "flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-sm transition-all text-left whitespace-nowrap sm:whitespace-normal",
                      activeSection === s.id
                        ? "bg-card text-primary font-medium border border-border shadow-sm"
                        : "text-muted-foreground hover:bg-card/50"
                    )}
                  >
                    <Icon className="w-4 h-4 flex-shrink-0" />
                    <span className="hidden sm:inline">{s.title}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex-1 min-w-0">
            {sections.map((s) => {
              if (s.id !== activeSection) return null;
              const Icon = s.icon;
              return (
                <motion.div key={s.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
                  <div className="flex items-center gap-3 mb-5">
                    <div className="w-10 h-10 rounded-xl bg-card flex items-center justify-center border border-border">
                      <Icon className="w-5 h-5 text-primary" />
                    </div>
                    <div>
                      <h2 className="text-lg font-semibold text-foreground">{s.title}</h2>
                      <p className="text-sm text-muted-foreground">{s.desc}</p>
                    </div>
                  </div>
                  {s.content}
                </motion.div>
              );
            })}
          </div>
        </div>
      </div>

      <AnimatePresence>
        {showSaveIndicator && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 20 }}
            className="fixed bottom-28 left-1/2 -translate-x-1/2 z-50 px-4 py-2.5 bg-foreground text-background rounded-xl text-xs font-medium shadow-2xl flex items-center gap-2"
          >
            <CheckCircle className="w-4 h-4" />
            Settings saved
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function ToggleCard({ label, desc, checked, onChange }: {
  label: string; desc: string; checked: boolean; onChange: (v: boolean) => void;
}) {
  return (
    <div className="p-4 rounded-2xl bg-card border border-border hover:border-primary/20 transition-colors">
      <div className="flex items-center justify-between">
        <div>
          <label className="text-sm font-medium text-foreground">{label}</label>
          <p className="text-xs text-muted-foreground mt-0.5">{desc}</p>
        </div>
        <button
          role="switch"
          aria-checked={checked}
          onClick={() => onChange(!checked)}
          className={cn(
            "w-11 h-6 rounded-full transition-colors relative flex-shrink-0 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2",
            checked ? "bg-primary" : "bg-border"
          )}
        >
          <div className={cn(
            "w-5 h-5 bg-white rounded-full shadow-sm absolute top-0.5 transition-transform",
            checked ? "translate-x-[22px]" : "translate-x-0.5"
          )} />
        </button>
      </div>
    </div>
  );
}

function SettingCard({ label, desc, control }: {
  label: string; desc: string; control: React.ReactNode;
}) {
  return (
    <div className="p-4 rounded-2xl bg-card border border-border">
      <div className="flex items-center justify-between">
        <div>
          <label className="text-sm font-medium text-foreground">{label}</label>
          <p className="text-xs text-muted-foreground mt-0.5">{desc}</p>
        </div>
        {control}
      </div>
    </div>
  );
}
