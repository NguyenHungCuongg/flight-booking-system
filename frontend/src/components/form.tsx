"use client";

import {
  useId,
  useState,
  type FormEvent,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from "react";
import { ApiError } from "@/lib/api";
import { Icon } from "./landing/icons";

/** Nút chính theo NEW_DESIGN.md (Wise style): pill nền Lime Voltage (#9fe870), chữ Forest Ink (#163300) đậm. */
export const pillPrimary =
  "inline-flex min-h-40 items-center justify-center gap-8 rounded-pill bg-cta px-18 py-10 text-caption leading-none font-semibold whitespace-nowrap text-cta-fg shadow-accent transition-[background-color,transform,box-shadow] duration-200 ease-out hover:-translate-y-1 hover:bg-cta-hover active:translate-y-0 active:scale-[0.98] disabled:cursor-wait disabled:opacity-60 disabled:hover:translate-y-0";
/** Nút phụ: pill nền thẻ, viền Pebble đậm lên thành Forest Ink khi rê chuột. */
export const pillLine =
  "inline-flex min-h-40 items-center justify-center gap-8 rounded-pill border border-line-strong bg-surface px-18 py-10 text-caption leading-none font-semibold whitespace-nowrap text-ink transition-[background-color,border-color,transform] duration-200 ease-out hover:border-ink hover:bg-accent-soft active:scale-[0.98] disabled:cursor-wait disabled:opacity-60";
/** Nút cho thao tác không hoàn tác được (huỷ chuyến bay): viền và chữ Alarm Red (#cb272f), nền đỏ khi rê chuột. */
export const pillDanger =
  "inline-flex min-h-40 items-center justify-center gap-8 rounded-pill border border-danger bg-surface px-18 py-10 text-caption leading-none font-semibold whitespace-nowrap text-danger transition-[background-color,color,transform] duration-200 ease-out hover:bg-danger hover:text-surface active:scale-[0.98] disabled:cursor-wait disabled:opacity-60";
export const textLink =
  "font-semibold underline decoration-accent/40 decoration-2 underline-offset-4 transition-colors hover:text-accent hover:decoration-accent";

/** Ô nhập và ô chọn theo NEW_DESIGN.md: bo 10px, viền Pebble 1px; focus viền Forest Ink kèm quầng Linen Mist. */
const inputBox = (invalid: boolean) =>
  `flex min-w-0 rounded-inner border bg-surface transition-[border-color,box-shadow] duration-200 hover:border-ink-2/70 focus-within:border-accent focus-within:shadow-[0_0_0_3px_var(--color-accent-soft)] ${
    invalid ? "border-danger" : "border-line-strong"
  }`;

const iconClass =
  "size-16 shrink-0 fill-none stroke-current stroke-2 [stroke-linecap:round] [stroke-linejoin:round]";

type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  name: string;
  /** Lỗi từ backend cho trường này. */
  error?: string;
  hint?: string;
  /** Câu báo khi giá trị sai pattern hoặc minLength. */
  invalidMessage?: string;
};

/** Câu báo tiếng Việt cho ràng buộc HTML (required, type=email, pattern) thay cho bong bóng mặc định của trình duyệt. */
function describe(
  input: HTMLInputElement,
  label: string,
  invalidMessage?: string,
) {
  const v = input.validity;
  if (v.valueMissing) return `Hãy nhập ${label.toLowerCase()}.`;
  if (v.typeMismatch) return "Email chưa đúng định dạng.";
  return invalidMessage ?? input.validationMessage;
}

/** Dưới ô nhập: lỗi (ưu tiên) hoặc gợi ý. */
function Note({
  id,
  message,
  hint,
}: {
  id: string;
  message?: string;
  hint?: string;
}) {
  if (message) {
    return (
      <p
        id={id}
        role="alert"
        className="flex items-start gap-8 text-caption font-semibold text-danger"
      >
        <Icon name="alert" className={`${iconClass} mt-2`} />
        {message}
      </p>
    );
  }
  return hint ? (
    <p id={id} className="text-caption text-muted">
      {hint}
    </p>
  ) : null;
}

