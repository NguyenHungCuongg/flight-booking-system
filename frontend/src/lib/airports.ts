import { useEffect, useState } from "react";
import { apiFetch, type Schemas } from "./api";

export type Airport = Schemas["AirportResponse"];

// ponytail: tải cả danh sách một lần (29 sân bay) rồi lọc ở client; lên hàng nghìn sân bay thì gọi GET /airports?q= theo từng lần gõ.
let all: Promise<Airport[]> | undefined;

/** FR-10: sân bay đang hoạt động (GET /airports không có q). `airports` là null khi đang tải. */
export function useAirports() {
  const [airports, setAirports] = useState<Airport[] | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    all ??= apiFetch<Airport[]>("/airports");
    let live = true;
    all.then(
      (list) => live && setAirports(list),
      () => {
        all = undefined; // lần mở trang sau tải lại
        if (live) setFailed(true);
      },
    );
    return () => {
      live = false;
    };
  }, []);
  return { airports, failed };
}

const region = new Intl.DisplayNames(["vi"], {
  type: "region",
  style: "short",
});

/** Tên quốc gia tiếng Việt từ mã ISO, VD "KR" → "Hàn Quốc". */
export const countryName = (code: string) => region.of(code) ?? code;
