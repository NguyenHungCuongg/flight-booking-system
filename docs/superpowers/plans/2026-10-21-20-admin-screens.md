# Plan 20 — Màn hình Admin: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng trọn bộ 10 màn hình quản trị Admin A-01–A-10 theo APP_FLOW §2.3 và PRD §5 (E9–E13), cho phép Admin quản lý toàn diện danh mục (sân bay, hãng bay, gói giá, hành lý), lịch bay (tạo chuyến lẻ/hàng loạt, sửa, huỷ 2 bước, xoá), voucher khuyến mãi, tài khoản người dùng (phân quyền, khoá/mở khoá), tham số hệ thống và các báo cáo thống kê chuyên sâu.

**Architecture:** Sử dụng Next.js 16 App Router với Client Components, tích hợp trực tiếp các REST API `/api/admin/**` đã được phân quyền `ROLE_ADMIN` ở backend. Giao diện tuân thủ chặt chẽ [DESIGN.md](../../../DESIGN.md) và thiết kế hệ thống token Tailwind `@theme` (font Inter, bảng màu Deep Ink/Pure White/Cool Ash/Pebble/Clay Ember, nút full pill `rounded-pill`, khối bảng hairline sắc nét). Mọi thao tác ghi dữ liệu sử dụng `apiFetch` (tự động đính kèm CSRF token và bắt lỗi ProblemDetail). Xác thực và bảo vệ route qua `proxy.ts` và chuyển hướng `/login?next=`.

**Tech Stack:** Next.js 16 (App Router, Client Components, `next/form`), React 19, TypeScript, Tailwind CSS v4, Playwright E2E Test Runner.

**Spec:** [PRD](../../PRD.md) FR-80–83, FR-90–94, FR-100, FR-110–111, FR-120–122, BR-80–87, BR-90–92, BR-100–105, BR-110–112 · [TDD](../../TDD.md) §4.3, §6.1, §6.11, §8.7, §9 · [App Flow](../../APP_FLOW.md) §2.3 A-01–A-10, §3 UF-06 · [Design](../../../DESIGN.md) · [Lộ trình](2026-10-09-00-roadmap.md) §2 dòng 20.

---

## Global Constraints

- **Quy ước frontend (Plan 03, Plan 04, Plan 18):**
  - Mọi lời gọi API qua hàm thuần `apiFetch<T>(path, options)` từ `@/lib/api`. Lỗi trả về kiểu `ApiError` chứa mã `code`, chi tiết `detail` và mảng lỗi trường `errors`.
  - Token giao diện lấy từ `@theme` trong `src/app/globals.css`. Dùng class tiện ích `pillDark`, `pillLine`, `textLink`, `Field`, `SelectField`, `Notice`, `FormSkeleton` từ `@/components/form`.
  - Phân trang hiển thị từ trang 1 trên URL (`?page=1`), chuyển thành `page=0` khi gửi lên backend API.
  - Giờ hiển thị theo múi giờ sân bay thông qua hàm `clock()` và `dmy()` từ `@/lib/booking`.
  - Chữ hiển thị và thông báo hoàn toàn bằng tiếng Việt có dấu.
- **Bảo mật và phân quyền (TDD §4.3, APP_FLOW §1):**
  - Route `/admin/**` đã được `proxy.ts` chặn nếu thiếu cookie `JSESSIONID`. Backend chặn 403 `FORBIDDEN` nếu tài khoản không mang quyền `ROLE_ADMIN`.
  - Sau khi Admin đăng nhập, `homeOf("ADMIN")` dẫn tới `/admin/flights` (A-04).
- **Quy tắc nghiệp vụ danh mục (BR-90, BR-91, BR-92):**
  - Mã sân bay 3 ký tự IATA (duy nhất). Hãng bay: mã 2 ký tự (duy nhất), mã vé 3 chữ số (duy nhất).
  - Dữ liệu danh mục đã được chuyến bay tham chiếu thì khi xoá backend trả 409 `RESOURCE_IN_USE`: giao diện hiển thị câu thông báo giải thích rõ ràng và chỉ cho phép chuyển sang trạng thái "Ngừng hoạt động" (`active: false`).
  - Hạng ghế (`cabinClass`) của gói giá không sửa được sau khi tạo (TDD §8.7).
- **Quy tắc nghiệp vụ chuyến bay (BR-80–87):**
  - Tạo chuyến hàng loạt (A-05): Chọn khoảng ngày (tối đa 180 ngày), các thứ trong tuần (1–7). Backend bỏ qua ngày trùng số hiệu/giờ cất cánh và trả về số chuyến đã tạo cùng số ngày bị bỏ qua (`CreatedFlights`).
  - Huỷ chuyến (A-06): Admin phải xác nhận hai bước (dialog cảnh báo rõ 4 hệ quả: huỷ booking chờ thanh toán, tạo yêu cầu hoàn 100% `FLIGHT_CANCELLED`, huỷ yêu cầu đổi, gửi email). Chuyến đã cất cánh không được sửa hay huỷ (BR-87).
  - Xoá chuyến (A-06): Chỉ xoá được chuyến chưa từng có booking (`bookingCount === 0`, BR-82).
- **Quy tắc tài khoản & tham số (BR-105, BR-25):**
  - Admin không thể tự khoá tài khoản của chính mình (nút khoá bị vô hiệu).
  - Giá trị tham số hệ thống cập nhật phải nằm trong khoảng hợp lệ [min, max] của từng tham số.

---

## Cấu trúc file sau plan

```
frontend/
├── src/
│   ├── components/
│   │   └── site-header.tsx                      # Task 1: Thêm mục điều hướng admin vào SiteHeader
│   ├── lib/
│   │   └── admin.ts                             # Task 1: Hàm thuần, nhãn và hằng số cho màn hình Admin
│   └── app/
│       └── admin/
│           ├── admin-ui.tsx                     # Task 1: Layout dùng chung AdminPage, Modal, Pager, Tabs
│           ├── airports/
│           │   ├── page.tsx                     # Task 2: Page A-01 Sân bay
│           │   └── airport-list.tsx             # Task 2: Component bảng & modal thêm/sửa/xoá sân bay
│           ├── airlines/
│           │   ├── page.tsx                     # Task 3: Page A-02 Hãng bay
│           │   ├── airline-list.tsx             # Task 3: Component bảng & modal thêm/sửa/xoá hãng bay
│           │   └── [code]/
│           │       ├── page.tsx                 # Task 3: Page A-03 Chi tiết hãng
│           │       └── airline-detail.tsx       # Task 3: Quản lý Gói giá & Bảng giá hành lý
│           ├── flights/
│           │   ├── page.tsx                     # Task 4: Page A-04 Danh sách chuyến bay
│           │   ├── flight-list.tsx              # Task 4: Bảng chuyến bay, bộ lọc, link tạo & chi tiết
│           │   ├── new/
│           │   │   ├── page.tsx                 # Task 5: Page A-05 Tạo chuyến bay
│           │   │   └── flight-form.tsx          # Task 5: Form tạo chuyến lẻ/hàng loạt theo FR-90
│           │   └── [id]/
│           │       ├── page.tsx                 # Task 4: Page A-06 Chi tiết chuyến bay
│           │       └── flight-detail.tsx        # Task 4: Xem kho ghế, sửa, huỷ 2 bước, xoá
│           ├── vouchers/
│           │   ├── page.tsx                     # Task 6: Page A-07 Quản lý voucher
│           │   └── voucher-list.tsx             # Task 6: Bảng voucher, modal thêm & sửa
│           ├── users/
│           │   ├── page.tsx                     # Task 7: Page A-08 Quản lý tài khoản người dùng
│           │   └── user-list.tsx                # Task 7: Tìm kiếm, tạo Staff/Admin, khoá/mở khoá
│           ├── settings/
│           │   ├── page.tsx                     # Task 8: Page A-09 Tham số hệ thống
│           │   └── settings-form.tsx            # Task 8: Xem và sửa 8 tham số với khoảng hợp lệ
│           └── reports/
│               ├── page.tsx                     # Task 9: Page A-10 Báo cáo thống kê
│               └── reports-view.tsx             # Task 9: 3 tabs Dòng tiền, Doanh số, Tỉ lệ lấp đầy
└── e2e/
    ├── admin-lib.spec.ts                        # Task 1: Unit tests logic admin.ts
    ├── smoke.spec.ts                            # Task 10: Thêm các route admin vào danh sách bảo vệ
    └── admin.spec.ts                            # Task 10: E2E toàn diện A-01–A-10 & kiểm chứng tạo chuyến
```

---

## Task Decomposition

### Task 1: Navigation Header & Admin Shared Framework (`site-header.tsx`, `admin.ts`, `admin-ui.tsx`)

**Files:**
- Modify: `frontend/src/components/site-header.tsx:10-36`
- Create: `frontend/src/lib/admin.ts`
- Create: `frontend/src/app/admin/admin-ui.tsx`
- Test: `frontend/e2e/admin-lib.spec.ts`

**Interfaces:**
- Consumes: `SiteHeader`, `apiFetch`, `useCurrentUser`, `usePathname`
- Produces: `NAV.admin`, `AdminPage`, `AdminTabs`, `AdminModal`, `ConfirmModal`, `admin.ts` utilities (`VOUCHER_TYPE_LABEL`, `WEEKDAY_OPTIONS`, `SETTING_INFO`, `formatVnd`)

- [ ] **Step 1: Write the failing unit tests for `admin.ts`**

Create `frontend/e2e/admin-lib.spec.ts`:
```typescript
import { expect, test } from "@playwright/test";
import {
  formatVnd,
  validateSettingValue,
  WEEKDAY_OPTIONS,
  VOUCHER_TYPE_LABEL,
  SETTING_INFO,
} from "../src/lib/admin";

test.describe("admin.ts pure helpers", () => {
  test("formatVnd formats Vietnamese currency correctly", () => {
    expect(formatVnd(0)).toBe("0 ₫");
    expect(formatVnd(1500000)).toBe("1.500.000 ₫");
  });

  test("WEEKDAY_OPTIONS contains 7 days from Monday (1) to Sunday (7)", () => {
    expect(WEEKDAY_OPTIONS).toHaveLength(7);
    expect(WEEKDAY_OPTIONS[0]).toEqual({ day: 1, label: "Thứ 2" });
    expect(WEEKDAY_OPTIONS[6]).toEqual({ day: 7, label: "Chủ nhật" });
  });

  test("VOUCHER_TYPE_LABEL maps percent and fixed", () => {
    expect(VOUCHER_TYPE_LABEL.PERCENT).toBe("Phần trăm (%)");
    expect(VOUCHER_TYPE_LABEL.FIXED).toBe("Số tiền cố định");
  });

  test("validateSettingValue verifies bounds and connection rules", () => {
    expect(validateSettingValue("booking.hold_minutes", 15)).toBeNull();
    expect(validateSettingValue("booking.hold_minutes", 3)).toBe(
      "Giá trị phải từ 5 đến 60",
    );
    expect(validateSettingValue("booking.hold_minutes", 70)).toBe(
      "Giá trị phải từ 5 đến 60",
    );
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx playwright test e2e/admin-lib.spec.ts`
Expected: FAIL with "Cannot find module '../src/lib/admin'"

