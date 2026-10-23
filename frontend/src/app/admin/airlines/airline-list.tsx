"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import {
  AdminModal,
  AdminPage,
  ConfirmModal,
  tableBox,
  td,
  th,
} from "../admin-ui";
import {
  Field,
  FormSkeleton,
  Notice,
  pillDark,
  pillLine,
  textLink,
} from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { errorText } from "@/lib/admin";
import { useApi } from "@/lib/use-api";

type Airline = Schemas["AirlineResponse"];

export default function AirlineList() {
  const [version, setVersion] = useState(0);
  const airlinesRes = useApi<Airline[]>("/admin/airlines", version);

  const [modalState, setModalState] = useState<{
    mode: "create" | "edit";
    airline?: Airline;
  } | null>(null);

  const [deleteTarget, setDeleteTarget] = useState<Airline | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const reload = () => setVersion((v) => v + 1);

  const handleSave = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const formData = new FormData(e.currentTarget);
    const code = (formData.get("code") as string)?.toUpperCase().trim();
    const name = (formData.get("name") as string)?.trim();
    const ticketPrefix = (formData.get("ticketPrefix") as string)?.trim();
    const active = formData.get("active") === "on";

    try {
      if (modalState?.mode === "create") {
        await apiFetch("/admin/airlines", {
          method: "POST",
          body: {
            code,
            name,
            ticketPrefix,
            active: true,
          } satisfies Schemas["CreateAirlineRequest"],
        });
      } else if (modalState?.mode === "edit" && modalState.airline) {
        await apiFetch(`/admin/airlines/${modalState.airline.code}`, {
          method: "PUT",
          body: {
            name,
            ticketPrefix,
            active,
          } satisfies Schemas["UpdateAirlineRequest"],
        });
      }
      setModalState(null);
      reload();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setActionError(errorText(apiErr) || "Không thể lưu hãng bay.");
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setActionError(null);
    setLoading(true);
    try {
      await apiFetch(`/admin/airlines/${deleteTarget.code}`, {
        method: "DELETE",
      });
      setDeleteTarget(null);
      reload();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      if (apiErr.code === "RESOURCE_IN_USE") {
        setActionError(
          `Hãng bay ${deleteTarget.code} đã có dữ liệu tham chiếu nên không thể xoá. Hãy chọn Sửa và tắt Hoạt động.`,
        );
      } else {
        setActionError(errorText(apiErr) || "Không thể xoá hãng bay.");
      }
      setDeleteTarget(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AdminPage>
      <div className="flex flex-wrap items-center justify-between gap-16">
        <div>
          <h1 className="text-heading-lg font-bold">Hãng bay.</h1>
          <p className="text-caption text-muted mt-4">
            Quản lý các hãng hàng không, mã số vé 3 chữ số và cấu hình gói giá
            (A-02).
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setActionError(null);
            setModalState({ mode: "create" });
          }}
          className={pillDark}
        >
          + Thêm hãng bay
        </button>
      </div>

      {actionError && modalState === null && <Notice>{actionError}</Notice>}

      {airlinesRes === undefined ? (
        <FormSkeleton rows={3} />
      ) : "error" in airlinesRes ? (
        <Notice>{airlinesRes.error.detail}</Notice>
      ) : (
        <div className={tableBox}>
          <table aria-label="Bảng hãng bay" className="w-full border-collapse">
            <thead>
              <tr>
                <th className={th}>Mã</th>
                <th className={th}>Tên hãng</th>
                <th className={th}>Mã vé</th>
                <th className={th}>Trạng thái</th>
                <th className={th}>Cấu hình dịch vụ</th>
                <th className={th}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {airlinesRes.data.map((al) => (
                <tr key={al.code}>
                  <td className={`${td} font-bold`}>{al.code}</td>
                  <td className={td}>{al.name}</td>
                  <td className={`${td} font-mono font-bold`}>
                    {al.ticketPrefix}
                  </td>
                  <td className={td}>
                    {al.active ? (
                      <span className="text-ink font-bold">Hoạt động</span>
                    ) : (
                      <span className="text-muted">Ngừng kích hoạt</span>
                    )}
                  </td>
                  <td className={td}>
                    <Link
                      href={`/admin/airlines/${al.code}`}
                      className={textLink}
                    >
                      Gói giá & Bảng giá hành lý →
                    </Link>
                  </td>
                  <td className={`${td} space-x-16`}>
                    <button
                      type="button"
                      onClick={() => {
                        setActionError(null);
                        setModalState({ mode: "edit", airline: al });
                      }}
                      className={textLink}
                    >
                      Sửa
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setActionError(null);
                        setDeleteTarget(al);
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

      {/* Modal Thêm / Sửa Hãng bay */}
      <AdminModal
        title={
          modalState?.mode === "create"
            ? "Thêm hãng bay mới"
            : `Sửa hãng bay ${modalState?.airline?.code}`
        }
        isOpen={modalState !== null}
        onClose={() => setModalState(null)}
      >
        <form onSubmit={handleSave} className="grid gap-16">
          {actionError && <Notice>{actionError}</Notice>}
          {modalState?.mode === "create" && (
            <Field
              label="Mã IATA (2 ký tự)"
              name="code"
              required
              maxLength={2}
              pattern="^[A-Z0-9]{2}$"
              placeholder="VN"
              invalidMessage="Mã hãng bay gồm 2 ký tự in hoa/số (VD: VN, VJ, QH)"
            />
          )}
          <Field
            label="Mã số vé (3 chữ số duy nhất)"
            name="ticketPrefix"
            required
            maxLength={3}
            pattern="^\d{3}$"
            defaultValue={modalState?.airline?.ticketPrefix ?? ""}
            placeholder="738"
            invalidMessage="Mã số vé gồm đúng 3 chữ số (VD: 738, 978)"
          />
          <Field
            label="Tên hãng hàng không"
            name="name"
            required
            defaultValue={modalState?.airline?.name ?? ""}
            placeholder="Vietnam Airlines"
          />
          {modalState?.mode === "edit" && (
            <label className="flex items-center gap-10 text-caption font-bold cursor-pointer">
              <input
                type="checkbox"
                name="active"
                defaultChecked={modalState.airline?.active}
                className="size-18"
              />
              Đang hoạt động
            </label>
          )}
          <div className="flex justify-end gap-11 mt-11">
            <button
              type="button"
              onClick={() => setModalState(null)}
              className={pillLine}
              disabled={loading}
            >
              Huỷ
            </button>
            <button type="submit" className={pillDark} disabled={loading}>
              {loading ? "Đang lưu..." : "Lưu hãng bay"}
            </button>
          </div>
        </form>
      </AdminModal>

      {/* Confirm Modal Xoá Hãng bay */}
      <ConfirmModal
        title={`Xoá hãng bay ${deleteTarget?.code}?`}
        message={`Bạn có chắc muốn xoá hãng ${deleteTarget?.name} (${deleteTarget?.code})? Nếu hãng đã được tham chiếu bởi chuyến bay hoặc gói giá, hệ thống sẽ từ chối.`}
        confirmLabel="Xoá hãng"
        isOpen={deleteTarget !== null}
        isLoading={loading}
        onConfirm={handleDelete}
        onClose={() => setDeleteTarget(null)}
      />
    </AdminPage>
  );
}
