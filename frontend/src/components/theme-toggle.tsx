"use client";

import { useSyncExternalStore } from "react";

type ThemeMode = "light" | "dark";

const STORAGE_KEY = "skyline-theme";
const EVENT_NAME = "skyline-theme-change";

function resolveCurrentTheme(): ThemeMode {
  if (typeof document === "undefined") return "light";
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "dark" || attr === "light") return attr;
  if (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-color-scheme: dark)").matches
  ) {
    return "dark";
  }
  return "light";
}

function subscribeTheme(onStoreChange: () => void) {
  if (typeof window === "undefined") return () => {};
  const media = window.matchMedia("(prefers-color-scheme: dark)");
  window.addEventListener(EVENT_NAME, onStoreChange);
  window.addEventListener("storage", onStoreChange);
  media.addEventListener("change", onStoreChange);
  return () => {
    window.removeEventListener(EVENT_NAME, onStoreChange);
    window.removeEventListener("storage", onStoreChange);
    media.removeEventListener("change", onStoreChange);
  };
}

function applyTheme(mode: ThemeMode) {
  const root = document.documentElement;
  root.setAttribute("data-theme", mode);
  root.style.colorScheme = mode;
  try {
    localStorage.setItem(STORAGE_KEY, mode);
  } catch {
    // Ignore storage errors in private mode
  }
  window.dispatchEvent(new CustomEvent(EVENT_NAME, { detail: mode }));
}

export function ThemeToggle({
  className = "",
  showLabel = false,
}: {
  className?: string;
  showLabel?: boolean;
}) {
  const theme = useSyncExternalStore(
    subscribeTheme,
    resolveCurrentTheme,
    (): ThemeMode => "light",
  );

  const isDark = theme === "dark";

  const toggle = () => {
    const next: ThemeMode = resolveCurrentTheme() === "dark" ? "light" : "dark";
    applyTheme(next);
  };

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={
        isDark ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"
      }
      title={
        isDark
          ? "Đang bật chế độ tối, bấm để đổi sang sáng"
          : "Đang bật chế độ sáng, bấm để đổi sang tối"
      }
      className={
        className ||
        "inline-flex h-36 items-center gap-7 rounded-inner border border-line-strong bg-surface px-11 text-caption font-semibold text-ink transition hover:border-ink hover:bg-accent-soft active:scale-[0.98]"
      }
    >
      {isDark ? (
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="4.5" />
          <path d="M12 2v2.5M12 19.5V22M4.93 4.93l1.77 1.77M17.3 17.3l1.77 1.77M2 12h2.5M19.5 12H22M4.93 19.07l1.77-1.77M17.3 6.7l1.77-1.77" />
        </svg>
      ) : (
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M21 12.79A9 9 0 1 1 11.21 3a7 7 0 0 0 9.79 9.79Z" />
        </svg>
      )}
      {showLabel && <span>{isDark ? "Sáng" : "Tối"}</span>}
    </button>
  );
}

