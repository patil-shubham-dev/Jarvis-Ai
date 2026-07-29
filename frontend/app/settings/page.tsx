"use client";

import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Settings, Key, Globe, Smartphone, Save,
  CheckCircle, Bell, Brain,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { AIProviderCard } from "@/components/AIProviderCard";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

interface ProviderInfo { id: string; name: string; }

export default function SettingsPage() {
  const [activeSection, setActiveSection] = useState("api");
  const [saved, setSaved] = useState(false);
  const [apiKey, setApiKey] = useState(() => { try { return sessionStorage.getItem("jarvis_api_key") || ""; } catch { return ""; } });
  const [detectedProvider, setDetectedProvider] = useState<ProviderInfo | null>(() => {
    try {
      const p = sessionStorage.getItem("jarvis_provider");
      if (p && p !== "null") return { id: p, name: p.charAt(0).toUpperCase() + p.slice(1) };
    } catch {}
    return null;
  });
  const [models, setModels] = useState<any[]>([]);
  const [selectedModel, setSelectedModel] = useState(() => { try { return sessionStorage.getItem("jarvis_model") || ""; } catch { return ""; } });

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

  const apiSection = (
    <AIProviderCard
      apiKey={apiKey}
      detectedProvider={detectedProvider}
      selectedModel={selectedModel}
      models={models}
      onApiKeyChange={(key) => setApiKey(key)}              onModelChange={(model) => setSelectedModel(model)}
              onModelsFetched={(fetched) => setModels(fetched)}
            />
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
