// NFR-01: đo p95 thời gian phản hồi trên dữ liệu seed. Chạy khi backend (profile dev) đang chạy:
//   node scripts/perf.mjs            (mặc định http://localhost:8080, 200 lượt mỗi API)
//   BASE_URL=http://localhost:3000 RUNS=50 node scripts/perf.mjs
// Ngưỡng: tìm kiếm dưới 1000 ms, API khác dưới 500 ms. Có API vượt ngưỡng thì thoát mã 1.
const BASE = process.env.BASE_URL ?? "http://localhost:8080";
const RUNS = Number(process.env.RUNS ?? 200);

const day = (n) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(
    Date.now() + n * 86_400_000,
  );

let cookies = {};
const cookieHeader = () =>
  Object.entries(cookies)
    .map(([k, v]) => `${k}=${v}`)
    .join("; ");
async function call(path, init = {}) {
  const res = await fetch(BASE + path, {
    ...init,
    redirect: "manual",
    headers: {
      cookie: cookieHeader(),
      "X-XSRF-TOKEN": cookies["XSRF-TOKEN"] ?? "",
      ...init.headers,
    },
  });
  for (const c of res.headers.getSetCookie()) {
    const [kv] = c.split(";");
    const i = kv.indexOf("=");
    cookies[kv.slice(0, i)] = kv.slice(i + 1);
  }
  await res.arrayBuffer();
  if (!res.ok)
    throw new Error(`${init.method ?? "GET"} ${path}: ${res.status}`);
  return res;
}
const json = (method, path, body) =>
  call(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

await call("/api/auth/csrf");
await json("POST", "/api/auth/login", {
  email: "customer@demo.local",
  password: "Demo@1234",
});
await call("/api/auth/csrf"); // token mới sau khi đăng nhập

const search = (from, to) =>
  `/api/flights/search?from=${from}&to=${to}&date=${day(7)}&adults=2&children=1&infants=1&cabin=ECONOMY`;
const flights = await (await fetch(BASE + search("HAN", "SGN"))).json();
const it = flights.itineraries[0];
const quote = {
  journeys: [
    {
      direction: "OUTBOUND",
      flightIds: it.segments.map((s) => s.flightId),
      fareFamilyId: it.fareOptions[0].fareFamilyId,
    },
  ],
  passengers: [
    {
      type: "ADULT",
      lastName: "NGUYEN",
      firstName: "VAN AN",
      gender: "MALE",
      dateOfBirth: "1990-01-01",
    },
  ],
  baggage: [],
  contact: {
    name: "Nguyễn Văn An",
    email: "customer@demo.local",
    phone: "0912345678",
  },
  voucherCode: "",
};

const cases = [
  ["GET /api/flights/search (HAN→SGN)", 1000, () => call(search("HAN", "SGN"))],
  [
    "GET /api/flights/search (HAN→PQC, nối chuyến)",
    1000,
    () => call(search("HAN", "PQC")),
  ],
  ["GET /api/airports", 500, () => call("/api/airports")],
  [
    "POST /api/bookings/quote",
    500,
    () => json("POST", "/api/bookings/quote", quote),
  ],
  ["GET /api/bookings", 500, () => call("/api/bookings")],
  ["GET /api/me", 500, () => call("/api/me")],
];

let failed = false;
console.log(`${BASE}, ${RUNS} lượt mỗi API`);
for (const [name, limit, run] of cases) {
  for (let i = 0; i < 10; i++) await run(); // làm nóng JIT và connection pool
  const ms = [];
  for (let i = 0; i < RUNS; i++) {
    const t = performance.now();
    await run();
    ms.push(performance.now() - t);
  }
  ms.sort((a, b) => a - b);
  const p = (q) =>
    ms[Math.min(ms.length - 1, Math.ceil(q * ms.length) - 1)].toFixed(1);
  const ok = Number(p(0.95)) < limit;
  failed ||= !ok;
  console.log(
    `${ok ? "OK  " : "VƯỢT"} ${name}: p50 ${p(0.5)} ms, p95 ${p(0.95)} ms (ngưỡng ${limit} ms)`,
  );
}
process.exit(failed ? 1 : 0);
