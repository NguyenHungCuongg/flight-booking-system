"use client";

import Form from "next/form";
import { useSearchParams } from "next/navigation";
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
  PASSWORD_PATTERN,
  PASSWORD_RULE,
  SelectField,
  pillPrimary,
  pillLine,
  textLink,
} from "@/components/form";
import { errorText } from "@/lib/admin";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { listQuery } from "@/lib/staff";
import { Pager } from "../../staff/staff-ui";
import { useApi } from "@/lib/use-api";
import { useCurrentUser } from "@/lib/use-current-user";

type UserAdmin = Schemas["UserResponse"];

const ROLE_LABEL: Record<string, string> = {
  CUSTOMER: "Khách hàng",
  STAFF: "Nhân viên",
  ADMIN: "Quản trị viên",
};

export default function UserList() {
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const role = params.get("role") ?? "";
  const currentUser = useCurrentUser();

  const [version, setVersion] = useState(0);
  const query = listQuery(params, ["q", "role"]);
  const usersRes = useApi<Schemas["PagedModelUserResponse"]>(
    `/admin/users?${query}`,
    version,
  );

  const [createOpen, setCreateOpen] = useState(false);
  const [toggleTarget, setToggleTarget] = useState<UserAdmin | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const reload = () => setVersion((v) => v + 1);

  const handleCreate = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const email = (fd.get("email") as string)?.trim();
    const fullName = (fd.get("fullName") as string)?.trim();
    const phone = (fd.get("phone") as string)?.trim();
    const password = String(fd.get("password"));
    const role = fd.get("role") as "STAFF" | "ADMIN";

    try {
      await apiFetch("/admin/users", {
        method: "POST",
        body: {
          email,
          fullName,
          phone,
          password,
          role,
        } satisfies Schemas["CreateUserRequest"],
      });
      setCreateOpen(false);
      reload();
    } catch (err: unknown) {
      setActionError(errorText(err as ApiError) || "Không thể tạo tài khoản.");
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async () => {
    if (!toggleTarget) return;
    setActionError(null);
    setLoading(true);

    const nextStatus = toggleTarget.status === "ACTIVE" ? "LOCKED" : "ACTIVE";

    try {
      await apiFetch(`/admin/users/${toggleTarget.id}/status`, {
        method: "PATCH",
        body: { status: nextStatus } satisfies Schemas["UpdateStatusRequest"],
      });
      setToggleTarget(null);
      reload();
    } catch (err: unknown) {
      setActionError(
        errorText(err as ApiError) || "Không thể đổi trạng thái tài khoản.",
      );
      setToggleTarget(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AdminPage>
      <div className="flex flex-wrap items-center justify-between gap-16">
        <div>
          <h1 className="text-heading-lg font-bold">Người dùng & Tài khoản.</h1>
          <p className="text-caption text-muted mt-4">
            Quản trị danh sách người dùng, cấp quyền tài khoản và khoá phiên
            truy cập (A-08).
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setActionError(null);
            setCreateOpen(true);
          }}
          className={pillPrimary}
        >
          + Tạo tài khoản Staff / Admin
        </button>
      </div>

      {actionError && !createOpen && <Notice>{actionError}</Notice>}

      <Form
        action="/admin/users"
        key={params.toString()}
        className="grid gap-16 sm:grid-cols-[1fr_200px_auto] sm:items-end"
      >
        <Field
          label="Tìm kiếm theo Email hoặc Họ tên"
          name="q"
          defaultValue={q}
          placeholder="Nhập email hoặc tên..."
        />
        <SelectField label="Vai trò" name="role" defaultValue={role}>
          <option value="">Tất cả vai trò</option>
          <option value="CUSTOMER">Khách hàng</option>
          <option value="STAFF">Nhân viên</option>
          <option value="ADMIN">Quản trị viên</option>
        </SelectField>
        <button type="submit" className={pillPrimary}>
          Tìm kiếm
        </button>
      </Form>

      {usersRes === undefined ? (
        <FormSkeleton rows={3} />
      ) : "error" in usersRes ? (
        <Notice>{usersRes.error.detail}</Notice>
      ) : (
        <>
          {usersRes.data.content.length === 0 ? (
            <p className="text-muted">Không có tài khoản nào khớp bộ lọc.</p>
          ) : (
            <div className={tableBox}>
              <table
                aria-label="Bảng người dùng"
                className="w-full border-collapse"
              >
                <thead>
                  <tr>
                    <th className={th}>ID</th>
                    <th className={th}>Họ tên</th>
                    <th className={th}>Email</th>
                    <th className={th}>Số điện thoại</th>
                    <th className={th}>Vai trò</th>
                    <th className={th}>Trạng thái</th>
                    <th className={th}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {usersRes.data.content.map((u) => {
                    const isMe = currentUser?.id === u.id;
                    return (
                      <tr key={u.id}>
                        <td className={td}>#{u.id}</td>
                        <td className={`${td} font-bold`}>{u.fullName}</td>
                        <td className={td}>{u.email}</td>
                        <td className={td}>{u.phone}</td>
                        <td className={td}>{ROLE_LABEL[u.role] ?? u.role}</td>
                        <td className={td}>
                          {u.status === "ACTIVE" ? (
                            <span className="font-bold text-ink">
                              Hoạt động
                            </span>
                          ) : (
                            <span className="font-semibold text-danger">
                              Đã khoá
                            </span>
                          )}
                        </td>
                        <td className={td}>
                          {isMe ? (
                            <span
                              className="text-muted text-caption"
                              title="BR-105: Không thể tự khoá chính mình"
                            >
                              (Chính bạn)
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() => {
                                setActionError(null);
                                setToggleTarget(u);
                              }}
                              className={textLink}
                            >
                              {u.status === "ACTIVE"
                                ? "Khoá tài khoản"
                                : "Mở khoá"}
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          <Pager
            path="/admin/users"
            params={params}
            totalPages={usersRes.data.page.totalPages}
          />
        </>
      )}

      {/* Modal Tạo tài khoản mới */}
      <AdminModal
        title="Tạo tài khoản Staff hoặc Admin mới"
        isOpen={createOpen}
        onClose={() => setCreateOpen(false)}
      >
        <form onSubmit={handleCreate} className="grid gap-16">
          {actionError && <Notice>{actionError}</Notice>}
          <Field
            label="Họ và tên"
            name="fullName"
            required
            placeholder="Nguyễn Văn A"
          />
          <Field
            label="Email đăng nhập"
            name="email"
            type="email"
            required
            placeholder="staff.new@skyline.vn"
          />
          <div className="grid grid-cols-2 gap-16">
            <Field
              label="Số điện thoại"
              name="phone"
              type="tel"
              required
              placeholder="0912345678"
            />
            <SelectField
              label="Vai trò cấp phát"
              name="role"
              defaultValue="STAFF"
            >
              <option value="STAFF">Nhân viên (STAFF)</option>
              <option value="ADMIN">Quản trị viên (ADMIN)</option>
            </SelectField>
          </div>
          <Field
            label="Mật khẩu khởi tạo"
            name="password"
            type="password"
            required
            pattern={PASSWORD_PATTERN}
            invalidMessage={PASSWORD_RULE}
            hint={PASSWORD_RULE}
          />
          <div className="flex justify-end gap-11 mt-11">
            <button
              type="button"
              onClick={() => setCreateOpen(false)}
              className={pillLine}
              disabled={loading}
            >
              Huỷ
            </button>
            <button type="submit" className={pillPrimary} disabled={loading}>
              {loading ? "Đang tạo..." : "Tạo tài khoản"}
            </button>
          </div>
        </form>
      </AdminModal>

      {/* Confirm Modal Khoá/Mở khoá */}
      <ConfirmModal
        title={
          toggleTarget?.status === "ACTIVE"
            ? `Khoá tài khoản ${toggleTarget?.email}?`
            : `Mở khoá tài khoản ${toggleTarget?.email}?`
        }
        message={
          toggleTarget?.status === "ACTIVE"
            ? `Tài khoản ${toggleTarget?.fullName} (${toggleTarget?.email}) sẽ bị mất hiệu lực ngay lập tức và không thể đăng nhập cho đến khi được mở lại (BR-102).`
            : `Tài khoản ${toggleTarget?.fullName} (${toggleTarget?.email}) sẽ được phục hồi quyền truy cập hệ thống bình thường.`
        }
        confirmLabel={
          toggleTarget?.status === "ACTIVE" ? "Khoá tài khoản" : "Mở khoá"
        }
        isOpen={toggleTarget !== null}
        isLoading={loading}
        onConfirm={handleToggleStatus}
        onClose={() => setToggleTarget(null)}
      />
    </AdminPage>
  );
}
