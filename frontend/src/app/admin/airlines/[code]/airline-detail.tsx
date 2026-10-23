"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AdminModal, AdminPage, AdminTabs, ConfirmModal, tableBox, td, th } from "../../admin-ui";
import { Field, FormSkeleton, Notice, SelectField, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { vnd } from "@/app/flights/itineraries";
import { errorText } from "@/lib/admin";
import { cabinLabel } from "@/lib/staff";
import { useApi } from "@/lib/use-api";

type FareFamily = Schemas["FareFamilyResponse"];
type BaggageOption = Schemas["BaggageOptionResponse"];

const CABIN_OPTIONS = [
  { value: "ECONOMY", label: "Phổ thông" },
  { value: "PREMIUM_ECONOMY", label: "Phổ thông đặc biệt" },
  { value: "BUSINESS", label: "Thương gia" },
  { value: "FIRST", label: "Hạng nhất" },
];

export default function AirlineDetail() {
  const code = useParams<{ code: string }>().code.toUpperCase();
  const [tab, setTab] = useState<"fares" | "baggage">("fares");
  const [faresVersion, setFaresVersion] = useState(0);
  const [baggageVersion, setBaggageVersion] = useState(0);

  const faresRes = useApi<FareFamily[]>(`/admin/airlines/${code}/fare-families`, faresVersion);
  const baggageRes = useApi<BaggageOption[]>(`/admin/airlines/${code}/baggage-options`, baggageVersion);

  const [fareModal, setFareModal] = useState<{ mode: "create" | "edit"; item?: FareFamily } | null>(null);
  const [baggageModal, setBaggageModal] = useState<{ mode: "create" | "edit"; item?: BaggageOption } | null>(null);

  const [deleteTarget, setDeleteTarget] = useState<{
    type: "fare" | "baggage";
    id: number;
    name: string;
  } | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSaveFare = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const body = {
      name: (fd.get("name") as string)?.trim(),
      cabinClass: ((fd.get("cabinClass") as string) || fareModal?.item?.cabinClass) as FareFamily["cabinClass"],
      carryOnKg: Number(fd.get("carryOnKg")),
      checkedBaggageKg: Number(fd.get("checkedBaggageKg")),
      refundable: fd.get("refundable") === "on",
      refundFee: Number(fd.get("refundFee") || 0),
      changeable: fd.get("changeable") === "on",
      changeFee: Number(fd.get("changeFee") || 0),
      active: fd.get("active") === "on",
    } satisfies Schemas["FareFamilyFields"];

    try {
      if (fareModal?.mode === "create") {
        await apiFetch(`/admin/airlines/${code}/fare-families`, {
          method: "POST",
          body,
        });
      } else if (fareModal?.mode === "edit" && fareModal.item) {
        await apiFetch(`/admin/fare-families/${fareModal.item.id}`, {
          method: "PUT",
          body,
        });
      }
      setFareModal(null);
      setFaresVersion((v) => v + 1);
    } catch (err: unknown) {
      setActionError(errorText(err as ApiError) || "Không thể lưu gói giá.");
    } finally {
      setLoading(false);
    }
  };

  const handleSaveBaggage = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const body = {
      weightKg: Number(fd.get("weightKg")),
      price: Number(fd.get("price")),
      active: fd.get("active") === "on",
    } satisfies Schemas["BaggageOptionRequest"];

    try {
      if (baggageModal?.mode === "create") {
        await apiFetch(`/admin/airlines/${code}/baggage-options`, {
          method: "POST",
          body,
        });
      } else if (baggageModal?.mode === "edit" && baggageModal.item) {
        await apiFetch(`/admin/baggage-options/${baggageModal.item.id}`, {
          method: "PUT",
          body,
        });
      }
      setBaggageModal(null);
      setBaggageVersion((v) => v + 1);
    } catch (err: unknown) {
      setActionError(errorText(err as ApiError) || "Không thể lưu mức hành lý.");
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setActionError(null);
    setLoading(true);
    try {
      if (deleteTarget.type === "fare") {
        await apiFetch(`/admin/fare-families/${deleteTarget.id}`, { method: "DELETE" });
        setFaresVersion((v) => v + 1);
      } else {
        await apiFetch(`/admin/baggage-options/${deleteTarget.id}`, { method: "DELETE" });
        setBaggageVersion((v) => v + 1);
      }
      setDeleteTarget(null);
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      if (apiErr.code === "RESOURCE_IN_USE") {
        setActionError(`Mục "${deleteTarget.name}" đã được sử dụng nên không thể xoá. Hãy sửa và tắt trạng thái Hoạt động.`);
      } else {
        setActionError(errorText(apiErr) || "Không thể xoá.");
      }
      setDeleteTarget(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AdminPage>
      <div className="flex items-center gap-16">
        <Link href="/admin/airlines" className={`${textLink} text-caption text-muted hover:text-ink`}>
          ← Danh sách hãng bay
        </Link>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-16">
        <div>
          <h1 className="text-heading-lg font-bold">Hãng {code}.</h1>
          <p className="text-caption text-muted mt-4">
            Quản trị gói giá và bảng giá hành lý ký gửi mua thêm (A-03).
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setActionError(null);
            if (tab === "fares") {
              setFareModal({ mode: "create" });
            } else {
              setBaggageModal({ mode: "create" });
            }
          }}
          className={pillDark}
        >
          {tab === "fares" ? "+ Thêm gói giá" : "+ Thêm mức hành lý"}
        </button>
      </div>

      {actionError && !fareModal && !baggageModal && <Notice>{actionError}</Notice>}

      <AdminTabs
        activeKey={tab}
        onChange={(k) => {
          setActionError(null);
          setTab(k as "fares" | "baggage");
        }}
        tabs={[
          { key: "fares", label: "Gói giá (Fare Families)" },
          { key: "baggage", label: "Bảng giá hành lý (Baggage Options)" },
        ]}
      />

      {tab === "fares" && (
        <>
          {faresRes === undefined ? (
            <FormSkeleton rows={3} />
          ) : "error" in faresRes ? (
            <Notice>{faresRes.error.detail}</Notice>
          ) : (
            <div className={tableBox}>
              <table aria-label="Bảng gói giá" className="w-full border-collapse">
                <thead>
                  <tr>
                    <th className={th}>Tên gói giá</th>
                    <th className={th}>Hạng ghế</th>
                    <th className={th}>Xách tay</th>
                    <th className={th}>Ký gửi</th>
                    <th className={th}>Hoàn vé</th>
                    <th className={th}>Đổi chuyến</th>
                    <th className={th}>Trạng thái</th>
                    <th className={th}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {faresRes.data.map((f) => (
                    <tr key={f.id}>
                      <td className={`${td} font-bold`}>{f.name}</td>
                      <td className={td}>{cabinLabel(f.cabinClass)}</td>
                      <td className={td}>{f.carryOnKg} kg</td>
                      <td className={td}>{f.checkedBaggageKg} kg</td>
                      <td className={td}>
                        {f.refundable ? `Có (${vnd(f.refundFee)})` : "Không"}
                      </td>
                      <td className={td}>
                        {f.changeable ? `Có (${vnd(f.changeFee)})` : "Không"}
                      </td>
                      <td className={td}>
                        {f.active ? (
                          <span className="font-bold text-ink">Bán</span>
                        ) : (
                          <span className="text-muted">Ngừng bán</span>
                        )}
                      </td>
                      <td className={`${td} space-x-16`}>
                        <button
                          type="button"
                          onClick={() => {
                            setActionError(null);
                            setFareModal({ mode: "edit", item: f });
                          }}
                          className={textLink}
                        >
                          Sửa
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setActionError(null);
                            setDeleteTarget({ type: "fare", id: f.id, name: f.name });
                          }}
                          className={`${textLink} text-muted hover:text-ink`}
                        >
                          Xoá
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {tab === "baggage" && (
        <>
          {baggageRes === undefined ? (
            <FormSkeleton rows={3} />
          ) : "error" in baggageRes ? (
            <Notice>{baggageRes.error.detail}</Notice>
          ) : (
            <div className={tableBox}>
              <table aria-label="Bảng giá hành lý" className="w-full border-collapse">
                <thead>
                  <tr>
                    <th className={th}>Trọng lượng (kg)</th>
                    <th className={th}>Giá bán</th>
                    <th className={th}>Trạng thái</th>
                    <th className={th}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {baggageRes.data.map((b) => (
                    <tr key={b.id}>
                      <td className={`${td} font-bold`}>{b.weightKg} kg</td>
                      <td className={td}>{vnd(b.price)}</td>
                      <td className={td}>
                        {b.active ? (
                          <span className="font-bold text-ink">Bán</span>
                        ) : (
                          <span className="text-muted">Ngừng bán</span>
                        )}
                      </td>
                      <td className={`${td} space-x-16`}>
                        <button
                          type="button"
                          onClick={() => {
                            setActionError(null);
                            setBaggageModal({ mode: "edit", item: b });
                          }}
                          className={textLink}
                        >
                          Sửa
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setActionError(null);
                            setDeleteTarget({ type: "baggage", id: b.id, name: `${b.weightKg} kg` });
                          }}
                          className={`${textLink} text-muted hover:text-ink`}
                        >
                          Xoá
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* Modal Thêm/Sửa Gói giá */}
      <AdminModal
        title={fareModal?.mode === "create" ? "Thêm gói giá mới" : `Sửa gói giá ${fareModal?.item?.name}`}
        isOpen={fareModal !== null}
        onClose={() => setFareModal(null)}
      >
        <form onSubmit={handleSaveFare} className="grid gap-16">
          {actionError && <Notice>{actionError}</Notice>}
          <Field
            label="Tên gói giá"
            name="name"
            required
            defaultValue={fareModal?.item?.name ?? ""}
            placeholder="Economy Classic"
          />
          {fareModal?.mode === "create" ? (
            <SelectField label="Hạng ghế" name="cabinClass" defaultValue="ECONOMY">
              {CABIN_OPTIONS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </SelectField>
          ) : (
            <div className="grid gap-4">
              <span className="text-caption font-bold">Hạng ghế (không thể đổi)</span>
              <p className="text-caption text-muted">{cabinLabel(fareModal?.item?.cabinClass ?? "")}</p>
            </div>
          )}
          <div className="grid grid-cols-2 gap-16">
            <Field
              label="Hành lý xách tay (kg)"
              name="carryOnKg"
              type="number"
              min={0}
              required
              defaultValue={fareModal?.item?.carryOnKg ?? 7}
            />
            <Field
              label="Hành lý ký gửi (kg)"
              name="checkedBaggageKg"
              type="number"
              min={0}
              required
              defaultValue={fareModal?.item?.checkedBaggageKg ?? 20}
            />
          </div>
          <div className="grid grid-cols-2 gap-16 border-t border-line pt-16">
            <label className="flex items-center gap-10 text-caption font-bold cursor-pointer">
              <input
                type="checkbox"
                name="refundable"
                defaultChecked={fareModal?.item?.refundable ?? true}
                className="size-18"
              />
              Được hoàn vé
            </label>
            <Field
              label="Phí hoàn vé (₫)"
              name="refundFee"
              type="number"
              min={0}
              step={10000}
              defaultValue={fareModal?.item?.refundFee ?? 350000}
            />
          </div>
          <div className="grid grid-cols-2 gap-16">
            <label className="flex items-center gap-10 text-caption font-bold cursor-pointer">
              <input
                type="checkbox"
                name="changeable"
                defaultChecked={fareModal?.item?.changeable ?? true}
                className="size-18"
              />
              Được đổi chuyến
            </label>
            <Field
              label="Phí đổi chuyến (₫)"
              name="changeFee"
              type="number"
              min={0}
              step={10000}
              defaultValue={fareModal?.item?.changeFee ?? 350000}
            />
          </div>
          <label className="flex items-center gap-10 text-caption font-bold cursor-pointer border-t border-line pt-16">
            <input
              type="checkbox"
              name="active"
              defaultChecked={fareModal?.item?.active ?? true}
              className="size-18"
            />
            Đang mở bán
          </label>
          <div className="flex justify-end gap-11 mt-11">
            <button type="button" onClick={() => setFareModal(null)} className={pillLine} disabled={loading}>
              Huỷ
            </button>
            <button type="submit" className={pillDark} disabled={loading}>
              {loading ? "Đang lưu..." : "Lưu gói giá"}
            </button>
          </div>
        </form>
      </AdminModal>

      {/* Modal Thêm/Sửa Bảng giá hành lý */}
      <AdminModal
        title={baggageModal?.mode === "create" ? "Thêm mức hành lý" : `Sửa mức hành lý ${baggageModal?.item?.weightKg} kg`}
        isOpen={baggageModal !== null}
        onClose={() => setBaggageModal(null)}
      >
        <form onSubmit={handleSaveBaggage} className="grid gap-16">
          {actionError && <Notice>{actionError}</Notice>}
          <Field
            label="Trọng lượng mua thêm (kg)"
            name="weightKg"
            type="number"
            min={1}
            max={100}
            required
            defaultValue={baggageModal?.item?.weightKg ?? 10}
            placeholder="10"
          />
          <Field
            label="Giá bán (₫)"
            name="price"
            type="number"
            min={0}
            step={10000}
            required
            defaultValue={baggageModal?.item?.price ?? 150000}
            placeholder="150000"
          />
          <label className="flex items-center gap-10 text-caption font-bold cursor-pointer">
            <input
              type="checkbox"
              name="active"
              defaultChecked={baggageModal?.item?.active ?? true}
              className="size-18"
            />
            Đang mở bán
          </label>
          <div className="flex justify-end gap-11 mt-11">
            <button type="button" onClick={() => setBaggageModal(null)} className={pillLine} disabled={loading}>
              Huỷ
            </button>
            <button type="submit" className={pillDark} disabled={loading}>
              {loading ? "Đang lưu..." : "Lưu mức hành lý"}
            </button>
          </div>
        </form>
      </AdminModal>

      {/* Confirm Modal Xoá */}
      <ConfirmModal
        title={`Xoá ${deleteTarget?.name}?`}
        message={`Bạn có chắc muốn xoá mục ${deleteTarget?.name}? Thao tác không thể hoàn tác nếu chưa có đơn đặt chỗ nào tham chiếu.`}
        confirmLabel="Xoá mục này"
        isOpen={deleteTarget !== null}
        isLoading={loading}
        onConfirm={handleDelete}
        onClose={() => setDeleteTarget(null)}
      />
    </AdminPage>
  );
}
