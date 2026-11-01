"use client";

import Link from "next/link";
import StatusBadge from "@/components/status-badge";
import { useParams, useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import {
  AdminModal,
  AdminPage,
  ConfirmModal,
  tableBox,
  td,
  th,
} from "../../admin-ui";
import {
  Field,
  FormSkeleton,
  Notice,
  pillDanger,
  pillPrimary,
  pillLine,
  textLink,
} from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { vnd } from "@/app/flights/itineraries";
import { errorText } from "@/lib/admin";
import { clock, dmy, useNow } from "@/lib/booking";
import { cabinLabel, FLIGHT_STATUS } from "@/lib/staff";
import { useApi } from "@/lib/use-api";

type Detail = Schemas["FlightDetail"];

export default function FlightDetailView() {
  const id = Number(useParams<{ id: string }>().id);
  const router = useRouter();
  const [version, setVersion] = useState(0);
  const detailRes = useApi<Detail>(`/admin/flights/${id}`, version);

  const [editOpen, setEditOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // BR-87: chuyến đã cất cánh không sửa, không huỷ được. Giờ chỉ đọc sau khi hydrate (server: null).
  const now = useNow();
  const reload = () => setVersion((v) => v + 1);

  const handleUpdate = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!detailRes || !("data" in detailRes)) return;
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const departureTime = (fd.get("departureTime") as string)?.trim();
    const arrivalTime = (fd.get("arrivalTime") as string)?.trim();
    const aircraftModel = (fd.get("aircraftModel") as string)?.trim();

    const cabins = detailRes.data.flight.cabins.map((c) => ({
      cabinClass: c.cabinClass,
      totalSeats: Number(fd.get(`cabin_${c.cabinClass}`)),
    }));

    const fares = detailRes.data.fares.map((f) => ({
      fareFamilyId: f.fareFamilyId,
      price: Number(fd.get(`fare_${f.fareFamilyId}`)),
    }));

    try {
      await apiFetch(`/admin/flights/${id}`, {
        method: "PUT",
        body: {
          departureTime,
          arrivalTime,
          aircraftModel,
          cabins,
          fares,
        } satisfies Schemas["FlightChanges"],
      });
      setEditOpen(false);
      reload();
    } catch (err: unknown) {
      setActionError(
        errorText(err as ApiError) || "Không thể cập nhật chuyến bay.",
      );
    } finally {
      setLoading(false);
    }
  };

  const handleCancelFlight = async () => {
    setActionError(null);
    setLoading(true);
    try {
      await apiFetch(`/admin/flights/${id}/cancel`, { method: "POST" });
      setCancelOpen(false);
      reload();
    } catch (err: unknown) {
      setActionError(errorText(err as ApiError) || "Không thể huỷ chuyến bay.");
      setCancelOpen(false);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteFlight = async () => {
    setActionError(null);
    setLoading(true);
    try {
      await apiFetch(`/admin/flights/${id}`, { method: "DELETE" });
      router.push("/admin/flights");
    } catch (err: unknown) {
      setActionError(errorText(err as ApiError) || "Không thể xoá chuyến bay.");
      setDeleteOpen(false);
    } finally {
      setLoading(false);
    }
  };

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

      {actionError && !editOpen && <Notice>{actionError}</Notice>}

      {detailRes === undefined ? (
        <FormSkeleton rows={4} />
      ) : "error" in detailRes ? (
        <>
          <h1 className="text-heading-lg font-bold">
            {detailRes.error.status === 404
              ? "Không tìm thấy chuyến bay."
              : "Chi tiết chuyến bay."}
          </h1>
          {detailRes.error.status !== 404 && (
            <Notice>{detailRes.error.detail}</Notice>
          )}
        </>
      ) : (
        <>
          {/* Header & Thao tác */}
          <div className="flex flex-wrap items-center justify-between gap-16 border-b border-line pb-16">
            <div>
              <span className="text-caption font-bold text-muted">
                {detailRes.data.flight.departureAirport} →{" "}
                {detailRes.data.flight.arrivalAirport}
              </span>
              <h1 className="text-heading-lg font-bold">
                Chuyến bay {detailRes.data.flight.flightNumber}.
              </h1>
            </div>
            <div className="flex flex-wrap gap-11">
              <Link
                href={`/staff/flights/${id}/passengers`}
                className={pillLine}
              >
                Danh sách hành khách (Manifest)
              </Link>
              {detailRes.data.flight.status === "SCHEDULED" &&
                now !== null &&
                Date.parse(detailRes.data.flight.departureTime) > now && (
                  <>
                    <button
                      type="button"
                      onClick={() => {
                        setActionError(null);
                        setEditOpen(true);
                      }}
                      className={pillLine}
                    >
                      Sửa chuyến bay
                    </button>
                    <button
                      type="button"
                      onClick={() => setCancelOpen(true)}
                      className={pillDanger}
                    >
                      Huỷ chuyến
                    </button>
                  </>
                )}
              {detailRes.data.bookingCount === 0 && (
                <button
                  type="button"
                  onClick={() => setDeleteOpen(true)}
                  className={`${pillLine} text-muted border-line hover:border-ink hover:text-ink`}
                >
                  Xoá chuyến
                </button>
              )}
            </div>
          </div>

          {/* Thông tin hành trình & thời gian */}
          <div className="grid gap-16 sm:grid-cols-2 lg:grid-cols-4">
            <div className="card p-16">
              <span className="text-caption text-muted">Trạng thái</span>
              <p className="mt-4">
                <StatusBadge
                  large
                  status={detailRes.data.flight.status}
                  label={
                    FLIGHT_STATUS[detailRes.data.flight.status] ??
                    detailRes.data.flight.status
                  }
                />
              </p>
            </div>
            <div className="card p-16">
              <span className="text-caption text-muted">
                Cất cánh ({detailRes.data.flight.departureAirport})
              </span>
              <p className="text-subheading font-bold mt-4">
                {clock(detailRes.data.flight.departureTime)}
              </p>
              <p className="text-caption text-muted">
                {dmy(detailRes.data.flight.departureTime)}
              </p>
            </div>
            <div className="card p-16">
              <span className="text-caption text-muted">
                Hạ cánh ({detailRes.data.flight.arrivalAirport})
              </span>
              <p className="text-subheading font-bold mt-4">
                {clock(detailRes.data.flight.arrivalTime)}
              </p>
              <p className="text-caption text-muted">
                {dmy(detailRes.data.flight.arrivalTime)}
              </p>
            </div>
            <div className="card p-16">
              <span className="text-caption text-muted">Số lượt đặt chỗ</span>
              <p className="text-subheading font-bold mt-4">
                {detailRes.data.bookingCount} đơn
              </p>
              <p className="text-caption text-muted">
                Loại máy bay: {detailRes.data.flight.aircraftModel}
              </p>
            </div>
          </div>

          {/* Kho ghế theo hạng */}
          <section className="grid gap-16">
            <h2 className="text-subheading font-bold">Kho ghế theo hạng</h2>
            <div className={tableBox}>
              <table aria-label="Kho ghế" className="w-full border-collapse">
                <thead>
                  <tr>
                    <th className={th}>Hạng ghế</th>
                    <th className={th}>Tổng số ghế</th>
                    <th className={th}>Ghế còn bán</th>
                    <th className={th}>Tỉ lệ đã lấp</th>
                  </tr>
                </thead>
                <tbody>
                  {detailRes.data.flight.cabins.map((c) => {
                    const booked = c.totalSeats - c.availableSeats;
                    const percent =
                      c.totalSeats > 0
                        ? ((booked / c.totalSeats) * 100).toFixed(1)
                        : "0.0";
                    return (
                      <tr key={c.cabinClass}>
                        <td className={`${td} font-bold`}>
                          {cabinLabel(c.cabinClass)}
                        </td>
                        <td className={td}>{c.totalSeats} ghế</td>
                        <td className={td}>{c.availableSeats} ghế</td>
                        <td className={td}>
                          {booked}/{c.totalSeats} ({percent}%)
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>

          {/* Bảng giá theo gói giá */}
          <section className="grid gap-16">
            <h2 className="text-subheading font-bold">Giá vé theo gói</h2>
            <div className={tableBox}>
              <table
                aria-label="Giá vé theo gói"
                className="w-full border-collapse"
              >
                <thead>
                  <tr>
                    <th className={th}>Gói giá</th>
                    <th className={th}>Hạng ghế</th>
                    <th className={th}>Giá vé người lớn</th>
                  </tr>
                </thead>
                <tbody>
                  {detailRes.data.fares.map((f) => (
                    <tr key={f.fareFamilyId}>
                      <td className={`${td} font-bold`}>{f.fareFamilyName}</td>
                      <td className={td}>{cabinLabel(f.cabinClass)}</td>
                      <td className={`${td} font-bold`}>{vnd(f.price)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          {/* Modal Sửa chuyến bay */}
          <AdminModal
            title={`Sửa chuyến bay ${detailRes.data.flight.flightNumber}`}
            isOpen={editOpen}
            onClose={() => setEditOpen(false)}
          >
            <form
              onSubmit={handleUpdate}
              className="grid gap-16 max-h-[75vh] overflow-y-auto pr-8"
            >
              {actionError && <Notice>{actionError}</Notice>}
              <Field
                label="Loại máy bay"
                name="aircraftModel"
                required
                defaultValue={detailRes.data.flight.aircraftModel}
              />
              <div className="grid grid-cols-2 gap-16">
                <Field
                  label="Giờ cất cánh (giờ địa phương)"
                  type="datetime-local"
                  name="departureTime"
                  required
                  defaultValue={detailRes.data.flight.departureTime.slice(
                    0,
                    16,
                  )}
                />
                <Field
                  label="Giờ hạ cánh (giờ địa phương)"
                  type="datetime-local"
                  name="arrivalTime"
                  required
                  defaultValue={detailRes.data.flight.arrivalTime.slice(0, 16)}
                />
              </div>

              <div className="border-t border-line pt-16 grid gap-11">
                <span className="text-caption font-bold">
                  Cập nhật tổng số ghế (BR-82: không giảm dưới số ghế đã bán)
                </span>
                {detailRes.data.flight.cabins.map((c) => {
                  const minSeats = c.totalSeats - c.availableSeats;
                  return (
                    <Field
                      key={c.cabinClass}
                      label={`Tổng ghế ${cabinLabel(c.cabinClass)} (tối thiểu ${minSeats})`}
                      name={`cabin_${c.cabinClass}`}
                      type="number"
                      min={minSeats}
                      required
                      defaultValue={c.totalSeats}
                    />
                  );
                })}
              </div>

              <div className="border-t border-line pt-16 grid gap-11">
                <span className="text-caption font-bold">
                  Cập nhật giá bán theo gói (BR-86)
                </span>
                {detailRes.data.fares.map((f) => (
                  <Field
                    key={f.fareFamilyId}
                    label={`Giá vé ${f.fareFamilyName} (₫)`}
                    name={`fare_${f.fareFamilyId}`}
                    type="number"
                    min={0}
                    step={10000}
                    required
                    defaultValue={f.price}
                  />
                ))}
              </div>

              <div className="flex justify-end gap-11 mt-11">
                <button
                  type="button"
                  onClick={() => setEditOpen(false)}
                  className={pillLine}
                  disabled={loading}
                >
                  Huỷ
                </button>
                <button
                  type="submit"
                  className={pillPrimary}
                  disabled={loading}
                >
                  {loading ? "Đang lưu..." : "Lưu thay đổi"}
                </button>
              </div>
            </form>
          </AdminModal>

          {/* Confirm Modal Huỷ chuyến bay (2 bước theo BR-84) */}
          <ConfirmModal
            title={`Huỷ chuyến bay ${detailRes.data.flight.flightNumber}?`}
            message={`CẢNH BÁO: Huỷ chuyến bay là thao tác không thể hoàn tác (BR-84). Hệ thống sẽ tự động: (1) Huỷ mọi đặt chỗ đang chờ thanh toán; (2) Huỷ các yêu cầu đổi chuyến đang chờ thanh toán; (3) Tạo yêu cầu hoàn 100% cho mọi chiều đã xuất vé trên chuyến; (4) Gửi email thông báo cho hành khách.`}
            confirmLabel="Xác nhận huỷ chuyến"
            isOpen={cancelOpen}
            isLoading={loading}
            onConfirm={handleCancelFlight}
            onClose={() => setCancelOpen(false)}
          />

          {/* Confirm Modal Xoá chuyến bay */}
          <ConfirmModal
            title={`Xoá chuyến bay ${detailRes.data.flight.flightNumber}?`}
            message={`Bạn có chắc muốn xoá chuyến bay này? Chuyến bay chưa có đơn đặt chỗ nào và sẽ bị xoá hoàn toàn khỏi cơ sở dữ liệu.`}
            confirmLabel="Xoá vĩnh viễn"
            isOpen={deleteOpen}
            isLoading={loading}
            onConfirm={handleDeleteFlight}
            onClose={() => setDeleteOpen(false)}
          />
        </>
      )}
    </AdminPage>
  );
}
