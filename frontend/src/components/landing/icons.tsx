const PATHS = {
  menu: ["M4 6l16 0", "M4 12l16 0", "M4 18l16 0"],
  close: ["M18 6l-12 12", "M6 6l12 12"],
  users: [
    "M9 7m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
    "M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2",
    "M16 3.13a4 4 0 0 1 0 7.75",
    "M21 21v-2a4 4 0 0 0 -3 -3.85",
  ],
  chev: ["M6 9l6 6l6 -6"],
  info: ["M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 9h.01", "M11 12h1v4h1"],
  alert: ["M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 8v4", "M12 16h.01"],
  search: ["M10 10m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M21 21l-6 -6"],
  swap: ["M21 7l-18 0", "M18 10l3 -3l-3 -3", "M6 20l-3 -3l3 -3", "M3 17l18 0"],
  left: ["M15 6l-6 6l6 6"],
  right: ["M9 6l6 6l-6 6"],
  plus: ["M12 5l0 14", "M5 12l14 0"],
  minus: ["M5 12l14 0"],
  check: ["M5 12l5 5l10 -10"],
  arrow: ["M5 12l14 0", "M13 18l6 -6", "M13 6l6 6"],
  upright: ["M17 7l-10 10", "M8 7l9 0l0 9"],
  mail: [
    "M11 19h-6a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v6",
    "M3 7l9 6l9 -6",
    "M15 19l2 2l4 -4",
  ],
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({ name, className = "" }: { name: IconName; className?: string }) {
  return (
    <svg className={`ic ${className}`.trim()} viewBox="0 0 24 24" aria-hidden="true">
      {PATHS[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}

const JET_BODY = "M45 40L12 60V67L45 58ZM55 40L88 60V67L55 58ZM45 70L32 80V85L45 81ZM55 70L68 80V85L55 81Z";

/** Biểu tượng máy bay (mũi hướng sang phải), dùng làm ký hiệu chuyến bay. */
export function Jet() {
  return (
    <svg className="glyph" viewBox="0 0 100 100">
      <g transform="rotate(90 50 50)">
        <rect x="45" y="14" width="10" height="70" rx="5" />
        <path d={JET_BODY} />
      </g>
    </svg>
  );
}

export function Logo() {
  return (
    <svg className="logo-svg" viewBox="0 0 160 160" aria-hidden="true">
      <rect width="160" height="160" rx="36" fill="#000d10" />
      <g transform="translate(20 20) scale(1.2)">
        <circle cx="80" cy="20" r="7" fill="#bc7155" />
        <g transform="matrix(0.671751 0.671751 -0.671751 0.671751 46 -13.1751)" fill="#ffffff">
          <rect x="45" y="14" width="10" height="70" rx="5" />
          <path d={JET_BODY} />
        </g>
      </g>
    </svg>
  );
}