/** Nhãn trên ô, lỗi dưới ô (NEW_DESIGN.md: ô nhập bo 10px, viền 1px Pebble). */
export function Field({
  label,
  error,
  hint,
  invalidMessage,
  type = "text",
  onChange,
  ...input
}: FieldProps) {
  const id = useId();
  const [own, setOwn] = useState<string>();
  const [visible, setVisible] = useState(false);
  const message = own ?? error;
  const isPassword = type === "password";
  const noteId = `${id}-note`;

  return (
    <div className="grid gap-6">
      <label htmlFor={id} className="text-caption font-semibold text-ink-2">
        {label}
      </label>
      <div className={inputBox(!!message)}>
        <input
          {...input}
          id={id}
          type={isPassword && visible ? "text" : type}
          aria-invalid={message ? true : undefined}
          aria-describedby={message || hint ? noteId : undefined}
          className="min-w-0 flex-1 rounded-inner bg-transparent px-13 py-9 text-body text-ink outline-none placeholder:text-muted read-only:text-muted"
          onInvalid={(e) => {
            e.preventDefault();
            setOwn(describe(e.currentTarget, label, invalidMessage));
          }}
          onChange={(e) => {
            setOwn(undefined);
            onChange?.(e);
          }}
        />
        {isPassword && (
          <button
            type="button"
            className="px-13 text-caption font-semibold text-muted transition-colors hover:text-accent"
            aria-label={
              visible
                ? `Ẩn ${label.toLowerCase()}`
                : `Hiện ${label.toLowerCase()}`
            }
            onClick={() => setVisible((v) => !v)}
          >
            {visible ? "Ẩn" : "Hiện"}
          </button>
        )}
      </div>
      <Note id={noteId} message={message} hint={hint} />
    </div>
  );
}

type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  name: string;
  /** Lỗi từ backend cho trường này. */
  error?: string;
  hint?: string;
};

/** Như Field nhưng là ô chọn (giới tính, hành lý mua thêm). */
export function SelectField({
  label,
  error,
  hint,
  onChange,
  children,
  ...select
}: SelectFieldProps) {
  const id = useId();
  const [own, setOwn] = useState<string>();
  const message = own ?? error;
  const noteId = `${id}-note`;

  return (
    <div className="grid gap-6">
      <label htmlFor={id} className="text-caption font-semibold text-ink-2">
        {label}
      </label>
      <div className={inputBox(!!message)}>
        <select
          {...select}
          id={id}
          aria-invalid={message ? true : undefined}
          aria-describedby={message || hint ? noteId : undefined}
          className="h-42 min-w-0 flex-1 cursor-pointer rounded-inner bg-transparent px-13 text-body text-ink outline-none"
          onInvalid={(e) => {
            e.preventDefault();
            setOwn(`Hãy chọn ${label.toLowerCase()}.`);
          }}
          onChange={(e) => {
            setOwn(undefined);
            onChange?.(e);
          }}
        >
          {children}
        </select>
      </div>
      <Note id={noteId} message={message} hint={hint} />
    </div>
  );
}

/** Thông báo cho cả form: lỗi (tone="error") hoặc xác nhận đã xong. */
export function Notice({
  tone = "error",
  children,
}: {
  tone?: "error" | "ok";
  children: ReactNode;
}) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={`flex items-start gap-10 rounded-inner px-14 py-10 text-caption text-ink ${
        tone === "error" ? "bg-danger-soft" : "bg-ok-soft"
      }`}
    >
      <Icon
        name={tone === "error" ? "alert" : "check"}
        className={`${iconClass} mt-2 ${tone === "error" ? "text-danger" : "text-ok"}`}
      />
      <div>{children}</div>
    </div>
  );
}

/** Khung xương giữ chỗ cho form trong lúc chờ đọc tham số URL hoặc tải dữ liệu. */
export function FormSkeleton({ rows }: { rows: number }) {
  return (
    <div aria-hidden="true" className="grid gap-18 motion-safe:animate-pulse">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="grid gap-8">
          <div className="h-14 w-110 rounded-badge bg-tint" />
          <div className="h-42 rounded-inner bg-tint" />
        </div>
      ))}
      <div className="h-40 w-140 rounded-pill bg-tint" />
    </div>
  );
}

/**
 * Trạng thái gửi form: đang gửi, lỗi theo trường (errors của ProblemDetail) và lỗi chung.
 * `submit(fn, onError)` trả handler cho onSubmit; onError trả true nghĩa là đã tự xử lý lỗi đó.
 */
export function useSubmit() {
  const [pending, setPending] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();

  const submit =
    (
      fn: (data: FormData, form: HTMLFormElement) => Promise<unknown>,
      onError?: (e: ApiError) => boolean,
    ) =>
    async (e: FormEvent<HTMLFormElement>) => {
      e.preventDefault();
      setPending(true);
      setErrors({});
      setFormError(undefined);
      try {
        const form = e.currentTarget;
        await fn(new FormData(form), form);
      } catch (err) {
        if (!(err instanceof ApiError)) throw err;
        if (onError?.(err)) return;
        if (err.errors.length > 0) {
          setErrors(
            Object.fromEntries(err.errors.map((f) => [f.field, f.message])),
          );
        } else {
          setFormError(err.detail);
        }
      } finally {
        setPending(false);
      }
    };

  return { pending, errors, formError, setErrors, setFormError, submit };
}

/** Đọc một trường text của form. */
export function text(data: FormData, name: string) {
  return String(data.get(name) ?? "").trim();
}

/** BR-100, cùng luật với @Password ở backend: ít nhất 8 ký tự, có chữ và số. */
export const PASSWORD_PATTERN = "(?=.*\\p{L})(?=.*\\d).{8,}";
export const PASSWORD_RULE = "Ít nhất 8 ký tự, gồm cả chữ và số.";