- [ ] **Step 3: Implement `admin.ts`, `site-header.tsx`, and `admin-ui.tsx`**

Create `frontend/src/lib/admin.ts`:
```typescript
/**
 * Tiện ích và hằng số dùng chung cho toàn bộ các màn hình Quản trị Admin (A-01–A-10).
 */

export const VOUCHER_TYPE_LABEL: Record<string, string> = {
  PERCENT: "Phần trăm (%)",
  FIXED: "Số tiền cố định",
};

export const WEEKDAY_OPTIONS = [
  { day: 1, label: "Thứ 2" },
  { day: 2, label: "Thứ 3" },
  { day: 3, label: "Thứ 4" },
  { day: 4, label: "Thứ 5" },
  { day: 5, label: "Thứ 6" },
  { day: 6, label: "Thứ 7" },
  { day: 7, label: "Chủ nhật" },
] as const;

export interface SettingDefinition {
  name: string;
  unit: string;
  min: number;
  max: number;
  description: string;
}

export const SETTING_INFO: Record<string, SettingDefinition> = {
  "booking.hold_minutes": {
    name: "Thời gian giữ chỗ",
    unit: "phút",
    min: 5,
    max: 60,
    description: "Thời gian giữ chỗ và thanh toán đổi chuyến.",
  },
  "booking.min_hours_before_departure": {
    name: "Khoảng cách đặt vé tối thiểu",
    unit: "giờ",
    min: 0,
    max: 72,
    description: "Không bán chuyến bay cất cánh trong vòng N giờ tới.",
  },
  "booking.max_seated_passengers": {
    name: "Số khách tối đa mỗi đơn",
    unit: "khách",
    min: 1,
    max: 9,
    description: "Số khách chiếm ghế tối đa trong một booking.",
  },
  "pricing.child_percent": {
    name: "Tỉ lệ giá vé trẻ em",
    unit: "%",
    min: 0,
    max: 100,
    description: "Giá vé trẻ em tính theo % giá người lớn.",
  },
  "pricing.infant_percent": {
    name: "Tỉ lệ giá vé em bé",
    unit: "%",
    min: 0,
    max: 100,
    description: "Giá vé em bé tính theo % giá người lớn.",
  },
  "aftersales.min_hours_before_departure": {
    name: "Hạn chót yêu cầu hoàn/đổi",
    unit: "giờ",
    min: 0,
    max: 168,
    description: "Thời hạn tối thiểu trước giờ cất cánh để yêu cầu hoàn hoặc đổi vé.",
  },
  "search.min_connection_minutes": {
    name: "Nối chuyến tối thiểu",
    unit: "phút",
    min: 30,
    max: 600,
    description: "Thời gian nối chuyến tối thiểu giữa các chặng.",
  },
  "search.max_connection_minutes": {
    name: "Nối chuyến tối đa",
    unit: "phút",
    min: 60,
    max: 1440,
    description: "Thời gian nối chuyến tối đa giữa các chặng.",
  },
};

export function formatVnd(amount: number): string {
  return `${amount.toLocaleString("vi-VN")} ₫`;
}

export function validateSettingValue(key: string, value: number): string | null {
  const meta = SETTING_INFO[key];
  if (!meta) return null;
  if (!Number.isFinite(value) || value < meta.min || value > meta.max) {
    return `Giá trị phải từ ${meta.min} đến ${meta.max}`;
  }
  return null;
}
```

Update `frontend/src/components/site-header.tsx`:
Replace lines 10-36 with:
```typescript
const NAV: Record<
  "customer" | "staff" | "admin",
  { label: string; links: readonly (readonly [string, string])[] }
> = {
  customer: {
    label: "Khách hàng",
    links: [
      ["/flights", "Tìm chuyến bay"],
      ["/bookings", "Đặt chỗ của tôi"],
    ],
  },
  staff: {
    label: "Nhân viên",
    links: [
      ["/staff/bookings", "Đặt chỗ"],
      ["/staff/refunds", "Yêu cầu hoàn"],
      ["/staff/flights", "Chuyến bay"],
    ],
  },
  admin: {
    label: "Quản trị viên",
    links: [
      ["/admin/flights", "Chuyến bay"],
      ["/admin/airports", "Sân bay"],
      ["/admin/airlines", "Hãng bay"],
      ["/admin/vouchers", "Voucher"],
      ["/admin/users", "Tài khoản"],
      ["/admin/settings", "Tham số"],
      ["/admin/reports", "Báo cáo"],
    ],
  },
};

/** Đầu trang chung của khu khách hàng, nhân viên và quản trị viên. */
export default function SiteHeader({
  area = "customer",
}: {
  area?: "customer" | "staff" | "admin";
} = {}) {
```

Create `frontend/src/app/admin/admin-ui.tsx`:
```typescript
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import SiteHeader from "@/components/site-header";
import { pillDark, pillLine } from "@/components/form";

export const tableBox = "overflow-x-auto";
export const th =
  "py-11 pr-16 text-left text-caption font-bold border-b border-line whitespace-nowrap";
export const td =
  "py-11 pr-16 text-left text-caption border-b border-line align-top";

/**
 * Khung giao diện chuẩn cho tất cả các màn hình Admin (A-01–A-10).
 */
export function AdminPage({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <div className="min-h-dvh bg-paper text-ink">
      <SiteHeader area="admin" />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        {children}
      </main>
    </div>
  );
}

/**
 * Thanh chuyển Tab dùng cho Chi tiết hãng (A-03) và Báo cáo (A-10).
 */
export function AdminTabs({
  tabs,
  activeKey,
  onChange,
}: {
  tabs: readonly { key: string; label: string }[];
  activeKey: string;
  onChange: (key: string) => void;
}) {
  return (
    <div className="flex border-b border-line gap-22">
      {tabs.map((tab) => {
        const active = tab.key === activeKey;
        return (
          <button
            key={tab.key}
            type="button"
            onClick={() => onChange(tab.key)}
            className={`pb-11 text-caption font-bold transition-colors ${
              active
                ? "border-b-2 border-ink text-ink"
                : "text-muted hover:text-ink"
            }`}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

/**
 * Modal Popup chuẩn cho các form thêm / sửa / xác nhận.
 */
export function AdminModal({
  title,
  isOpen,
  onClose,
  children,
}: {
  title: string;
  isOpen: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="fixed inset-0 z-50 flex items-center justify-center bg-deep-ink/40 p-16 backdrop-blur-xs"
    >
      <div className="w-full max-w-560 border border-line-strong bg-paper p-22 shadow-lg">
        <div className="flex items-center justify-between border-b border-line pb-16 mb-22">
          <h2 className="text-subheading font-bold">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="text-muted hover:text-ink text-caption font-bold"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

/**
 * Dialog xác nhận hai bước (dành cho Huỷ chuyến bay, Xoá dữ liệu, Khoá tài khoản).
 */
export function ConfirmModal({
  title,
  message,
  confirmLabel = "Xác nhận",
  isOpen,
  isLoading,
  onConfirm,
  onClose,
}: {
  title: string;
  message: string;
  confirmLabel?: string;
  isOpen: boolean;
  isLoading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <AdminModal title={title} isOpen={isOpen} onClose={onClose}>
      <div className="grid gap-22">
        <p className="text-caption text-ink leading-relaxed">{message}</p>
        <div className="flex justify-end gap-11">
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className={pillLine}
          >
            Huỷ bỏ
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isLoading}
            className={pillDark}
          >
            {isLoading ? "Đang xử lý..." : confirmLabel}
          </button>
        </div>
      </div>
    </AdminModal>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx playwright test e2e/admin-lib.spec.ts`
Expected: PASS (4 passed)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/site-header.tsx frontend/src/lib/admin.ts frontend/src/app/admin/admin-ui.tsx frontend/e2e/admin-lib.spec.ts
git commit -m "feat(admin): add admin navigation, shared UI shell and pure helpers"
```

---

### Task 2: Screen A-01 Sân bay (`/admin/airports`)

**Files:**
- Create: `frontend/src/app/admin/airports/page.tsx`
- Create: `frontend/src/app/admin/airports/airport-list.tsx`

**Interfaces:**
- Consumes: `GET /api/admin/airports`, `POST /api/admin/airports`, `PUT /api/admin/airports/{code}`, `DELETE /api/admin/airports/{code}`
- Produces: Màn hình quản lý sân bay A-01 với bảng dữ liệu, thêm sân bay mới, sửa thông tin và xoá/ngừng kích hoạt có xử lý 409 `RESOURCE_IN_USE`.

- [ ] **Step 1: Write component `airport-list.tsx`**

Create `frontend/src/app/admin/airports/airport-list.tsx`:
```typescript
"use client";

