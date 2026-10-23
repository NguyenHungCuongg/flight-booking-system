"use client";

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
  pillPrimary,
  pillLine,
  textLink,
} from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { errorText } from "@/lib/admin";
import { useApi } from "@/lib/use-api";

type Airport = Schemas["AirportResponse"];

export default function AirportList() {
  const [version, setVersion] = useState(0);
  const airportsRes = useApi<Airport[]>("/admin/airports", version);

  const [modalState, setModalState] = useState<{
    mode: "create" | "edit";
    airport?: Airport;
  } | null>(null);

  const [deleteTarget, setDeleteTarget] = useState<Airport | null>(null);
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
    const city = (formData.get("city") as string)?.trim();
    const countryCode = (formData.get("countryCode") as string)
      ?.toUpperCase()
      .trim();
    const timezone = (formData.get("timezone") as string)?.trim();
    const active = formData.get("active") === "on";

    try {
      if (modalState?.mode === "create") {
        await apiFetch("/admin/airports", {
          method: "POST",
          body: {
            code,
            name,
            city,
            countryCode,
            timezone,
            active: true,
          } satisfies Schemas["CreateAirportRequest"],
        });
      } else if (modalState?.mode === "edit" && modalState.airport) {
        await apiFetch(`/admin/airports/${modalState.airport.code}`, {
          method: "PUT",
          body: {
            name,
            city,
            countryCode,
            timezone,
            active,
          } satisfies Schemas["UpdateAirportRequest"],
        });
      }
      setModalState(null);
      reload();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setActionError(errorText(apiErr) || "Không thể lưu sân bay.");
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setActionError(null);
    setLoading(true);
    try {
      await apiFetch(`/admin/airports/${deleteTarget.code}`, {
        method: "DELETE",
      });
      setDeleteTarget(null);
      reload();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      if (apiErr.code === "RESOURCE_IN_USE") {
        setActionError(
          `Sân bay ${deleteTarget.code} đã có chuyến bay sử dụng nên không thể xoá. Hãy chọn Sửa và tắt Hoạt động.`,
        );
      } else {
        setActionError(errorText(apiErr) || "Không thể xoá sân bay.");
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
          <h1 className="text-heading-lg font-bold">Sân bay.</h1>
          <p className="text-caption text-muted mt-4">
            Quản lý danh mục sân bay đi, đến và múi giờ tương ứng (A-01).
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
          + Thêm sân bay
        </button>
      </div>

      {actionError && modalState === null && <Notice>{actionError}</Notice>}

      {airportsRes === undefined ? (
        <FormSkeleton rows={3} />
      ) : "error" in airportsRes ? (
        <Notice>{airportsRes.error.detail}</Notice>
      ) : (
        <div className={tableBox}>
          <table aria-label="Bảng sân bay" className="w-full border-collapse">
            <thead>
              <tr>
                <th className={th}>Mã</th>
                <th className={th}>Tên sân bay</th>
                <th className={th}>Thành phố</th>
                <th className={th}>Quốc gia</th>
                <th className={th}>Múi giờ</th>
                <th className={th}>Trạng thái</th>
                <th className={th}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {airportsRes.data.map((a) => (
                <tr key={a.code}>
                  <td className={`${td} font-bold`}>{a.code}</td>
                  <td className={td}>{a.name}</td>
                  <td className={td}>{a.city}</td>
                  <td className={td}>{a.countryCode}</td>
                  <td className={`${td} font-mono text-caption`}>
                    {a.timezone}
                  </td>
                  <td className={td}>
                    {a.active ? (
                      <span className="text-ink font-bold">Hoạt động</span>
                    ) : (
                      <span className="text-muted">Ngừng kích hoạt</span>
                    )}
                  </td>
                  <td className={`${td} space-x-16`}>
                    <button
                      type="button"
                      onClick={() => {
                        setActionError(null);
                        setModalState({ mode: "edit", airport: a });
                      }}
                      className={textLink}
                    >
                      Sửa
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setActionError(null);
                        setDeleteTarget(a);
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

      {/* Modal Thêm / Sửa Sân bay */}
      <AdminModal
        title={
          modalState?.mode === "create"
            ? "Thêm sân bay mới"
            : `Sửa sân bay ${modalState?.airport?.code}`
        }
        isOpen={modalState !== null}
        onClose={() => setModalState(null)}
      >
        <form onSubmit={handleSave} className="grid gap-16">
          {actionError && <Notice>{actionError}</Notice>}
          {modalState?.mode === "create" && (
            <Field
              label="Mã IATA (3 chữ cái)"
              name="code"
              required
              maxLength={3}
              pattern="^[A-Z]{3}$"
              placeholder="HAN"
              invalidMessage="Mã sân bay phải gồm đúng 3 chữ in hoa (VD: HAN, SGN)"
            />
          )}
          <Field
            label="Tên sân bay"
            name="name"
            required
            defaultValue={modalState?.airport?.name ?? ""}
            placeholder="Sân bay Quốc tế Nội Bài"
          />
          <div className="grid grid-cols-2 gap-16">
            <Field
              label="Thành phố"
              name="city"
              required
              defaultValue={modalState?.airport?.city ?? ""}
              placeholder="Hà Nội"
            />
            <Field
              label="Mã quốc gia (2 chữ cái)"
              name="countryCode"
              required
              maxLength={2}
              pattern="^[A-Z]{2}$"
              defaultValue={modalState?.airport?.countryCode ?? "VN"}
              placeholder="VN"
              invalidMessage="Mã quốc gia gồm 2 chữ cái in hoa theo ISO (VD: VN)"
            />
          </div>
          <Field
            label="Múi giờ IANA"
            name="timezone"
            required
            defaultValue={modalState?.airport?.timezone ?? "Asia/Ho_Chi_Minh"}
            placeholder="Asia/Ho_Chi_Minh"
          />
          {modalState?.mode === "edit" && (
            <label className="flex items-center gap-10 text-caption font-bold cursor-pointer">
              <input
                type="checkbox"
                name="active"
                defaultChecked={modalState.airport?.active}
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
            <button type="submit" className={pillPrimary} disabled={loading}>
              {loading ? "Đang lưu..." : "Lưu sân bay"}
            </button>
          </div>
        </form>
      </AdminModal>

      {/* Confirm Modal Xoá Sân bay */}
      <ConfirmModal
        title={`Xoá sân bay ${deleteTarget?.code}?`}
        message={`Bạn có chắc muốn xoá sân bay ${deleteTarget?.name} (${deleteTarget?.code})? Nếu sân bay đã có chuyến bay tham chiếu, hệ thống sẽ từ chối và yêu cầu chuyển sang ngừng hoạt động.`}
        confirmLabel="Xoá sân bay"
        isOpen={deleteTarget !== null}
        isLoading={loading}
        onConfirm={handleDelete}
        onClose={() => setDeleteTarget(null)}
      />
    </AdminPage>
  );
}
