"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { AdminPage } from "../../admin-ui";
import {
  Field,
  FormSkeleton,
  Notice,
  SelectField,
  pillPrimary,
  pillLine,
  textLink,
} from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { useAirports } from "@/lib/airports";
import { errorText, WEEKDAY_OPTIONS } from "@/lib/admin";
import { cabinLabel } from "@/lib/staff";
import { useApi } from "@/lib/use-api";

type CreatedFlights = Schemas["CreatedFlights"];
type FareFamily = Schemas["FareFamilyResponse"];
type NewFlights = Schemas["NewFlights"];

/** Thứ 2 (1) … Chủ nhật (7) của WEEKDAY_OPTIONS sang DayOfWeek của backend. */
const DAY_OF_WEEK: NewFlights["daysOfWeek"] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

export default function FlightCreateForm() {
  const { airports, failed: airportsFailed } = useAirports();
  const airlinesRes = useApi<Schemas["AirlineResponse"][]>("/airlines");

  // Chưa chọn thì lấy hãng đầu tiên của danh sách.
  const [chosenAirline, setSelectedAirline] = useState<string>("");
  const selectedAirline =
    chosenAirline ||
    (airlinesRes && "data" in airlinesRes
      ? (airlinesRes.data[0]?.code ?? "")
      : "");
  const faresRes = useApi<FareFamily[]>(
    selectedAirline ? `/admin/airlines/${selectedAirline}/fare-families` : null,
  );
  const loadingFares = faresRes === undefined;
  const fareFamilies =
    faresRes && "data" in faresRes ? faresRes.data.filter((f) => f.active) : [];

  // Chế độ tạo: 'single' (lẻ 1 ngày) hoặc 'recurring' (hàng loạt nhiều ngày)
  const [mode, setMode] = useState<"single" | "recurring">("single");
  const [selectedDays, setSelectedDays] = useState<number[]>([
    1, 2, 3, 4, 5, 6, 7,
  ]);

  const [createdResult, setCreatedResult] = useState<CreatedFlights | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const toggleDay = (day: number) => {
    setSelectedDays((prev) =>
      prev.includes(day)
        ? prev.filter((d) => d !== day)
        : [...prev, day].sort(),
    );
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    const fd = new FormData(e.currentTarget);
    const flightNumber = (fd.get("flightNumber") as string)
      ?.toUpperCase()
      .trim();
    const departureAirport = (fd.get("departureAirport") as string)?.trim();
    const arrivalAirport = (fd.get("arrivalAirport") as string)?.trim();
    const departureTime = (fd.get("departureTime") as string)?.trim();
    const durationMinutes = Number(fd.get("durationMinutes"));
    const startDate = (fd.get("startDate") as string)?.trim();
    const endDate =
      mode === "single" ? startDate : (fd.get("endDate") as string)?.trim();
    const aircraftModel = (fd.get("aircraftModel") as string)?.trim();

    if (departureAirport === arrivalAirport) {
      setError("Sân bay đi và đến không được trùng nhau.");
      setSubmitting(false);
      return;
    }

    if (mode === "recurring" && selectedDays.length === 0) {
      setError(
        "Vui lòng chọn ít nhất một thứ trong tuần để tạo chuyến định kỳ.",
      );
      setSubmitting(false);
      return;
    }

    // Ghế theo hạng: ít nhất 1 hạng có ghế > 0
    const cabins = [
      {
        cabinClass: "ECONOMY" as const,
        totalSeats: Number(fd.get("cabin_ECONOMY") || 0),
      },
      {
        cabinClass: "PREMIUM_ECONOMY" as const,
        totalSeats: Number(fd.get("cabin_PREMIUM_ECONOMY") || 0),
      },
      {
        cabinClass: "BUSINESS" as const,
        totalSeats: Number(fd.get("cabin_BUSINESS") || 0),
      },
      {
        cabinClass: "FIRST" as const,
        totalSeats: Number(fd.get("cabin_FIRST") || 0),
      },
    ].filter((c) => c.totalSeats > 0);

    if (cabins.length === 0) {
      setError(
        "Mỗi chuyến bay phải có ít nhất một hạng ghế với số ghế lớn hơn 0.",
      );
      setSubmitting(false);
      return;
    }

    // Giá theo gói của các hạng ghế đã mở
    const cabinClasses = new Set(cabins.map((c) => c.cabinClass));
    const fares = fareFamilies
      .filter((f) => cabinClasses.has(f.cabinClass))
      .map((f) => ({
        fareFamilyId: f.id,
        price: Number(fd.get(`fare_${f.id}`) || 0),
      }))
      .filter((f) => f.price > 0);

    if (fares.length === 0) {
      setError(
        "Vui lòng nhập giá bán cho các gói giá thuộc các hạng ghế đã cấu hình.",
      );
      setSubmitting(false);
      return;
    }

    // Payload NewFlights theo TDD §8.7
    const payload: NewFlights = {
      airlineCode: selectedAirline,
      flightNumber,
      departureAirport,
      arrivalAirport,
      departureTime,
      durationMinutes,
      fromDate: startDate,
      toDate: endDate,
      // Chuyến lẻ: khoảng một ngày, mọi thứ trong tuần (backend bắt buộc daysOfWeek khác rỗng).
      daysOfWeek:
        mode === "single"
          ? DAY_OF_WEEK
          : selectedDays.map((d) => DAY_OF_WEEK[d - 1]),
      aircraftModel,
      cabins,
      fares,
    };

    try {
      const res = await apiFetch<CreatedFlights>("/admin/flights", {
        method: "POST",
        body: payload,
      });
      setCreatedResult(res);
    } catch (err: unknown) {
      setError(errorText(err as ApiError) || "Không thể tạo chuyến bay.");
    } finally {
      setSubmitting(false);
    }
  };

  if (airportsFailed || (airlinesRes && "error" in airlinesRes)) {
    return (
      <AdminPage>
        <Notice>
          Không tải được danh sách sân bay hoặc hãng bay. Hãy tải lại trang.
        </Notice>
      </AdminPage>
    );
  }
  if (!airports || !airlinesRes) {
    return (
      <AdminPage>
        <FormSkeleton rows={4} />
      </AdminPage>
    );
  }

  return (
    <AdminPage>
      <div className="flex items-center gap-16">
        <Link
          href="/admin/flights"
          className={`${textLink} text-caption text-muted hover:text-ink`}
        >
          ← Danh sách chuyến bay
        </Link>
      </div>

      <div>
        <h1 className="text-heading-lg font-bold">Tạo chuyến bay mới.</h1>
        <p className="text-caption text-muted mt-4">
          Tạo một chuyến lẻ hoặc phát hành lịch bay định kỳ hàng loạt (FR-90,
          BR-85).
        </p>
      </div>

      {error && <Notice>{error}</Notice>}

      {createdResult ? (
        <div className="card p-16 grid gap-16">
          <h2 className="text-subheading font-bold">Kết quả tạo chuyến bay:</h2>
          <div className="grid gap-8 text-caption">
            <p className="font-bold text-ink">
              ✓ Đã tạo thành công: {createdResult.created} chuyến bay.
            </p>
            {createdResult.skipped > 0 && (
              <p className="text-muted">
                ℹ Đã bỏ qua: {createdResult.skipped} ngày vì bị trùng số hiệu và
                giờ cất cánh (BR-80).
              </p>
            )}
          </div>
          <div className="flex gap-11 mt-11">
            <Link href="/admin/flights" className={pillPrimary}>
              Xem danh sách chuyến bay
            </Link>
            <button
              type="button"
              onClick={() => {
                setCreatedResult(null);
              }}
              className={pillLine}
            >
              Tiếp tục tạo chuyến khác
            </button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="grid gap-16 max-w-720">
          {/* Thông tin cơ bản */}
          <div className="grid gap-16 sm:grid-cols-2">
            <SelectField
              label="Hãng hàng không"
              name="airlineCode"
              value={selectedAirline}
              onChange={(e) => setSelectedAirline(e.target.value)}
            >
              {airlinesRes.data.map((al) => (
                <option key={al.code} value={al.code}>
                  {al.code} - {al.name}
                </option>
              ))}
            </SelectField>
            <Field
              label="Số hiệu chuyến bay"
              name="flightNumber"
              required
              placeholder={`${selectedAirline || "VN"}213`}
            />
          </div>

          <div className="grid gap-16 sm:grid-cols-2">
            <SelectField
              label="Sân bay đi"
              name="departureAirport"
              defaultValue="HAN"
            >
              {airports.map((a) => (
                <option key={a.code} value={a.code}>
                  {a.code} ({a.city}) - {a.name}
                </option>
              ))}
            </SelectField>
            <SelectField
              label="Sân bay đến"
              name="arrivalAirport"
              defaultValue="SGN"
            >
              {airports.map((a) => (
                <option key={a.code} value={a.code}>
                  {a.code} ({a.city}) - {a.name}
                </option>
              ))}
            </SelectField>
          </div>

          <div className="grid gap-16 sm:grid-cols-3">
            <Field
              label="Giờ cất cánh (giờ địa phương)"
              name="departureTime"
              type="time"
              required
              defaultValue="08:00"
            />
            <Field
              label="Thời lượng bay (phút)"
              name="durationMinutes"
              type="number"
              min={15}
              max={1440}
              required
              defaultValue={125}
            />
            <Field
              label="Loại máy bay"
              name="aircraftModel"
              required
              defaultValue="Airbus A321"
              placeholder="Boeing 787"
            />
          </div>

          {/* Lựa chọn Chuyến lẻ hay Định kỳ */}
          <div className="border-t border-line pt-16 grid gap-16">
            <span className="text-caption font-bold">Lịch bay</span>
            <div className="flex gap-16">
              <label className="flex items-center gap-8 text-caption font-bold cursor-pointer">
                <input
                  type="radio"
                  name="flightMode"
                  value="single"
                  checked={mode === "single"}
                  onChange={() => setMode("single")}
                  className="size-18"
                />
                Tạo 1 chuyến lẻ
              </label>
              <label className="flex items-center gap-8 text-caption font-bold cursor-pointer">
                <input
                  type="radio"
                  name="flightMode"
                  value="recurring"
                  checked={mode === "recurring"}
                  onChange={() => setMode("recurring")}
                  className="size-18"
                />
                Tạo lịch định kỳ hàng loạt
              </label>
            </div>

            {mode === "single" ? (
              <Field label="Ngày bay" name="startDate" type="date" required />
            ) : (
              <div className="grid gap-16">
                <div className="grid grid-cols-2 gap-16">
                  <Field
                    label="Từ ngày"
                    name="startDate"
                    type="date"
                    required
                  />
                  <Field
                    label="Đến ngày (tối đa 180 ngày)"
                    name="endDate"
                    type="date"
                    required
                  />
                </div>
                <div>
                  <span className="text-caption text-muted mb-8 block">
                    Chọn các thứ bay trong tuần:
                  </span>
                  <div className="flex flex-wrap gap-11">
                    {WEEKDAY_OPTIONS.map((w) => {
                      const checked = selectedDays.includes(w.day);
                      return (
                        <button
                          key={w.day}
                          type="button"
                          onClick={() => toggleDay(w.day)}
                          className={checked ? pillPrimary : pillLine}
                        >
                          {w.label}
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Cấu hình kho ghế theo hạng */}
          <div className="border-t border-line pt-16 grid gap-16">
            <div>
              <span className="text-caption font-bold">
                Số ghế từng hạng (BR-81)
              </span>
              <p className="text-caption text-muted">
                Nhập số ghế cho các hạng máy bay phục vụ (0 nếu không có).
              </p>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-16">
              <Field
                label="Phổ thông"
                name="cabin_ECONOMY"
                type="number"
                min={0}
                defaultValue={180}
              />
              <Field
                label="Phổ thông ĐB"
                name="cabin_PREMIUM_ECONOMY"
                type="number"
                min={0}
                defaultValue={0}
              />
              <Field
                label="Thương gia"
                name="cabin_BUSINESS"
                type="number"
                min={0}
                defaultValue={16}
              />
              <Field
                label="Hạng nhất"
                name="cabin_FIRST"
                type="number"
                min={0}
                defaultValue={0}
              />
            </div>
          </div>

          {/* Cấu hình giá bán cho các gói giá của hãng */}
          <div className="border-t border-line pt-16 grid gap-16">
            <div>
              <span className="text-caption font-bold">
                Giá vé cơ sở từng gói giá ({selectedAirline})
              </span>
              <p className="text-caption text-muted">
                Giá vé người lớn một chiều (chưa gồm hành lý mua thêm).
              </p>
            </div>
            {loadingFares ? (
              <FormSkeleton rows={2} />
            ) : fareFamilies.length === 0 ? (
              <p className="text-muted">
                Hãng {selectedAirline} chưa có gói giá đang hoạt động. Vui lòng
                vào trang Hãng bay để tạo gói giá trước.
              </p>
            ) : (
              <div className="grid gap-11 sm:grid-cols-2">
                {fareFamilies.map((ff) => (
                  <Field
                    key={ff.id}
                    label={`${ff.name} (${cabinLabel(ff.cabinClass)})`}
                    name={`fare_${ff.id}`}
                    type="number"
                    min={0}
                    step={10000}
                    defaultValue={1500000}
                    placeholder="1500000"
                  />
                ))}
              </div>
            )}
          </div>

          <div className="flex justify-end gap-11 border-t border-line pt-16">
            <Link href="/admin/flights" className={pillLine}>
              Huỷ bỏ
            </Link>
            <button
              type="submit"
              disabled={submitting || fareFamilies.length === 0}
              className={pillPrimary}
            >
              {submitting ? "Đang tạo chuyến..." : "Phát hành chuyến bay"}
            </button>
          </div>
        </form>
      )}
    </AdminPage>
  );
}