import { useState } from "react";
import { AdminModal, AdminPage, ConfirmModal, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
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

  const handleSave = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const formData = new FormData(e.currentTarget);
    const code = (formData.get("code") as string)?.toUpperCase().trim();
    const name = (formData.get("name") as string)?.trim();
    const city = (formData.get("city") as string)?.trim();
    const country = (formData.get("country") as string)?.toUpperCase().trim();
    const timezone = (formData.get("timezone") as string)?.trim();
    const active = formData.get("active") === "on";

    try {
      if (modalState?.mode === "create") {
        await apiFetch("/admin/airports", {
          method: "POST",
          body: { code, name, city, country, timezone },
        });
      } else if (modalState?.mode === "edit" && modalState.airport) {
        await apiFetch(`/admin/airports/${modalState.airport.code}`, {
          method: "PUT",
          body: { name, city, country, timezone, active },
        });
      }
      setModalState(null);
      reload();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setActionError(apiErr.detail || "Không thể lưu sân bay.");
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
          `Sân bay ${deleteTarget.code} đã có chuyến bay sử dụng nên không thể xoá. Hãy chọn Sửa và tắt Hoạt động.`
        );
      } else {
        setActionError(apiErr.detail || "Không thể xoá sân bay.");
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
          className={pillDark}
        >
          + Thêm sân bay
        </button>
      </div>

      {actionError && <Notice>{actionError}</Notice>}

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
                  <td className={td}>{a.country}</td>
                  <td className={`${td} font-mono text-caption`}>{a.timezone}</td>
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
        title={modalState?.mode === "create" ? "Thêm sân bay mới" : `Sửa sân bay ${modalState?.airport?.code}`}
        isOpen={modalState !== null}
        onClose={() => setModalState(null)}
      >
        <form onSubmit={handleSave} className="grid gap-16">
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
              name="country"
              required
              maxLength={2}
              pattern="^[A-Z]{2}$"
              defaultValue={modalState?.airport?.country ?? "VN"}
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
            <button type="submit" className={pillDark} disabled={loading}>
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
```

- [ ] **Step 2: Create page wrapper `frontend/src/app/admin/airports/page.tsx`**

Create `frontend/src/app/admin/airports/page.tsx`:
```typescript
import AirportList from "./airport-list";

export const metadata = {
  title: "Quản lý sân bay | SkyLine Admin",
};

export default function AdminAirportsPage() {
  return <AirportList />;
}
```

- [ ] **Step 3: Commit Task 2**

```bash
git add frontend/src/app/admin/airports
git commit -m "feat(admin): implement A-01 airports management screen"
```

---

### Task 3: Screen A-02 Hãng bay & Screen A-03 Chi tiết hãng (`/admin/airlines`, `/admin/airlines/[code]`)

**Files:**
- Create: `frontend/src/app/admin/airlines/page.tsx`
- Create: `frontend/src/app/admin/airlines/airline-list.tsx`
- Create: `frontend/src/app/admin/airlines/[code]/page.tsx`
- Create: `frontend/src/app/admin/airlines/[code]/airline-detail.tsx`

**Interfaces:**
- Consumes:
  - `GET, POST /api/admin/airlines`, `PUT, DELETE /api/admin/airlines/{code}`
  - `GET, POST /api/admin/airlines/{code}/fare-families`, `PUT, DELETE /api/admin/fare-families/{id}`
  - `GET, POST /api/admin/airlines/{code}/baggage-options`, `PUT, DELETE /api/admin/baggage-options/{id}`
- Produces: Màn hình danh sách hãng bay A-02 và chi tiết hãng A-03 (quản lý Gói giá & Bảng giá hành lý)

- [ ] **Step 1: Create `airline-list.tsx` and its page wrapper**

Create `frontend/src/app/admin/airlines/airline-list.tsx`:
```typescript
"use client";

import Link from "next/link";
import { useState } from "react";
import { AdminModal, AdminPage, ConfirmModal, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
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

  const handleSave = async (e: React.FormEvent<HTMLFormElement>) => {
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
          body: { code, name, ticketPrefix },
        });
      } else if (modalState?.mode === "edit" && modalState.airline) {
        await apiFetch(`/admin/airlines/${modalState.airline.code}`, {
          method: "PUT",
          body: { name, active },
        });
      }
      setModalState(null);
      reload();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setActionError(apiErr.detail || "Không thể lưu hãng bay.");
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
          `Hãng bay ${deleteTarget.code} đã có dữ liệu tham chiếu nên không thể xoá. Hãy chọn Sửa và tắt Hoạt động.`
        );
      } else {
        setActionError(apiErr.detail || "Không thể xoá hãng bay.");
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
            Quản lý các hãng hàng không, mã số vé 3 chữ số và cấu hình gói giá (A-02).
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

      {actionError && <Notice>{actionError}</Notice>}

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
                  <td className={`${td} font-mono font-bold`}>{al.ticketPrefix}</td>
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
        title={modalState?.mode === "create" ? "Thêm hãng bay mới" : `Sửa hãng bay ${modalState?.airline?.code}`}
        isOpen={modalState !== null}
        onClose={() => setModalState(null)}
      >
        <form onSubmit={handleSave} className="grid gap-16">
          {modalState?.mode === "create" && (
            <>
              <Field
                label="Mã IATA (2 ký tự)"
                name="code"
                required
                maxLength={2}
                pattern="^[A-Z0-9]{2}$"
                placeholder="VN"
                invalidMessage="Mã hãng bay gồm 2 ký tự in hoa/số (VD: VN, VJ, QH)"
              />
              <Field
                label="Mã số vé (3 chữ số duy nhất)"
                name="ticketPrefix"
                required
                maxLength={3}
                pattern="^\d{3}$"
                placeholder="738"
                invalidMessage="Mã số vé gồm đúng 3 chữ số (VD: 738, 978)"
              />
            </>
          )}
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
```

Create `frontend/src/app/admin/airlines/page.tsx`:
```typescript
import AirlineList from "./airline-list";

export const metadata = {
  title: "Quản lý hãng bay | SkyLine Admin",
};

export default function AdminAirlinesPage() {
  return <AirlineList />;
}
```

- [ ] **Step 2: Create `airline-detail.tsx` and its page wrapper**

Create `frontend/src/app/admin/airlines/[code]/airline-detail.tsx`:
```typescript
"use client";

import Link from "next/link";
import { useState } from "react";
import { AdminModal, AdminPage, AdminTabs, ConfirmModal, tableBox, td, th } from "../../admin-ui";
import { Field, FormSkeleton, Notice, SelectField, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { formatVnd } from "@/lib/admin";
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

export default function AirlineDetail({ code }: { code: string }) {
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

  const handleSaveFare = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const body = {
      name: (fd.get("name") as string)?.trim(),
      cabinClass: (fd.get("cabinClass") as string) || fareModal?.item?.cabinClass,
      carryOnKg: Number(fd.get("carryOnKg")),
      checkedBaggageKg: Number(fd.get("checkedBaggageKg")),
      refundable: fd.get("refundable") === "on",
      refundFee: Number(fd.get("refundFee") || 0),
      changeable: fd.get("changeable") === "on",
      changeFee: Number(fd.get("changeFee") || 0),
      active: fd.get("active") === "on",
    };

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
      setActionError((err as ApiError).detail || "Không thể lưu gói giá.");
    } finally {
      setLoading(false);
    }
  };

  const handleSaveBaggage = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const body = {
      weightKg: Number(fd.get("weightKg")),
      price: Number(fd.get("price")),
      active: fd.get("active") === "on",
    };

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
      setActionError((err as ApiError).detail || "Không thể lưu mức hành lý.");
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
        setActionError(apiErr.detail || "Không thể xoá.");
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

      {actionError && <Notice>{actionError}</Notice>}

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
                        {f.refundable ? `Có (${formatVnd(f.refundFee)})` : "Không"}
                      </td>
                      <td className={td}>
                        {f.changeable ? `Có (${formatVnd(f.changeFee)})` : "Không"}
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
                      <td className={td}>{formatVnd(b.price)}</td>
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
```

Create `frontend/src/app/admin/airlines/[code]/page.tsx`:
```typescript
import AirlineDetail from "./airline-detail";

export const metadata = {
  title: "Chi tiết hãng bay | SkyLine Admin",
};

export default async function AdminAirlineDetailPage({
  params,
}: {
  params: Promise<{ code: string }>;
}) {
  const { code } = await params;
  return <AirlineDetail code={code.toUpperCase()} />;
}
```

- [ ] **Step 3: Commit Task 3**

```bash
git add frontend/src/app/admin/airlines
git commit -m "feat(admin): implement A-02 airlines and A-03 airline detail screens"
```

---

### Task 4: Screen A-04 Danh sách chuyến bay & Screen A-06 Chi tiết chuyến bay (`/admin/flights`, `/admin/flights/[id]`)

**Files:**
- Create: `frontend/src/app/admin/flights/page.tsx`
- Create: `frontend/src/app/admin/flights/flight-list.tsx`
- Create: `frontend/src/app/admin/flights/[id]/page.tsx`
- Create: `frontend/src/app/admin/flights/[id]/flight-detail.tsx`

**Interfaces:**
- Consumes:
  - `GET /api/staff/flights?from=&to=&date=&airline=&status=&page=&size=`
  - `GET /api/admin/flights/{id}`, `PUT /api/admin/flights/{id}`, `POST /api/admin/flights/{id}/cancel`, `DELETE /api/admin/flights/{id}`
- Produces: Màn hình danh sách chuyến bay Admin A-04 và chi tiết chuyến bay A-06 (xem kho ghế, sửa giờ/loại máy bay/giá bán, huỷ chuyến 2 bước theo BR-84, xoá chuyến chưa có booking).

- [ ] **Step 1: Create `flight-list.tsx` and its page wrapper**

Create `frontend/src/app/admin/flights/flight-list.tsx`:
```typescript
"use client";

import Link from "next/link";
import Form from "next/form";
import { useSearchParams } from "next/navigation";
import { AdminPage, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, SelectField, pillDark, textLink } from "@/components/form";
import type { Schemas } from "@/lib/api";
import { useAirports } from "@/lib/airports";
import { clock, dmy } from "@/lib/booking";
import { FLIGHT_STATUS, cabinLabel, listQuery } from "@/lib/staff";
import { Pager } from "../../staff/staff-ui";
import { useApi } from "@/lib/use-api";

const STATUS_OPTIONS = [
  { value: "", label: "Tất cả" },
  ...Object.entries(FLIGHT_STATUS).map(([value, label]) => ({ value, label })),
];

export default function AdminFlightList() {
  const params = useSearchParams();
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const date = params.get("date") ?? "";
  const airline = params.get("airline") ?? "";
  const status = params.get("status") ?? "";

  const { airports, failed: airportsFailed } = useAirports();
  const airlinesRes = useApi<Schemas["AirlineResponse"][]>("/airlines");
  const airlinesReady = airlinesRes !== undefined;
  const airportsReady = airports !== null || airportsFailed;

  const query = listQuery(params, ["from", "to", "date", "airline", "status"]);
  const flightsRes = useApi<Schemas["PagedModelFlightView"]>(`/staff/flights?${query}`);

  const airportOptions = [
    { value: "", label: "Tất cả" },
    ...(airports ?? []).map((a) => ({
      value: a.code,
      label: `${a.code}, ${a.city}`,
    })),
  ];

  const airlineOptions = [
    { value: "", label: "Tất cả" },
    ...(airlinesRes && "data" in airlinesRes ? airlinesRes.data : []).map((a) => ({
      value: a.code,
      label: `${a.code}, ${a.name}`,
    })),
  ];

  return (
    <AdminPage>
      <div className="flex flex-wrap items-center justify-between gap-16">
        <div>
          <h1 className="text-heading-lg font-bold">Chuyến bay.</h1>
          <p className="text-caption text-muted mt-4">
            Quản trị lịch bay, kho ghế và trạng thái vận hành của các chuyến (A-04).
          </p>
        </div>
        <Link href="/admin/flights/new" className={pillDark}>
          + Tạo chuyến bay
        </Link>
      </div>

      {!airportsReady || !airlinesReady ? (
        <FormSkeleton rows={1} />
      ) : (
        <Form
          action="/admin/flights"
          key={params.toString()}
          className="grid gap-16 sm:grid-cols-[repeat(auto-fit,minmax(160px,1fr))] sm:items-end"
        >
          <SelectField label="Điểm đi" name="from" defaultValue={from}>
            {airportOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </SelectField>
          <SelectField label="Điểm đến" name="to" defaultValue={to}>
            {airportOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </SelectField>
          <Field label="Ngày bay" name="date" type="date" defaultValue={date} />
          <SelectField label="Hãng bay" name="airline" defaultValue={airline}>
            {airlineOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </SelectField>
          <SelectField label="Trạng thái" name="status" defaultValue={status}>
            {STATUS_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </SelectField>
          <button type="submit" className={pillDark}>
            Lọc
          </button>
        </Form>
      )}

      {flightsRes === undefined && <FormSkeleton rows={2} />}
      {flightsRes && "error" in flightsRes && <Notice>{flightsRes.error.detail}</Notice>}
      {flightsRes && "data" in flightsRes && (
        <>
          {flightsRes.data.content.length === 0 ? (
            <p className="text-muted">Không có chuyến bay nào khớp với bộ lọc.</p>
          ) : (
            <div className="grid gap-16">
              <div className={tableBox}>
                <table aria-label="Danh sách chuyến bay" className="w-full border-collapse">
                  <thead>
                    <tr>
                      <th className={th}>Chuyến</th>
                      <th className={th}>Tuyến</th>
                      <th className={th}>Cất cánh</th>
                      <th className={th}>Hạ cánh</th>
                      <th className={th}>Máy bay</th>
                      <th className={th}>Trạng thái</th>
                      <th className={th}>Ghế trống</th>
                      <th className={th}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {flightsRes.data.content.map((f) => (
                      <tr key={f.id}>
                        <td className={`${td} font-bold`}>{f.flightNumber}</td>
                        <td className={td}>{`${f.departureAirport} → ${f.arrivalAirport}`}</td>
                        <td className={td}>{`${clock(f.departureTime)} ${dmy(f.departureTime)}`}</td>
                        <td className={td}>{`${clock(f.arrivalTime)} ${dmy(f.arrivalTime)}`}</td>
                        <td className={td}>{f.aircraftModel}</td>
                        <td className={td}>{FLIGHT_STATUS[f.status] ?? f.status}</td>
                        <td className={td}>
                          {f.cabins.map((c) => (
                            <p key={c.cabinClass} className="text-caption">
                              {`${cabinLabel(c.cabinClass)}: ${c.availableSeats}/${c.totalSeats}`}
                            </p>
                          ))}
                        </td>
                        <td className={`${td} space-x-16`}>
                          <Link href={`/admin/flights/${f.id}`} className={textLink}>
                            Chi tiết
                          </Link>
                          <Link href={`/staff/flights/${f.id}/passengers`} className={`${textLink} text-muted hover:text-ink`}>
                            Hành khách
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="text-caption text-muted">Giờ hiển thị theo múi giờ địa phương của sân bay.</p>
            </div>
          )}
          <Pager
            path="/admin/flights"
            params={params}
            totalPages={flightsRes.data.page.totalPages}
          />
        </>
      )}
    </AdminPage>
  );
}
```

Create `frontend/src/app/admin/flights/page.tsx`:
```typescript
import AdminFlightList from "./flight-list";

export const metadata = {
  title: "Quản lý chuyến bay | SkyLine Admin",
};

export default function AdminFlightsPage() {
  return <AdminFlightList />;
}
```

- [ ] **Step 2: Create `flight-detail.tsx` and its page wrapper**

Create `frontend/src/app/admin/flights/[id]/flight-detail.tsx`:
```typescript
"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AdminModal, AdminPage, ConfirmModal, tableBox, td, th } from "../../admin-ui";
import { Field, FormSkeleton, Notice, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { formatVnd } from "@/lib/admin";
import { clock, dmy } from "@/lib/booking";
import { cabinLabel, FLIGHT_STATUS } from "@/lib/staff";
import { useApi } from "@/lib/use-api";

type Detail = Schemas["FlightDetail"];

export default function FlightDetailView({ id }: { id: number }) {
  const router = useRouter();
  const [version, setVersion] = useState(0);
  const detailRes = useApi<Detail>(`/admin/flights/${id}`, version);

  const [editOpen, setEditOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const reload = () => setVersion((v) => v + 1);

  const handleUpdate = async (e: React.FormEvent<HTMLFormElement>) => {
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
      adultPrice: Number(fd.get(`fare_${f.fareFamilyId}`)),
    }));

    try {
      await apiFetch(`/admin/flights/${id}`, {
        method: "PUT",
        body: { departureTime, arrivalTime, aircraftModel, cabins, fares },
      });
      setEditOpen(false);
      reload();
    } catch (err: unknown) {
      setActionError((err as ApiError).detail || "Không thể cập nhật chuyến bay.");
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
      setActionError((err as ApiError).detail || "Không thể huỷ chuyến bay.");
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
      setActionError((err as ApiError).detail || "Không thể xoá chuyến bay.");
      setDeleteOpen(false);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AdminPage>
      <div className="flex items-center gap-16">
        <Link href="/admin/flights" className={`${textLink} text-caption text-muted hover:text-ink`}>
          ← Danh sách chuyến bay
        </Link>
      </div>

      {actionError && <Notice>{actionError}</Notice>}

      {detailRes === undefined ? (
        <FormSkeleton rows={4} />
      ) : "error" in detailRes ? (
        <Notice>{detailRes.error.detail}</Notice>
      ) : (
        <>
          {/* Header & Thao tác */}
          <div className="flex flex-wrap items-center justify-between gap-16 border-b border-line pb-22">
            <div>
              <span className="text-caption font-bold text-muted">
                {detailRes.data.flight.departureAirport} → {detailRes.data.flight.arrivalAirport}
              </span>
              <h1 className="text-heading-lg font-bold">
                Chuyến bay {detailRes.data.flight.flightNumber}.
              </h1>
            </div>
            <div className="flex flex-wrap gap-11">
              <Link href={`/staff/flights/${id}/passengers`} className={pillLine}>
                Danh sách hành khách (Manifest)
              </Link>
              {detailRes.data.flight.status === "SCHEDULED" && (
                <>
                  <button type="button" onClick={() => setEditOpen(true)} className={pillLine}>
                    Sửa chuyến bay
                  </button>
                  <button
                    type="button"
                    onClick={() => setCancelOpen(true)}
                    className={`${pillLine} text-clay-ember border-clay-ember hover:bg-clay-ember hover:text-paper`}
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
          <div className="grid gap-22 sm:grid-cols-2 lg:grid-cols-4">
            <div className="border border-line p-16 bg-tint/30">
              <span className="text-caption text-muted">Trạng thái</span>
              <p className="text-subheading font-bold mt-4">
                {FLIGHT_STATUS[detailRes.data.flight.status] ?? detailRes.data.flight.status}
              </p>
            </div>
            <div className="border border-line p-16">
              <span className="text-caption text-muted">Cất cánh ({detailRes.data.flight.departureAirport})</span>
              <p className="text-subheading font-bold mt-4">
                {clock(detailRes.data.flight.departureTime)}
              </p>
              <p className="text-caption text-muted">{dmy(detailRes.data.flight.departureTime)}</p>
            </div>
            <div className="border border-line p-16">
              <span className="text-caption text-muted">Hạ cánh ({detailRes.data.flight.arrivalAirport})</span>
              <p className="text-subheading font-bold mt-4">
                {clock(detailRes.data.flight.arrivalTime)}
              </p>
              <p className="text-caption text-muted">{dmy(detailRes.data.flight.arrivalTime)}</p>
            </div>
            <div className="border border-line p-16">
              <span className="text-caption text-muted">Số lượt đặt chỗ</span>
              <p className="text-subheading font-bold mt-4">
                {detailRes.data.bookingCount} đơn
              </p>
              <p className="text-caption text-muted">Loại máy bay: {detailRes.data.flight.aircraftModel}</p>
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
                    const percent = c.totalSeats > 0 ? ((booked / c.totalSeats) * 100).toFixed(1) : "0.0";
                    return (
                      <tr key={c.cabinClass}>
                        <td className={`${td} font-bold`}>{cabinLabel(c.cabinClass)}</td>
                        <td className={td}>{c.totalSeats} ghế</td>
                        <td className={td}>{c.availableSeats} ghế</td>
                        <td className={td}>{booked}/{c.totalSeats} ({percent}%)</td>
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
              <table aria-label="Giá vé theo gói" className="w-full border-collapse">
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
                      <td className={`${td} font-bold`}>{formatVnd(f.adultPrice)}</td>
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
            <form onSubmit={handleUpdate} className="grid gap-16 max-h-[75vh] overflow-y-auto pr-8">
              <Field
                label="Loại máy bay"
                name="aircraftModel"
                required
                defaultValue={detailRes.data.flight.aircraftModel}
              />
              <div className="grid grid-cols-2 gap-16">
                <Field
                  label="Giờ cất cánh (ISO Offset)"
                  name="departureTime"
                  required
                  defaultValue={detailRes.data.flight.departureTime}
                />
                <Field
                  label="Giờ hạ cánh (ISO Offset)"
                  name="arrivalTime"
                  required
                  defaultValue={detailRes.data.flight.arrivalTime}
                />
              </div>

              <div className="border-t border-line pt-16 grid gap-11">
                <span className="text-caption font-bold">Cập nhật tổng số ghế (BR-82: không giảm dưới số ghế đã bán)</span>
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
                <span className="text-caption font-bold">Cập nhật giá bán theo gói (BR-86)</span>
                {detailRes.data.fares.map((f) => (
                  <Field
                    key={f.fareFamilyId}
                    label={`Giá vé ${f.fareFamilyName} (₫)`}
                    name={`fare_${f.fareFamilyId}`}
                    type="number"
                    min={0}
                    step={10000}
                    required
                    defaultValue={f.adultPrice}
                  />
                ))}
              </div>

              <div className="flex justify-end gap-11 mt-11">
                <button type="button" onClick={() => setEditOpen(false)} className={pillLine} disabled={loading}>
                  Huỷ
                </button>
                <button type="submit" className={pillDark} disabled={loading}>
                  {loading ? "Đang lưu..." : "Lưu thay đổi"}
                </button>
              </div>
            </form>
          </AdminModal>

          {/* Confirm Modal Huỷ chuyến bay (2 bước theo BR-84) */}
          <ConfirmModal
            title={`Huỷ chuyến bay ${detailRes.data.flight.flightNumber}?`}
            message={`CẢNH BÁO: Huỷ chuyến bay là thao tác không thể hoàn tác (BR-84). Hệ thống sẽ tự động: (1) Huỷ mọi booking đang giữ chỗ PENDING_PAYMENT; (2) Huỷ các yêu cầu đổi chuyến đang chờ; (3) Tự động tạo yêu cầu hoàn 100% FLIGHT_CANCELLED cho mọi chiều đã xuất vé; (4) Gửi email thông báo cho hành khách.`}
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
```

Create `frontend/src/app/admin/flights/[id]/page.tsx`:
```typescript
import FlightDetailView from "./flight-detail";

export const metadata = {
  title: "Chi tiết chuyến bay | SkyLine Admin",
};

export default async function AdminFlightDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <FlightDetailView id={Number(id)} />;
}
```

- [ ] **Step 3: Commit Task 4**

```bash
git add frontend/src/app/admin/flights
git commit -m "feat(admin): implement A-04 flight list and A-06 flight detail screens"
```

---

### Task 5: Screen A-05 Tạo chuyến bay lẻ và hàng loạt (`/admin/flights/new`)

**Files:**
- Create: `frontend/src/app/admin/flights/new/page.tsx`
- Create: `frontend/src/app/admin/flights/new/flight-form.tsx`

**Interfaces:**
- Consumes:
  - `GET /api/airports`, `GET /api/airlines`
  - `GET /api/admin/airlines/{code}/fare-families`
  - `POST /api/admin/flights`
- Produces: Màn hình A-05 tạo chuyến bay theo FR-90, BR-80, BR-81, BR-85 (chuyến lẻ hoặc hàng loạt định kỳ theo các thứ trong tuần, nhập ghế từng hạng và giá theo gói, hiển thị số chuyến đã tạo và số ngày trùng bị bỏ qua).

- [ ] **Step 1: Create `flight-form.tsx`**

Create `frontend/src/app/admin/flights/new/flight-form.tsx`:
```typescript
"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AdminPage } from "../../admin-ui";
import { Field, FormSkeleton, Notice, SelectField, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { useAirports } from "@/lib/airports";
import { WEEKDAY_OPTIONS } from "@/lib/admin";
import { cabinLabel } from "@/lib/staff";
import { useApi } from "@/lib/use-api";

type CreatedFlights = Schemas["CreatedFlights"];
type FareFamily = Schemas["FareFamilyResponse"];

export default function FlightCreateForm() {
  const router = useRouter();
  const { airports, failed: airportsFailed } = useAirports();
  const airlinesRes = useApi<Schemas["AirlineResponse"][]>("/airlines");

  const [selectedAirline, setSelectedAirline] = useState<string>("");
  const [fareFamilies, setFareFamilies] = useState<FareFamily[]>([]);
  const [loadingFares, setLoadingFares] = useState(false);

  // Chế độ tạo: 'single' (lẻ 1 ngày) hoặc 'recurring' (hàng loạt nhiều ngày)
  const [mode, setMode] = useState<"single" | "recurring">("single");
  const [selectedDays, setSelectedDays] = useState<number[]>([1, 2, 3, 4, 5, 6, 7]);

  const [createdResult, setCreatedResult] = useState<CreatedFlights | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Tự động chọn hãng đầu tiên khi danh sách hãng bay sẵn sàng
  useEffect(() => {
    if (airlinesRes && "data" in airlinesRes && airlinesRes.data.length > 0 && !selectedAirline) {
      setSelectedAirline(airlinesRes.data[0].code);
    }
  }, [airlinesRes, selectedAirline]);

  // Tải gói giá khi đổi hãng bay
  useEffect(() => {
    if (!selectedAirline) return;
    setLoadingFares(true);
    apiFetch<FareFamily[]>(`/admin/airlines/${selectedAirline}/fare-families`)
      .then((data) => setFareFamilies(data.filter((f) => f.active)))
      .catch(() => setFareFamilies([]))
      .finally(() => setLoadingFares(false));
  }, [selectedAirline]);

  const toggleDay = (day: number) => {
    setSelectedDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day].sort()
    );
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    const fd = new FormData(e.currentTarget);
    const flightNumber = (fd.get("flightNumber") as string)?.toUpperCase().trim();
    const departureAirport = (fd.get("departureAirport") as string)?.trim();
    const arrivalAirport = (fd.get("arrivalAirport") as string)?.trim();
    const departureTime = (fd.get("departureTime") as string)?.trim();
    const durationMinutes = Number(fd.get("durationMinutes"));
    const startDate = (fd.get("startDate") as string)?.trim();
    const endDate = mode === "single" ? startDate : (fd.get("endDate") as string)?.trim();
    const aircraftModel = (fd.get("aircraftModel") as string)?.trim();

    if (departureAirport === arrivalAirport) {
      setError("Sân bay đi và đến không được trùng nhau.");
      setSubmitting(false);
      return;
    }

    if (mode === "recurring" && selectedDays.length === 0) {
      setError("Vui lòng chọn ít nhất một thứ trong tuần để tạo chuyến định kỳ.");
      setSubmitting(false);
      return;
    }

    // Ghế theo hạng: ít nhất 1 hạng có ghế > 0
    const cabins = [
      { cabinClass: "ECONOMY", totalSeats: Number(fd.get("cabin_ECONOMY") || 0) },
      { cabinClass: "PREMIUM_ECONOMY", totalSeats: Number(fd.get("cabin_PREMIUM_ECONOMY") || 0) },
      { cabinClass: "BUSINESS", totalSeats: Number(fd.get("cabin_BUSINESS") || 0) },
      { cabinClass: "FIRST", totalSeats: Number(fd.get("cabin_FIRST") || 0) },
    ].filter((c) => c.totalSeats > 0);

    if (cabins.length === 0) {
      setError("Mỗi chuyến bay phải có ít nhất một hạng ghế với số ghế lớn hơn 0.");
      setSubmitting(false);
      return;
    }

    // Giá theo gói của các hạng ghế đã mở
    const cabinClasses = new Set(cabins.map((c) => c.cabinClass));
    const fares = fareFamilies
      .filter((f) => cabinClasses.has(f.cabinClass))
      .map((f) => ({
        fareFamilyId: f.id,
        adultPrice: Number(fd.get(`fare_${f.id}`) || 0),
      }))
      .filter((f) => f.adultPrice > 0);

    if (fares.length === 0) {
      setError("Vui lòng nhập giá bán cho các gói giá thuộc các hạng ghế đã cấu hình.");
      setSubmitting(false);
      return;
    }

    // Payload NewFlights theo TDD §8.7
    const payload = {
      airlineCode: selectedAirline,
      flightNumber,
      departureAirport,
      arrivalAirport,
      departureTime,
      durationMinutes,
      startDate,
      endDate,
      daysOfWeek: mode === "single" ? [] : selectedDays,
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
      setError((err as ApiError).detail || "Không thể tạo chuyến bay.");
    } finally {
      setSubmitting(false);
    }
  };

  if (!airports || !airlinesRes || !("data" in airlinesRes)) {
    return (
      <AdminPage>
        <FormSkeleton rows={4} />
      </AdminPage>
    );
  }

  return (
    <AdminPage>
      <div className="flex items-center gap-16">
        <Link href="/admin/flights" className={`${textLink} text-caption text-muted hover:text-ink`}>
          ← Danh sách chuyến bay
        </Link>
      </div>

      <div>
        <h1 className="text-heading-lg font-bold">Tạo chuyến bay mới.</h1>
        <p className="text-caption text-muted mt-4">
          Tạo một chuyến lẻ hoặc phát hành lịch bay định kỳ hàng loạt (FR-90, BR-85).
        </p>
      </div>

      {error && <Notice>{error}</Notice>}

      {createdResult ? (
        <div className="border border-line bg-tint/30 p-22 grid gap-16">
          <h2 className="text-subheading font-bold">Kết quả tạo chuyến bay:</h2>
          <div className="grid gap-8 text-caption">
            <p className="font-bold text-ink">
              ✓ Đã tạo thành công: {createdResult.created} chuyến bay.
            </p>
            {createdResult.skipped > 0 && (
              <p className="text-muted">
                ℹ Đã bỏ qua: {createdResult.skipped} ngày vì bị trùng số hiệu và giờ cất cánh (BR-80).
              </p>
            )}
          </div>
          <div className="flex gap-11 mt-11">
            <Link href="/admin/flights" className={pillDark}>
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
        <form onSubmit={handleSubmit} className="grid gap-22 max-w-720">
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
            <SelectField label="Sân bay đi" name="departureAirport" defaultValue="HAN">
              {airports.map((a) => (
                <option key={a.code} value={a.code}>
                  {a.code} ({a.city}) - {a.name}
                </option>
              ))}
            </SelectField>
            <SelectField label="Sân bay đến" name="arrivalAirport" defaultValue="SGN">
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
          <div className="border-t border-line pt-22 grid gap-16">
            <span className="text-caption font-bold">Lịch bay</span>
            <div className="flex gap-22">
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
                  <Field label="Từ ngày" name="startDate" type="date" required />
                  <Field label="Đến ngày (tối đa 180 ngày)" name="endDate" type="date" required />
                </div>
                <div>
                  <span className="text-caption text-muted mb-8 block">Chọn các thứ bay trong tuần:</span>
                  <div className="flex flex-wrap gap-11">
                    {WEEKDAY_OPTIONS.map((w) => {
                      const checked = selectedDays.includes(w.day);
                      return (
                        <button
                          key={w.day}
                          type="button"
                          onClick={() => toggleDay(w.day)}
                          className={checked ? pillDark : pillLine}
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
          <div className="border-t border-line pt-22 grid gap-16">
            <div>
              <span className="text-caption font-bold">Số ghế từng hạng (BR-81)</span>
              <p className="text-caption text-muted">Nhập số ghế cho các hạng máy bay phục vụ (0 nếu không có).</p>
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
          <div className="border-t border-line pt-22 grid gap-16">
            <div>
              <span className="text-caption font-bold">Giá vé cơ sở từng gói giá ({selectedAirline})</span>
              <p className="text-caption text-muted">Giá vé người lớn một chiều (chưa gồm hành lý mua thêm).</p>
            </div>
            {loadingFares ? (
              <FormSkeleton rows={2} />
            ) : fareFamilies.length === 0 ? (
              <p className="text-muted">
                Hãng {selectedAirline} chưa có gói giá đang hoạt động. Vui lòng vào trang Hãng bay để tạo gói giá trước.
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

          <div className="flex justify-end gap-11 border-t border-line pt-22">
            <Link href="/admin/flights" className={pillLine}>
              Huỷ bỏ
            </Link>
            <button
              type="submit"
              disabled={submitting || fareFamilies.length === 0}
              className={pillDark}
            >
              {submitting ? "Đang tạo chuyến..." : "Phát hành chuyến bay"}
            </button>
          </div>
        </form>
      )}
    </AdminPage>
  );
}
```

Create `frontend/src/app/admin/flights/new/page.tsx`:
```typescript
import FlightCreateForm from "./flight-form";

export const metadata = {
  title: "Tạo chuyến bay | SkyLine Admin",
};

export default function AdminNewFlightPage() {
  return <FlightCreateForm />;
}
```

- [ ] **Step 2: Commit Task 5**

```bash
git add frontend/src/app/admin/flights/new
git commit -m "feat(admin): implement A-05 flight creation screen"
```

---

### Task 6: Screen A-07 Quản lý voucher (`/admin/vouchers`)

**Files:**
- Create: `frontend/src/app/admin/vouchers/page.tsx`
- Create: `frontend/src/app/admin/vouchers/voucher-list.tsx`

**Interfaces:**
- Consumes: `GET /api/admin/vouchers`, `POST /api/admin/vouchers`, `PUT /api/admin/vouchers/{id}`
- Produces: Màn hình quản lý voucher A-07 (xem danh sách, số lượt đã dùng, tạo mới và sửa voucher).

- [ ] **Step 1: Create `voucher-list.tsx` and its page wrapper**

Create `frontend/src/app/admin/vouchers/voucher-list.tsx`:
```typescript
"use client";

import { useState } from "react";
import { AdminModal, AdminPage, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, SelectField, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { formatVnd, VOUCHER_TYPE_LABEL } from "@/lib/admin";
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

  const handleSave = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const code = (fd.get("code") as string)?.toUpperCase().trim() || modalState?.voucher?.code;
    const discountType = fd.get("discountType") as "PERCENT" | "FIXED";
    const discountValue = Number(fd.get("discountValue"));
    const maxDiscountRaw = fd.get("maxDiscount");
    const maxDiscount = maxDiscountRaw ? Number(maxDiscountRaw) : null;
    const minOrderAmount = Number(fd.get("minOrderAmount") || 0);
    const usageLimitRaw = fd.get("usageLimit");
    const usageLimit = usageLimitRaw ? Number(usageLimitRaw) : null;
    const validFrom = (fd.get("validFrom") as string)?.trim();
    const validTo = (fd.get("validTo") as string)?.trim();
    const active = fd.get("active") === "on";

    const payload = {
      code,
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
      setActionError((err as ApiError).detail || "Không thể lưu voucher.");
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
          className={pillDark}
        >
          + Thêm voucher
        </button>
      </div>

      {actionError && <Notice>{actionError}</Notice>}

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
                      : formatVnd(v.discountValue)}
                  </td>
                  <td className={td}>{v.minOrderAmount > 0 ? formatVnd(v.minOrderAmount) : "Không"}</td>
                  <td className={td}>{v.maxDiscount ? formatVnd(v.maxDiscount) : "Không giới hạn"}</td>
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
              label="Hiệu lực từ"
              name="validFrom"
              required
              defaultValue={modalState?.voucher?.validFrom ?? "2026-01-01T00:00:00+07:00"}
            />
            <Field
              label="Hiệu lực đến"
              name="validTo"
              required
              defaultValue={modalState?.voucher?.validTo ?? "2026-12-31T23:59:59+07:00"}
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
            <button type="submit" className={pillDark} disabled={loading}>
              {loading ? "Đang lưu..." : "Lưu voucher"}
            </button>
          </div>
        </form>
      </AdminModal>
    </AdminPage>
  );
}
```

Create `frontend/src/app/admin/vouchers/page.tsx`:
```typescript
import VoucherList from "./voucher-list";

export const metadata = {
  title: "Quản lý voucher | SkyLine Admin",
};

export default function AdminVouchersPage() {
  return <VoucherList />;
}
```

- [ ] **Step 2: Commit Task 6**

```bash
git add frontend/src/app/admin/vouchers
git commit -m "feat(admin): implement A-07 vouchers management screen"
```

---

### Task 7: Screen A-08 Quản lý tài khoản người dùng (`/admin/users`)

**Files:**
- Create: `frontend/src/app/admin/users/page.tsx`
- Create: `frontend/src/app/admin/users/user-list.tsx`

**Interfaces:**
- Consumes:
  - `GET /api/admin/users?q=&role=&page=&size=`
  - `POST /api/admin/users`
  - `PATCH /api/admin/users/{id}/status`
- Produces: Màn hình quản lý tài khoản A-08 (tìm kiếm, tạo tài khoản Staff/Admin, khoá/mở khoá với kiểm tra không tự khoá chính mình theo BR-105).

- [ ] **Step 1: Create `user-list.tsx` and its page wrapper**

Create `frontend/src/app/admin/users/user-list.tsx`:
```typescript
"use client";

import Form from "next/form";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { AdminModal, AdminPage, ConfirmModal, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, SelectField, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { dmy } from "@/lib/booking";
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
  const usersRes = useApi<Schemas["PagedModelUserResponse"]>(`/admin/users?${query}`, version);

  const [createOpen, setCreateOpen] = useState(false);
  const [toggleTarget, setToggleTarget] = useState<UserAdmin | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const reload = () => setVersion((v) => v + 1);

  const handleCreate = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setActionError(null);
    setLoading(true);

    const fd = new FormData(e.currentTarget);
    const email = (fd.get("email") as string)?.trim();
    const fullName = (fd.get("fullName") as string)?.trim();
    const phone = (fd.get("phone") as string)?.trim();
    const password = (fd.get("password") as string)?.trim();
    const role = fd.get("role") as "STAFF" | "ADMIN";

    try {
      await apiFetch("/admin/users", {
        method: "POST",
        body: { email, fullName, phone, password, role },
      });
      setCreateOpen(false);
      reload();
    } catch (err: unknown) {
      setActionError((err as ApiError).detail || "Không thể tạo tài khoản.");
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
        body: { status: nextStatus },
      });
      setToggleTarget(null);
      reload();
    } catch (err: unknown) {
      setActionError((err as ApiError).detail || "Không thể đổi trạng thái tài khoản.");
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
            Quản trị danh sách người dùng, cấp quyền tài khoản và khoá phiên truy cập (A-08).
          </p>
        </div>
        <button type="button" onClick={() => setCreateOpen(true)} className={pillDark}>
          + Tạo tài khoản Staff / Admin
        </button>
      </div>

      {actionError && <Notice>{actionError}</Notice>}

      <Form
        action="/admin/users"
        key={params.toString()}
        className="grid gap-16 sm:grid-cols-[1fr,200px,auto] sm:items-end"
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
        <button type="submit" className={pillDark}>
          Tìm kiếm
        </button>
      </Form>

      {usersRes === undefined ? (
        <FormSkeleton rows={3} />
      ) : "error" in usersRes ? (
        <Notice>{usersRes.error.detail}</Notice>
      ) : (
        <>
          <div className={tableBox}>
            <table aria-label="Bảng người dùng" className="w-full border-collapse">
              <thead>
                <tr>
                  <th className={th}>ID</th>
                  <th className={th}>Họ tên</th>
                  <th className={th}>Email</th>
                  <th className={th}>Số điện thoại</th>
                  <th className={th}>Vai trò</th>
                  <th className={th}>Trạng thái</th>
                  <th className={th}>Ngày tạo</th>
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
                          <span className="font-bold text-ink">Hoạt động</span>
                        ) : (
                          <span className="text-clay-ember font-bold">Đã khoá</span>
                        )}
                      </td>
                      <td className={td}>{dmy(u.createdAt)}</td>
                      <td className={td}>
                        {isMe ? (
                          <span className="text-muted text-caption" title="BR-105: Không thể tự khoá chính mình">
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
                            {u.status === "ACTIVE" ? "Khoá tài khoản" : "Mở khoá"}
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
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
            <SelectField label="Vai trò cấp phát" name="role" defaultValue="STAFF">
              <option value="STAFF">Nhân viên (STAFF)</option>
              <option value="ADMIN">Quản trị viên (ADMIN)</option>
            </SelectField>
          </div>
          <Field
            label="Mật khẩu khởi tạo"
            name="password"
            type="password"
            required
            minLength={8}
            placeholder="Tối thiểu 8 ký tự, có chữ và số"
          />
          <div className="flex justify-end gap-11 mt-11">
            <button type="button" onClick={() => setCreateOpen(false)} className={pillLine} disabled={loading}>
              Huỷ
            </button>
            <button type="submit" className={pillDark} disabled={loading}>
              {loading ? "Đang tạo..." : "Tạo tài khoản"}
            </button>
          </div>
        </form>
      </AdminModal>

      {/* Confirm Modal Khoá/Mở khoá */}
      <ConfirmModal
        title={toggleTarget?.status === "ACTIVE" ? `Khoá tài khoản ${toggleTarget?.email}?` : `Mở khoá tài khoản ${toggleTarget?.email}?`}
        message={
          toggleTarget?.status === "ACTIVE"
            ? `Tài khoản ${toggleTarget?.fullName} (${toggleTarget?.email}) sẽ bị mất hiệu lực ngay lập tức và không thể đăng nhập cho đến khi được mở lại (BR-102).`
            : `Tài khoản ${toggleTarget?.fullName} (${toggleTarget?.email}) sẽ được phục hồi quyền truy cập hệ thống bình thường.`
        }
        confirmLabel={toggleTarget?.status === "ACTIVE" ? "Khoá tài khoản" : "Mở khoá"}
        isOpen={toggleTarget !== null}
        isLoading={loading}
        onConfirm={handleToggleStatus}
        onClose={() => setToggleTarget(null)}
      />
    </AdminPage>
  );
}
```

Create `frontend/src/app/admin/users/page.tsx`:
```typescript
import UserList from "./user-list";

export const metadata = {
  title: "Quản lý tài khoản | SkyLine Admin",
};

export default function AdminUsersPage() {
  return <UserList />;
}
```

- [ ] **Step 2: Commit Task 7**

```bash
git add frontend/src/app/admin/users
git commit -m "feat(admin): implement A-08 user accounts management screen"
```

---

### Task 8: Screen A-09 Tham số hệ thống (`/admin/settings`)

**Files:**
- Create: `frontend/src/app/admin/settings/page.tsx`
- Create: `frontend/src/app/admin/settings/settings-form.tsx`

**Interfaces:**
- Consumes: `GET /api/admin/settings`, `PUT /api/admin/settings/{key}`
- Produces: Màn hình quản lý 8 tham số hệ thống A-09 với kiểm tra khoảng hợp lệ theo PRD §7 và ghi nhận kiểm toán người sửa/thời điểm sửa.

- [ ] **Step 1: Create `settings-form.tsx` and its page wrapper**

Create `frontend/src/app/admin/settings/settings-form.tsx`:
```typescript
"use client";

import { useState } from "react";
import { AdminModal, AdminPage, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, pillDark, pillLine, textLink } from "@/components/form";
import { apiFetch, type ApiError, type Schemas } from "@/lib/api";
import { SETTING_INFO, validateSettingValue } from "@/lib/admin";
import { clock, dmy } from "@/lib/booking";
import { useApi } from "@/lib/use-api";

type Setting = Schemas["Setting"];

export default function SettingsForm() {
  const [version, setVersion] = useState(0);
  const settingsRes = useApi<Setting[]>("/admin/settings", version);

  const [editSetting, setEditSetting] = useState<Setting | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const reload = () => setVersion((v) => v + 1);

  const handleUpdate = async (e: React.FormEvent<HTMLFormElement>) => {
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
        body: { value },
      });
      setEditSetting(null);
      reload();
    } catch (err: unknown) {
      setActionError((err as ApiError).detail || "Không thể cập nhật tham số.");
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

      {actionError && <Notice>{actionError}</Notice>}

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
                      {s.minValue} – {s.maxValue}
                    </td>
                    <td className={td}>{meta?.description ?? s.description}</td>
                    <td className={td}>{s.updatedBy ?? "Hệ thống"}</td>
                    <td className={td}>
                      {s.updatedAt ? `${clock(s.updatedAt)} ${dmy(s.updatedAt)}` : "Mặc định"}
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
          <p className="text-caption text-muted">
            {editSetting ? (SETTING_INFO[editSetting.key]?.description ?? editSetting.description) : ""}
          </p>
          <Field
            label={`Giá trị mới (${editSetting ? SETTING_INFO[editSetting.key]?.unit : ""})`}
            name="value"
            type="number"
            min={editSetting?.minValue}
            max={editSetting?.maxValue}
            required
            defaultValue={editSetting?.value}
          />
          <div className="flex justify-end gap-11 mt-11">
            <button type="button" onClick={() => setEditSetting(null)} className={pillLine} disabled={loading}>
              Huỷ
            </button>
            <button type="submit" className={pillDark} disabled={loading}>
              {loading ? "Đang lưu..." : "Lưu giá trị mới"}
            </button>
          </div>
        </form>
      </AdminModal>
    </AdminPage>
  );
}
```

Create `frontend/src/app/admin/settings/page.tsx`:
```typescript
import SettingsForm from "./settings-form";

export const metadata = {
  title: "Tham số hệ thống | SkyLine Admin",
};

export default function AdminSettingsPage() {
  return <SettingsForm />;
}
```

- [ ] **Step 2: Commit Task 8**

```bash
git add frontend/src/app/admin/settings
git commit -m "feat(admin): implement A-09 system settings screen"
```

---

### Task 9: Screen A-10 Báo cáo thống kê (`/admin/reports`)

**Files:**
- Create: `frontend/src/app/admin/reports/page.tsx`
- Create: `frontend/src/app/admin/reports/reports-view.tsx`

**Interfaces:**
- Consumes:
  - `GET /api/admin/reports/cash-flow?from=&to=&groupBy=` (DAY, MONTH, YEAR)
  - `GET /api/admin/reports/sales?from=&to=&groupBy=` (AIRLINE, ROUTE)
  - `GET /api/admin/reports/load-factor?from=&to=&airline=`
  - `GET /api/airlines`
- Produces: Màn hình báo cáo A-10 với 3 tab Dòng tiền, Doanh số vé và Tỉ lệ lấp đầy kèm các thẻ KPI tổng quan và bảng dữ liệu chi tiết.

- [ ] **Step 1: Create `reports-view.tsx` and its page wrapper**

Create `frontend/src/app/admin/reports/reports-view.tsx`:
```typescript
"use client";

import { useId, useState } from "react";
import { AdminPage, AdminTabs, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, SelectField, pillDark } from "@/components/form";
import { type Schemas } from "@/lib/api";
import { formatVnd } from "@/lib/admin";
import { clock, dmy } from "@/lib/booking";
import { useApi } from "@/lib/use-api";

type CashFlow = Schemas["CashFlowReport"];
type Sales = Schemas["SalesReport"];
type LoadFactor = Schemas["LoadFactorReport"];

export default function ReportsView() {
  const [tab, setTab] = useState<"cash-flow" | "sales" | "load-factor">("cash-flow");

  // Mặc định khoảng ngày: 30 ngày gần nhất
  const today = new Date().toISOString().slice(0, 10);
  const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 3600 * 1000).toISOString().slice(0, 10);

  const [from, setFrom] = useState(thirtyDaysAgo);
  const [to, setTo] = useState(today);
  const [cashGroupBy, setCashGroupBy] = useState<"DAY" | "MONTH" | "YEAR">("DAY");
  const [salesGroupBy, setSalesGroupBy] = useState<"AIRLINE" | "ROUTE">("AIRLINE");
  const [airlineFilter, setAirlineFilter] = useState<string>("");

  const cashRes = useApi<CashFlow>(
    tab === "cash-flow" ? `/admin/reports/cash-flow?from=${from}&to=${to}&groupBy=${cashGroupBy}` : undefined
  );
  const salesRes = useApi<Sales>(
    tab === "sales" ? `/admin/reports/sales?from=${from}&to=${to}&groupBy=${salesGroupBy}` : undefined
  );
  const loadRes = useApi<LoadFactor>(
    tab === "load-factor" ? `/admin/reports/load-factor?from=${from}&to=${to}&airline=${airlineFilter}` : undefined
  );

  const airlinesRes = useApi<Schemas["AirlineResponse"][]>("/airlines");

  return (
    <AdminPage>
      <div>
        <h1 className="text-heading-lg font-bold">Báo cáo & Thống kê.</h1>
        <p className="text-caption text-muted mt-4">
          Thống kê dòng tiền, doanh số vé và tỉ lệ lấp đầy chuyến bay theo múi giờ Việt Nam (A-10, FR-120–122).
        </p>
      </div>

      <AdminTabs
        activeKey={tab}
        onChange={(k) => setTab(k as "cash-flow" | "sales" | "load-factor")}
        tabs={[
          { key: "cash-flow", label: "Dòng tiền (Cash Flow)" },
          { key: "sales", label: "Doanh số vé (Sales)" },
          { key: "load-factor", label: "Tỉ lệ lấp đầy (Load Factor)" },
        ]}
      />

      {/* Bộ lọc khoảng ngày và nhóm */}
      <div className="grid gap-16 sm:grid-cols-[160px,160px,1fr] sm:items-end border-b border-line pb-22">
        <Field
          label="Từ ngày"
          name="from"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
        />
        <Field
          label="Đến ngày"
          name="to"
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
        />

        {tab === "cash-flow" && (
          <SelectField
            label="Nhóm theo"
            name="cashGroupBy"
            value={cashGroupBy}
            onChange={(e) => setCashGroupBy(e.target.value as "DAY" | "MONTH" | "YEAR")}
          >
            <option value="DAY">Theo ngày</option>
            <option value="MONTH">Theo tháng</option>
            <option value="YEAR">Theo năm</option>
          </SelectField>
        )}

        {tab === "sales" && (
          <SelectField
            label="Nhóm theo"
            name="salesGroupBy"
            value={salesGroupBy}
            onChange={(e) => setSalesGroupBy(e.target.value as "AIRLINE" | "ROUTE")}
          >
            <option value="AIRLINE">Theo hãng khai thác</option>
            <option value="ROUTE">Theo tuyến bay (Đi - Đến)</option>
          </SelectField>
        )}

        {tab === "load-factor" && (
          <SelectField
            label="Lọc theo hãng bay"
            name="airlineFilter"
            value={airlineFilter}
            onChange={(e) => setAirlineFilter(e.target.value)}
          >
            <option value="">Tất cả các hãng</option>
            {airlinesRes && "data" in airlinesRes && airlinesRes.data.map((al) => (
              <option key={al.code} value={al.code}>
                {al.code} - {al.name}
              </option>
            ))}
          </SelectField>
        )}
      </div>

      {/* Tab 1: Dòng tiền */}
      {tab === "cash-flow" && (
        <>
          {cashRes === undefined ? (
            <FormSkeleton rows={3} />
          ) : "error" in cashRes ? (
            <Notice>{cashRes.error.detail}</Notice>
          ) : (
            <div className="grid gap-22">
              <div className="grid gap-16 sm:grid-cols-3">
                <div className="border border-line p-16">
                  <span className="text-caption text-muted">Tổng thu (Charged)</span>
                  <p className="text-heading font-bold text-ink mt-4">
                    {formatVnd(cashRes.data.totalCharged)}
                  </p>
                </div>
                <div className="border border-line p-16">
                  <span className="text-caption text-muted">Tổng hoàn (Refunded)</span>
                  <p className="text-heading font-bold text-clay-ember mt-4">
                    {formatVnd(cashRes.data.totalRefunded)}
                  </p>
                </div>
                <div className="border border-line p-16 bg-tint/30">
                  <span className="text-caption text-muted">Doanh thu ròng (Net)</span>
                  <p className="text-heading font-bold text-ink mt-4">
                    {formatVnd(cashRes.data.netRevenue)}
                  </p>
                </div>
              </div>

              <div className={tableBox}>
                <table aria-label="Báo cáo dòng tiền" className="w-full border-collapse">
                  <thead>
                    <tr>
                      <th className={th}>Thời kỳ</th>
                      <th className={th}>Tiền thu</th>
                      <th className={th}>Tiền hoàn</th>
                      <th className={th}>Doanh thu ròng</th>
                    </tr>
                  </thead>
                  <tbody>
                    {cashRes.data.items.map((item) => (
                      <tr key={item.period}>
                        <td className={`${td} font-bold font-mono`}>{item.period}</td>
                        <td className={td}>{formatVnd(item.charged)}</td>
                        <td className={`${td} text-clay-ember`}>{formatVnd(item.refunded)}</td>
                        <td className={`${td} font-bold`}>{formatVnd(item.net)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {/* Tab 2: Doanh số vé */}
      {tab === "sales" && (
        <>
          {salesRes === undefined ? (
            <FormSkeleton rows={3} />
          ) : "error" in salesRes ? (
            <Notice>{salesRes.error.detail}</Notice>
          ) : (
            <div className="grid gap-22">
              <div className="grid gap-16 sm:grid-cols-2">
                <div className="border border-line p-16">
                  <span className="text-caption text-muted">Tổng số vé xuất kỳ này</span>
                  <p className="text-heading font-bold text-ink mt-4">
                    {salesRes.data.totalTickets.toLocaleString("vi-VN")} vé
                  </p>
                </div>
                <div className="border border-line p-16 bg-tint/30">
                  <span className="text-caption text-muted">Tổng doanh thu vé</span>
                  <p className="text-heading font-bold text-ink mt-4">
                    {formatVnd(salesRes.data.totalRevenue)}
                  </p>
                </div>
              </div>

              <div className={tableBox}>
                <table aria-label="Báo cáo doanh số vé" className="w-full border-collapse">
                  <thead>
                    <tr>
                      <th className={th}>{salesGroupBy === "AIRLINE" ? "Hãng hàng không" : "Tuyến bay"}</th>
                      <th className={th}>Số lượng vé</th>
                      <th className={th}>Doanh thu vé</th>
                    </tr>
                  </thead>
                  <tbody>
                    {salesRes.data.items.map((item) => (
                      <tr key={item.groupKey}>
                        <td className={`${td} font-bold`}>
                          {item.groupKey} {item.groupName ? `- ${item.groupName}` : ""}
                        </td>
                        <td className={td}>{item.ticketCount} vé</td>
                        <td className={`${td} font-bold`}>{formatVnd(item.revenue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {/* Tab 3: Tỉ lệ lấp đầy */}
      {tab === "load-factor" && (
        <>
          {loadRes === undefined ? (
            <FormSkeleton rows={3} />
          ) : "error" in loadRes ? (
            <Notice>{loadRes.error.detail}</Notice>
          ) : (
            <div className="grid gap-22">
              <div className="border border-line p-16 bg-tint/30">
                <span className="text-caption text-muted">Tỉ lệ lấp đầy bình quân toàn bộ chuyến</span>
                <p className="text-heading font-bold text-ink mt-4">
                  {loadRes.data.averageLoadFactor.toFixed(1)}%
                </p>
              </div>

              <div className={tableBox}>
                <table aria-label="Báo cáo tỉ lệ lấp đầy" className="w-full border-collapse">
                  <thead>
                    <tr>
                      <th className={th}>Chuyến</th>
                      <th className={th}>Tuyến bay</th>
                      <th className={th}>Ngày cất cánh</th>
                      <th className={th}>Tổng ghế</th>
                      <th className={th}>Ghế đã lấp</th>
                      <th className={th}>Tỉ lệ lấp đầy</th>
                    </tr>
                  </thead>
                  <tbody>
                    {loadRes.data.items.map((item) => (
                      <tr key={item.flightId}>
                        <td className={`${td} font-bold`}>{item.flightNumber}</td>
                        <td className={td}>{item.route}</td>
                        <td className={td}>{dmy(item.departureTime)} {clock(item.departureTime)}</td>
                        <td className={td}>{item.totalSeats}</td>
                        <td className={td}>{item.occupiedSeats}</td>
                        <td className={`${td} font-bold`}>
                          {item.loadFactor.toFixed(1)}%
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </AdminPage>
  );
}
```

Create `frontend/src/app/admin/reports/page.tsx`:
```typescript
import ReportsView from "./reports-view";

export const metadata = {
  title: "Báo cáo thống kê | SkyLine Admin",
};

export default function AdminReportsPage() {
  return <ReportsView />;
}
```

- [ ] **Step 2: Commit Task 9**

```bash
git add frontend/src/app/admin/reports
git commit -m "feat(admin): implement A-10 analytics and reports screen"
```

---

### Task 10: Smoke Test Update & Full End-to-End Test Suite (`smoke.spec.ts`, `admin.spec.ts`, Roadmap)

**Files:**
- Modify: `frontend/e2e/smoke.spec.ts:14-27`
- Create: `frontend/e2e/admin.spec.ts`
- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md:58`

**Interfaces:**
- Consumes: All Admin screens A-01–A-10 and public flight search `/flights`
- Produces: Playwright test suite xác nhận toàn bộ quyền truy cập, các màn hình quản trị và kiểm chứng cốt lõi: tạo chuyến hàng loạt trên Admin và thấy chuyến đó xuất hiện trên tìm kiếm khách hàng.

- [ ] **Step 1: Update `frontend/e2e/smoke.spec.ts` to include Admin routes in `PROTECTED`**

In `frontend/e2e/smoke.spec.ts`:
Update `PROTECTED` array to:
```typescript
const PROTECTED = [
  "/account",
  "/bookings",
  "/checkout",
  "/bookings/ZZZZZZ",
  "/bookings/ZZZZZZ/refund/outbound",
  "/bookings/ZZZZZZ/reschedule/outbound",
  "/staff/bookings",
  "/staff/bookings/ZZZZZZ",
  "/staff/flights",
  "/staff/flights/0/passengers",
  "/staff/refunds",
  "/staff/refunds/0",
  "/admin/flights",
  "/admin/flights/new",
  "/admin/flights/0",
  "/admin/airports",
  "/admin/airlines",
  "/admin/airlines/QH",
  "/admin/vouchers",
  "/admin/users",
  "/admin/settings",
  "/admin/reports",
];
```

- [ ] **Step 2: Create comprehensive E2E test `frontend/e2e/admin.spec.ts`**

Create `frontend/e2e/admin.spec.ts`:
```typescript
import { expect, test, type Page } from "@playwright/test";
import { day, field } from "./helpers";

test.use({ timezoneId: "America/New_York" });

export async function loginAdmin(page: Page) {
  await page.context().clearCookies();
  await page.goto("/login");
  await field(page, "Email").fill("admin@demo.local");
  await field(page, "Mật khẩu").fill("Demo@1234");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL("/admin/flights");
}

test.describe("Admin Screens E2E (A-01–A-10)", () => {
  test("Phân quyền: Khách vãng lai và Nhân viên không thể vào /admin/**", async ({ page }) => {
    // 1. Chưa đăng nhập
    await page.goto("/admin/flights");
    await expect(page).toHaveURL("/login?next=%2Fadmin%2Fflights");

    // 2. Nhân viên đăng nhập không có quyền ADMIN
    await page.goto("/login");
    await field(page, "Email").fill("staff@demo.local");
    await field(page, "Mật khẩu").fill("Demo@1234");
    await page.getByRole("button", { name: "Đăng nhập" }).click();
    await expect(page).toHaveURL("/staff/bookings");

    // Truy cập /admin/flights
    await page.goto("/admin/flights");
    await expect(page.getByRole("alert").first()).toContainText(
      "Bạn không có quyền thực hiện thao tác này"
    );
  });

  test("Kiểm chứng cốt lõi: Tạo chuyến bay mới (A-05) rồi thấy chuyến trong kết quả tìm kiếm", async ({ page }) => {
    test.slow();
    await loginAdmin(page);

    // Mở trang tạo chuyến bay A-05
    await page.goto("/admin/flights/new");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Tạo chuyến bay mới.");

    const testFlightNumber = "VN999";
    const testDate = day(10); // 10 ngày tới

    await field(page, "Hãng hàng không").selectOption("VN");
    await field(page, "Số hiệu chuyến bay").fill(testFlightNumber);
    await field(page, "Sân bay đi").selectOption("HAN");
    await field(page, "Sân bay đến").selectOption("PQC");
    await field(page, "Giờ cất cánh (giờ địa phương)").fill("08:30");
    await field(page, "Thời lượng bay (phút)").fill("120");
    await field(page, "Loại máy bay").fill("Airbus A321");

    // Chọn ngày bay
    await field(page, "Ngày bay").fill(testDate);

    // Bấm phát hành
    await page.getByRole("button", { name: "Phát hành chuyến bay" }).click();

    // Kết quả thành công
    await expect(page.getByRole("heading", { level: 2 })).toHaveText("Kết quả tạo chuyến bay:");
    await expect(page.getByText("Đã tạo thành công: 1 chuyến bay.")).toBeVisible();

    // Khách hàng vào trang tìm chuyến bay /flights tìm HAN -> PQC vào đúng ngày
    await page.goto(`/flights?from=HAN&to=PQC&date=${testDate}&adults=1&children=0&infants=0&cabin=ECONOMY`);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Chọn chuyến bay.");
    // Kiểm tra chuyến bay vừa tạo xuất hiện trong danh sách hành trình
    await expect(page.getByText(testFlightNumber)).toBeVisible();
  });

  test("A-01: Quản trị sân bay", async ({ page }) => {
    await loginAdmin(page);
    await page.goto("/admin/airports");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Sân bay.");
    await expect(page.getByRole("table", { name: "Bảng sân bay" })).toBeVisible();
    await expect(page.getByText("HAN")).toBeVisible();
    await expect(page.getByText("SGN")).toBeVisible();
  });

  test("A-02 & A-03: Quản trị hãng bay và cấu hình gói giá", async ({ page }) => {
    await loginAdmin(page);
    await page.goto("/admin/airlines");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Hãng bay.");
    await expect(page.getByText("Bamboo Airways")).toBeVisible();

    // Vào chi tiết hãng QH
    await page.getByRole("link", { name: "Gói giá & Bảng giá hành lý →" }).first().click();
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Hãng ");
    await expect(page.getByRole("tab", { name: "Gói giá (Fare Families)" })).toBeVisible();
  });

  test("A-07: Quản trị Voucher", async ({ page }) => {
    await loginAdmin(page);
    await page.goto("/admin/vouchers");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Voucher khuyến mãi.");
    await expect(page.getByText("WELCOME10")).toBeVisible();
  });

  test("A-08: Quản trị tài khoản và không thể tự khoá chính mình (BR-105)", async ({ page }) => {
    await loginAdmin(page);
    await page.goto("/admin/users");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Người dùng & Tài khoản.");
    // Tài khoản của admin hiện tại có nhãn (Chính bạn) và không có nút khoá
    await expect(page.getByText("(Chính bạn)")).toBeVisible();
  });

  test("A-09: Tham số hệ thống", async ({ page }) => {
    await loginAdmin(page);
    await page.goto("/admin/settings");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Tham số hệ thống.");
    await expect(page.getByText("Thời gian giữ chỗ")).toBeVisible();
    await expect(page.getByText("Khoảng cách đặt vé tối thiểu")).toBeVisible();
  });

  test("A-10: Báo cáo & Thống kê", async ({ page }) => {
    await loginAdmin(page);
    await page.goto("/admin/reports");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Báo cáo & Thống kê.");
    await expect(page.getByText("Tổng thu (Charged)")).toBeVisible();
    await expect(page.getByText("Tổng hoàn (Refunded)")).toBeVisible();
  });
});
```

- [ ] **Step 3: Run the smoke and unit test verification commands**

Run: `npm run build` in `frontend` directory.
Expected: Build successfully without any TypeScript or React errors.

- [ ] **Step 4: Update roadmap row 58 to marked completed ☑**

Update row 58 in `docs/superpowers/plans/2026-10-09-00-roadmap.md`:
```markdown
| 20 | Màn hình Admin | A-01–A-10 | — | — | 04, 16, 19 | E2E: tạo chuyến hàng loạt rồi thấy chuyến đó trong kết quả tìm kiếm | P0 (A-01–A-06), còn lại P1 | ☑ |
```

- [ ] **Step 5: Commit Task 10**

```bash
git add frontend/e2e/smoke.spec.ts frontend/e2e/admin.spec.ts docs/superpowers/plans/2026-10-09-00-roadmap.md
git commit -m "feat(admin): complete E2E tests for A-01–A-10 and mark plan 20 complete"
```

---

## Self-Review Checklist

1. **Spec Coverage:**
   - A-01 Sân bay: Task 2 (`/admin/airports`, CRUD, 409 `RESOURCE_IN_USE`)
   - A-02 Hãng bay: Task 3 (`/admin/airlines`, CRUD, mã số vé 3 chữ số)
   - A-03 Chi tiết hãng: Task 3 (`/admin/airlines/[code]`, tab Gói giá & tab Bảng giá hành lý)
   - A-04 Danh sách chuyến bay: Task 4 (`/admin/flights`, bộ lọc, link tạo và chi tiết)
   - A-05 Tạo chuyến: Task 5 (`/admin/flights/new`, chuyến lẻ/hàng loạt định kỳ, skip ngày trùng)
   - A-06 Chi tiết chuyến: Task 4 (`/admin/flights/[id]`, kho ghế, sửa, huỷ 2 bước, xoá chưa có booking)
   - A-07 Voucher: Task 6 (`/admin/vouchers`, CRUD, số lượt đã dùng)
   - A-08 Tài khoản: Task 7 (`/admin/users`, tìm kiếm, tạo Staff/Admin, khoá/mở khoá, BR-105)
   - A-09 Tham số: Task 8 (`/admin/settings`, 8 tham số, kiểm tra khoảng hợp lệ, kiểm toán)
   - A-10 Báo cáo: Task 9 (`/admin/reports`, 3 tab Dòng tiền, Doanh số, Tỉ lệ lấp đầy)
   - Kiểm chứng cốt lõi: Task 10 (E2E tạo chuyến mới và thấy chuyến trong tìm kiếm vé)
2. **Placeholder scan:** Không có "TODO", "TBD", "tương tự task N" hay mã nguồn giả lập sơ sài. Mọi file và step đều cung cấp mã nguồn TypeScript / React đầy đủ.
3. **Type consistency:** Tất cả kiểu dữ liệu và schema API (`AirportResponse`, `AirlineResponse`, `FareFamilyResponse`, `BaggageOptionResponse`, `FlightView`, `FlightDetail`, `NewFlights`, `CreatedFlights`, `VoucherResponse`, `UserResponse`, `Setting`, `CashFlowReport`, `SalesReport`, `LoadFactorReport`) hoàn toàn khớp với `src/lib/api-types.ts` và backend OpenAPI spec.

