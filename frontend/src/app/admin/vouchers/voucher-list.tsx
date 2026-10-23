"use client";

import { useState, type FormEvent } from "react";
import { AdminModal, AdminPage, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, SelectField, pillPrimary, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { vnd } from "@/app/flights/itineraries";
import { errorText, VOUCHER_TYPE_LABEL } from "@/lib/admin";
import { dmy } from "@/lib/booking";
import { useApi } from "@/lib/use-api";

type Voucher = Schemas["VoucherResponse"];

export default function VoucherList() {
  const [version, setVersion] = useState(0);
  const vouchersRes = useApi<Voucher[]>("/admin/vouchers", version);

  const [modalState, setModalState] = useState<{
    mode: "create" | "edit";
    voucher?: Voucher;
  } | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const reload = () => setVersion((v) => v + 1);

  const handleSave = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const code = (fd.get("code") as string)?.toUpperCase().trim() || modalState?.voucher?.code;
    const discountType = fd.get("discountType") as "PERCENT" | "FIXED";
    const discountValue = Number(fd.get("discountValue"));
    const maxDiscountRaw = fd.get("maxDiscount");
    const maxDiscount = maxDiscountRaw ? Number(maxDiscountRaw) : undefined;
    const minOrderAmount = Number(fd.get("minOrderAmount") || 0);
    const usageLimitRaw = fd.get("usageLimit");
    const usageLimit = usageLimitRaw ? Number(usageLimitRaw) : undefined;
    // Ô datetime-local nhập giờ Việt Nam (API trả và nhận voucher theo +07:00).
    const validFrom = `${fd.get("validFrom")}:00+07:00`;
    const validTo = `${fd.get("validTo")}:00+07:00`;
    const active = fd.get("active") === "on";

    const payload: Schemas["VoucherFields"] = {
      code: code ?? "",
      description: (fd.get("description") as string)?.trim(),
      discountType,
      discountValue,
      maxDiscount,
      minOrderAmount,
      usageLimit,
      validFrom,
      validTo,
      active,
    };

    try {
      if (modalState?.mode === "create") {
        await apiFetch("/admin/vouchers", {
          method: "POST",
          body: payload,
        });
      } else if (modalState?.mode === "edit" && modalState.voucher) {
        await apiFetch(`/admin/vouchers/${modalState.voucher.id}`, {
          method: "PUT",
          body: payload,
        });
      }
      setModalState(null);
      reload();
    } catch (err: unknown) {
      setActionError(errorText(err as ApiError) || "Không thể lưu voucher.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AdminPage>
      <div className="flex flex-wrap items-center justify-between gap-16">
        <div>
          <h1 className="text-heading-lg font-bold">Voucher khuyến mãi.</h1>
          <p className="text-caption text-muted mt-4">
            Quản trị mã giảm giá, theo dõi số lượt đã sử dụng và cấu hình hiệu lực (A-07).
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setActionError(null);
            setModalState({ mode: "create" });
          }}
          className={pillPrimary}
        >
          + Thêm voucher
        </button>
      </div>

      {actionError && modalState === null && <Notice>{actionError}</Notice>}

      {vouchersRes === undefined ? (
        <FormSkeleton rows={3} />
      ) : "error" in vouchersRes ? (
        <Notice>{vouchersRes.error.detail}</Notice>
      ) : (
        <div className={tableBox}>
          <table aria-label="Bảng voucher" className="w-full border-collapse">
            <thead>
              <tr>
                <th className={th}>Mã voucher</th>
                <th className={th}>Mức giảm</th>
                <th className={th}>Đơn tối thiểu</th>
                <th className={th}>Giảm tối đa</th>
                <th className={th}>Lượt dùng</th>
                <th className={th}>Thời hạn</th>
                <th className={th}>Trạng thái</th>
                <th className={th}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {vouchersRes.data.map((v) => (
                <tr key={v.id}>
                  <td className={`${td} font-bold font-mono text-caption`}>{v.code}</td>
                  <td className={td}>
                    {v.discountType === "PERCENT"
                      ? `${v.discountValue}%`
                      : vnd(v.discountValue)}
                  </td>
                  <td className={td}>{v.minOrderAmount > 0 ? vnd(v.minOrderAmount) : "Không"}</td>
                  <td className={td}>{v.maxDiscount ? vnd(v.maxDiscount) : "Không giới hạn"}</td>
                  <td className={td}>
                    {v.usedCount} / {v.usageLimit ?? "∞"}
                  </td>
                  <td className={td}>
                    {dmy(v.validFrom)} → {dmy(v.validTo)}
                  </td>
                  <td className={td}>
                    {v.active ? (
                      <span className="font-bold text-ink">Hoạt động</span>
                    ) : (
                      <span className="text-muted">Đã tắt</span>
                    )}
                  </td>
                  <td className={td}>
                    <button
                      type="button"
                      onClick={() => {
                        setActionError(null);
                        setModalState({ mode: "edit", voucher: v });
                      }}
                      className={textLink}
                    >
                      Sửa
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Modal Thêm / Sửa Voucher */}
      <AdminModal
        title={modalState?.mode === "create" ? "Tạo voucher mới" : `Sửa voucher ${modalState?.voucher?.code}`}
        isOpen={modalState !== null}
        onClose={() => setModalState(null)}
      >
        <form onSubmit={handleSave} className="grid gap-16 max-h-[75vh] overflow-y-auto pr-8">
          {actionError && <Notice>{actionError}</Notice>}
          {modalState?.mode === "create" ? (
            <Field
              label="Mã voucher (in hoa, duy nhất)"
              name="code"
              required
              placeholder="SUMMER2026"
            />
          ) : (
            <div className="grid gap-4">
              <span className="text-caption font-bold">Mã voucher (không thể đổi)</span>
              <p className="font-mono font-bold text-caption text-ink">{modalState?.voucher?.code}</p>
            </div>
          )}

          <Field
            label="Mô tả"
            name="description"
            required
            maxLength={255}
            defaultValue={modalState?.voucher?.description ?? ""}
            placeholder="Giảm 10% cho đơn đầu tiên"
          />

          <div className="grid grid-cols-2 gap-16">
            <SelectField
              label="Loại giảm giá"
              name="discountType"
              defaultValue={modalState?.voucher?.discountType ?? "PERCENT"}
            >
              <option value="PERCENT">{VOUCHER_TYPE_LABEL.PERCENT}</option>
              <option value="FIXED">{VOUCHER_TYPE_LABEL.FIXED}</option>
            </SelectField>
            <Field
              label="Giá trị giảm (% hoặc ₫)"
              name="discountValue"
              type="number"
              min={1}
              required
              defaultValue={modalState?.voucher?.discountValue ?? 10}
            />
          </div>

          <div className="grid grid-cols-2 gap-16">
            <Field
              label="Mức giảm tối đa (₫)"
              name="maxDiscount"
              type="number"
              min={0}
              defaultValue={modalState?.voucher?.maxDiscount ?? ""}
              placeholder="Để trống nếu không giới hạn"
            />
            <Field
              label="Đơn hàng tối thiểu (₫)"
              name="minOrderAmount"
              type="number"
              min={0}
              defaultValue={modalState?.voucher?.minOrderAmount ?? 0}
            />
          </div>

          <Field
            label="Số lượt dùng tối đa"
            name="usageLimit"
            type="number"
            min={1}
            defaultValue={modalState?.voucher?.usageLimit ?? ""}
            placeholder="Để trống nếu không giới hạn"
          />

          <div className="grid grid-cols-2 gap-16">
            <Field
              label="Hiệu lực từ (giờ Việt Nam)"
              type="datetime-local"
              name="validFrom"
              required
              defaultValue={modalState?.voucher?.validFrom.slice(0, 16)}
            />
            <Field
              label="Hiệu lực đến (giờ Việt Nam)"
              type="datetime-local"
              name="validTo"
              required
              defaultValue={modalState?.voucher?.validTo.slice(0, 16)}
            />
          </div>

          <label className="flex items-center gap-10 text-caption font-bold cursor-pointer">
            <input
              type="checkbox"
              name="active"
              defaultChecked={modalState?.voucher?.active ?? true}
              className="size-18"
            />
            Đang kích hoạt
          </label>

          <div className="flex justify-end gap-11 mt-11">
            <button type="button" onClick={() => setModalState(null)} className={pillLine} disabled={loading}>
              Huỷ
            </button>
            <button type="submit" className={pillPrimary} disabled={loading}>
              {loading ? "Đang lưu..." : "Lưu voucher"}
            </button>
          </div>
        </form>
      </AdminModal>
    </AdminPage>
  );
}
