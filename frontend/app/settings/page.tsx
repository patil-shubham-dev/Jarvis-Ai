"use client";

import { useState } from "react";
import { motion } from "framer-motion";
import { Settings, Key, Globe, Palette, Shield, Smartphone, Save, CheckCircle, Eye, EyeOff, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

interface SettingSection {
  id: string;
  title: string;
  icon: LucideIcon;
  description: string;
  settings: SettingItem[];
}

interface SettingItem {
  id: string;
  label: string;
  description: string;
  type: "toggle" | "input" | "select" | "button";
  value?: any;
  options?: { label: string; value: string }[];
}

const settingSections: SettingSection[] = [
  {
    id: "api",
    title: "API Configuration",
    icon: Key,
    description: "Configure your AI provider API keys",
    settings: [
      { id: "openai_key", label: "OpenAI API Key", description: "Used for GPT-4o and embeddings", type: "input", value: "" },
      { id: "anthropic_key", label: "Anthropic API Key", description: "Used for Claude models", type: "input", value: "" },
      { id: "google_key", label: "Google AI Key", description: "Used for Gemini models", type: "input", value: "" },
    ],
  },
  {
    id: "model",
    title: "Model Preferences",
    icon: Globe,
    description: "Choose which AI models to use",
    settings: [
      { id: "primary_provider", label: "Primary Provider", description: "Default AI provider", type: "select", value: "openai", options: [
        { label: "OpenAI (GPT-4o)", value: "openai" },
        { label: "Anthropic (Claude)", value: "anthropic" },
        { label: "Google (Gemini)", value: "google" },
        { label: "Local (Ollama)", value: "ollama" },
      ]},
      { id: "vision_model", label: "Vision Model", description: "Model for image analysis", type: "select", value: "gpt-4o", options: [
        { label: "GPT-4o", value: "gpt-4o" },
        { label: "Claude 3 Opus", value: "claude-3-opus" },
        { label: "Gemini Pro Vision", value: "gemini-1.5-pro" },
      ]},
    ],
  },
  {
    id: "appearance",
    title: "Appearance",
    icon: Palette,
    description: "Customize the look and feel",
    settings: [
      { id: "theme", label: "Theme", description: "Color scheme", type: "select", value: "light", options: [
        { label: "Light", value: "light" },
        { label: "Dark", value: "dark" },
        { label: "System", value: "system" },
      ]},
    ],
  },
  {
    id: "android",
    title: "Android Integration",
    icon: Smartphone,
    description: "Configure device automation settings",
    settings: [
      { id: "auto_connect", label: "Auto-connect to device", description: "Automatically connect to paired Android device", type: "toggle", value: true },
      { id: "voice_wake", label: "Wake word detection", description: "Enable 'Hey Jarvis' wake word", type: "toggle", value: true },
      { id: "screen_capture", label: "Screen capture permission", description: "Allow screen capture for vision tasks", type: "toggle", value: false },
    ],
  },
  {
    id: "privacy",
    title: "Privacy & Security",
    icon: Shield,
    description: "Control data and privacy settings",
    settings: [
      { id: "memory_enabled", label: "Memory enabled", description: "Allow Jarvis to remember conversations", type: "toggle", value: true },
      { id: "analytics", label: "Usage analytics", description: "Help improve Jarvis with usage data", type: "toggle", value: false },
      { id: "clear_memory", label: "Clear all memory", description: "Delete all stored memories and history", type: "button" },
    ],
  },
];

function ToggleSwitch({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={cn(
        "w-11 h-6 rounded-full transition-colors relative flex-shrink-0 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2",
        checked ? "bg-primary" : "bg-[#E5E0D8]"
      )}
    >
      <div className={cn(
        "w-5 h-5 bg-white rounded-full shadow-sm absolute top-0.5 transition-transform",
        checked ? "translate-x-[22px]" : "translate-x-0.5"
      )} />
    </button>
  );
}

export default function SettingsPage() {
  const [activeSection, setActiveSection] = useState(settingSections[0].id);
  const [saved, setSaved] = useState(false);
  const [showKeys, setShowKeys] = useState<Record<string, boolean>>({});
  const [values, setValues] = useState<Record<string, string | boolean>>(() => {
    const stored = localStorage.getItem("jarvis_settings");
    const parsed = stored ? JSON.parse(stored) : {};
    return {
      ...parsed,
      openai_key: sessionStorage.getItem("jarvis_api_key_openai") || "",
      anthropic_key: sessionStorage.getItem("jarvis_api_key_anthropic") || "",
      google_key: sessionStorage.getItem("jarvis_api_key_google") || "",
      primary_provider: parsed.primary_provider || "openai",
      vision_model: parsed.vision_model || "gpt-4o",
      theme: parsed.theme || "light",
      auto_connect: parsed.auto_connect ?? true,
      voice_wake: parsed.voice_wake ?? true,
      screen_capture: parsed.screen_capture ?? false,
      memory_enabled: parsed.memory_enabled ?? true,
      analytics: parsed.analytics ?? false,
    };
  });

  const handleSave = () => {
    try {
      const safeValues = { ...values };
      delete safeValues.openai_key;
      delete safeValues.anthropic_key;
      delete safeValues.google_key;
      localStorage.setItem("jarvis_settings", JSON.stringify(safeValues));

      sessionStorage.setItem("jarvis_api_key_openai", values.openai_key);
      sessionStorage.setItem("jarvis_api_key_anthropic", values.anthropic_key);
      sessionStorage.setItem("jarvis_api_key_google", values.google_key);
    } catch {
      // sessionStorage may not be available in some environments
    }
    if (values.theme === "dark") {
      document.documentElement.classList.add("dark");
    } else if (values.theme === "light") {
      document.documentElement.classList.remove("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const updateValue = (id: string, val: any) => {
    setValues((prev) => ({ ...prev, [id]: val }));
  };

  return (
    <div className="min-h-screen bg-background p-6 pb-24">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-card flex items-center justify-center border border-border">
              <Settings className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-foreground">Settings</h1>
              <p className="text-sm text-muted-foreground">Configure Jarvis AI OS</p>
            </div>
          </div>
          <button
            onClick={handleSave}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-xl text-sm font-medium hover:opacity-90 transition-all shadow-sm"
          >
            {saved ? (
              <>
                <CheckCircle className="w-4 h-4" />
                Saved
              </>
            ) : (
              <>
                <Save className="w-4 h-4" />
                Save
              </>
            )}
          </button>
        </div>

        <div className="flex gap-6">
          <div className="w-56 flex-shrink-0">
            <div className="space-y-1 sticky top-6">
              {settingSections.map((section) => {
                const Icon = section.icon;
                return (
                  <button
                    key={section.id}
                    onClick={() => setActiveSection(section.id)}
                    className={cn(
                      "w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm transition-all text-left",
                      activeSection === section.id
                        ? "bg-card text-primary font-medium border border-border"
                        : "text-muted-foreground hover:bg-card/50"
                    )}
                  >
                    <Icon className="w-4 h-4" />
                    <span>{section.title}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex-1">
            {settingSections.map((section) => {
              if (section.id !== activeSection) return null;
              const SectionIcon = section.icon;

              return (
                <motion.div
                  key={section.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                >
                  <div className="flex items-center gap-3 mb-6">
                    <div className="w-10 h-10 rounded-xl bg-card flex items-center justify-center border border-border">
                      <SectionIcon className="w-5 h-5 text-primary" />
                    </div>
                    <div>
                      <h2 className="text-lg font-semibold text-foreground">{section.title}</h2>
                      <p className="text-sm text-muted-foreground">{section.description}</p>
                    </div>
                  </div>

                  <div className="space-y-4">
                    {section.settings.map((setting) => (
                      <div
                        key={setting.id}
                        className="p-4 rounded-2xl bg-card border border-border hover:border-[#FF8425]/30 transition-colors"
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex-1">
                            <label className="text-sm font-medium text-foreground">{setting.label}</label>
                            <p className="text-xs text-muted-foreground mt-0.5">{setting.description}</p>
                          </div>

                          {setting.type === "toggle" && (
                            <ToggleSwitch
                              checked={values[setting.id] ?? setting.value}
                              onChange={(v) => updateValue(setting.id, v)}
                            />
                          )}

                          {setting.type === "input" && (
                            <div className="relative">
                              <input
                                type={showKeys[setting.id] ? "text" : "password"}
                                value={values[setting.id] || ""}
                                onChange={(e) => updateValue(setting.id, e.target.value)}
                                placeholder="sk-..."
                                className="w-48 pr-8 px-3 py-2 text-xs bg-muted rounded-xl border border-border outline-none focus:border-primary transition-colors placeholder:text-muted-foreground/60"
                              />
                              <button
                                type="button"
                                onClick={() => setShowKeys((prev) => ({ ...prev, [setting.id]: !prev[setting.id] }))}
                                className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                                aria-label={showKeys[setting.id] ? "Hide API key" : "Show API key"}
                              >
                                {showKeys[setting.id] ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                              </button>
                            </div>
                          )}

                          {setting.type === "select" && (
                            <select
                              value={values[setting.id] || setting.value}
                              onChange={(e) => updateValue(setting.id, e.target.value)}
                              className="px-3 py-2 text-xs bg-muted rounded-xl border border-border outline-none focus:border-primary transition-colors text-foreground"
                            >
                              {setting.options?.map((opt) => (
                                <option key={opt.value} value={opt.value}>{opt.label}</option>
                              ))}
                            </select>
                          )}

                          {setting.type === "button" && (
                            <button
                              onClick={() => {
                                fetch(`/api/memories`, { method: "DELETE" }).catch(() => {});
                                setValues((prev) => ({ ...prev, memory_enabled: true }));
                              }}
                              className="px-3 py-1.5 text-xs font-medium text-destructive bg-destructive/10 rounded-xl hover:bg-destructive/20 transition-colors"
                            >
                              Clear
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </motion.div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
