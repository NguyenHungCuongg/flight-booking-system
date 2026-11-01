"use client";

import { useState, type FormEvent } from "react";
import { AdminModal, AdminPage, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, pillPrimary, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { errorText, SETTING_INFO, validateSettingValue, vnTime } from "@/lib/admin";
import { useApi } from "@/lib/use-api";

type Setting = Schemas["Setting"];

export default function SettingsForm() {
  const [version, setVersion] = useState(0);
  const settingsRes = useApi<Setting[]>("/admin/settings", version);

  const [editSetting, setEditSetting] = useState<Setting | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const reload = () => setVersion((v) => v + 1);

  const handleUpdate = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!editSetting) return;
    setActionError(null);

    const fd = new FormData(e.currentTarget);
    const value = Number(fd.get("value"));

    const valErr = validateSettingValue(editSetting.key, value);
    if (valErr) {
      setActionError(valErr);
      return;
    }

    setLoading(true);
    try {
      await apiFetch(`/admin/settings/${editSetting.key}`, {
        method: "PUT",
        body: { value } satisfies Schemas["UpdateSettingRequest"],
      });
      setEditSetting(null);
      reload();
    } catch (err: unknown) {
      setActionError(errorText(err as ApiError) || "Không thể cập nhật tham số.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AdminPage>
      <div>
        <h1 className="text-heading-lg font-bold">Tham số hệ thống.</h1>
        <p className="text-caption text-muted mt-4">
          Cấu hình 8 tham số vận hành cốt lõi của toàn hệ thống SkyLine (A-09, FR-111, BR-25).
        </p>
      </div>

      {actionError && !editSetting && <Notice>{actionError}</Notice>}

      {settingsRes === undefined ? (
        <FormSkeleton rows={3} />
      ) : "error" in settingsRes ? (
        <Notice>{settingsRes.error.detail}</Notice>
      ) : (
        <div className={tableBox}>
          <table aria-label="Bảng tham số hệ thống" className="w-full border-collapse">
            <thead>
              <tr>
                <th className={th}>Tên tham số</th>
                <th className={th}>Giá trị hiện tại</th>
                <th className={th}>Khoảng hợp lệ</th>
                <th className={th}>Mô tả ý nghĩa</th>
                <th className={th}>Người sửa gần nhất</th>
                <th className={th}>Thời điểm sửa</th>
                <th className={th}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {settingsRes.data.map((s) => {
                const meta = SETTING_INFO[s.key];
                return (
                  <tr key={s.key}>
                    <td className={`${td} font-bold`}>{meta?.name ?? s.key}</td>
                    <td className={`${td} font-bold text-caption font-mono`}>
                      {s.value} {meta?.unit ?? ""}
                    </td>
                    <td className={`${td} text-muted font-mono`}>
                      {s.min} – {s.max}
                    </td>
                    <td className={td}>{meta?.description ?? s.description}</td>
                    <td className={td}>{s.updatedByName ?? "Hệ thống"}</td>
                    <td className={td}>
                      {s.updatedAt ? vnTime(s.updatedAt) : "Mặc định"}
                    </td>
                    <td className={td}>
                      <button
                        type="button"
                        onClick={() => {
                          setActionError(null);
                          setEditSetting(s);
                        }}
                        className={textLink}
                      >
                        Sửa
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Modal Sửa Tham số */}
      <AdminModal
        title={`Sửa tham số: ${editSetting ? (SETTING_INFO[editSetting.key]?.name ?? editSetting.key) : ""}`}
        isOpen={editSetting !== null}
        onClose={() => setEditSetting(null)}
      >
        <form onSubmit={handleUpdate} className="grid gap-16">
          {actionError && <Notice>{actionError}</Notice>}
          <p className="text-caption text-muted">
            {editSetting ? (SETTING_INFO[editSetting.key]?.description ?? editSetting.description) : ""}
          </p>
          <Field
            label={`Giá trị mới (${editSetting ? SETTING_INFO[editSetting.key]?.unit : ""})`}
            name="value"
            type="number"
            min={editSetting?.min}
            max={editSetting?.max}
            invalidMessage={`Giá trị phải từ ${editSetting?.min} đến ${editSetting?.max}`}
            required
            defaultValue={editSetting?.value}
          />
          <div className="flex justify-end gap-11 mt-11">
            <button type="button" onClick={() => setEditSetting(null)} className={pillLine} disabled={loading}>
              Huỷ
            </button>
            <button type="submit" className={pillPrimary} disabled={loading}>
              {loading ? "Đang lưu..." : "Lưu giá trị mới"}
            </button>
          </div>
        </form>
      </AdminModal>
    </AdminPage>
  );
}
