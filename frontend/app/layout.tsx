import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Navigation } from "@/components/Navigation";
import { WebSocketProvider } from "@/hooks/WebSocketContext";
import { ErrorBoundary } from "@/components/ErrorBoundary";

const inter = Inter({ subsets: ["latin"] });

export const viewport: Viewport = {
  themeColor: "#F86607",
};

export const metadata: Metadata = {
  title: "Jarvis AI OS — Personal Intelligence",
  description: "A persistent AI operating system assistant with memory, voice, and workflows.",
  icons: {
    icon: [
      { url: "/favicon-16x16.png", sizes: "16x16", type: "image/png" },
      { url: "/favicon-32x32.png", sizes: "32x32", type: "image/png" },
    ],
    apple: { url: "/apple-touch-icon.png", sizes: "180x180" },
  },
  manifest: "/site.webmanifest",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "Jarvis AI OS",
  },
  openGraph: {
    title: "Jarvis AI OS — Personal Intelligence",
    description: "A persistent AI operating system assistant with memory, voice, and workflows.",
    siteName: "Jarvis AI OS",
    type: "website",
    images: [{ url: "/android-chrome-512x512.png", width: 512, height: 512 }],
  },
  twitter: {
    card: "summary_large_image",
    title: "Jarvis AI OS — Personal Intelligence",
    description: "A persistent AI operating system assistant with memory, voice, and workflows.",
    images: ["/android-chrome-512x512.png"],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{
          __html: `
            try {
              var t = localStorage.getItem("jarvis_settings");
              if (t) { var s = JSON.parse(t); if (s.theme === "dark") { document.documentElement.classList.add("dark"); } }
            } catch(e) {}
          `
        }} />
      </head>
      <body className={`${inter.className} antialiased bg-background text-foreground`}>
        <WebSocketProvider>
          <ErrorBoundary>
            {children}
          </ErrorBoundary>
          <Navigation />
        </WebSocketProvider>
      </body>
    </html>
  );
}
