"use client";

import {
  useId,
  useState,
  type FormEvent,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";
import { ApiError } from "@/lib/api";
import { Icon } from "./landing/icons";

/** Nút pill theo DESIGN.md: nền #000d10 (đổi sang sáng ở chế độ tối), chữ 17px đậm, padding 15/22/16. */
export const pillDark =
  "inline-flex min-h-52 items-center justify-center gap-10 rounded-pill bg-btn px-22 pt-15 pb-16 text-caption leading-none font-bold whitespace-nowrap text-btn-fg transition-[background-color,transform] duration-200 ease-out hover:bg-btn-hover active:scale-[0.98] disabled:cursor-wait disabled:opacity-60";
export const pillLine =
  "inline-flex min-h-52 items-center justify-center gap-10 rounded-pill border border-ink px-22 pt-15 pb-16 text-caption leading-none font-bold whitespace-nowrap text-ink transition-[background-color,color,transform] duration-200 ease-out hover:bg-ink hover:text-paper active:scale-[0.98] disabled:cursor-wait disabled:opacity-60";
export const textLink =
  "font-bold underline decoration-line-strong underline-offset-4 transition-colors hover:decoration-ink";

const iconClass =
  "size-18 shrink-0 fill-none stroke-current stroke-2 [stroke-linecap:round] [stroke-linejoin:round]";

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

/** Nhãn trên ô, lỗi dưới ô (DESIGN.md: ô nhập là khối vuông viền hairline, không bo góc). */
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
    <div className="grid gap-8">
      <label htmlFor={id} className="text-caption font-bold">
        {label}
      </label>
      <div
        className={`flex border bg-paper transition-colors focus-within:outline-2 focus-within:-outline-offset-2 focus-within:outline-ink ${
          message ? "border-ink" : "border-line-strong"
        }`}
      >
        <input
          {...input}
          id={id}
          type={isPassword && visible ? "text" : type}
          aria-invalid={message ? true : undefined}
          aria-describedby={message || hint ? noteId : undefined}
          className="min-w-0 flex-1 bg-transparent px-16 py-13 text-body text-ink outline-none placeholder:text-muted read-only:text-muted"
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
            className="px-16 text-caption font-bold text-muted transition-colors hover:text-ink"
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
      {message ? (
        <p
          id={noteId}
          role="alert"
          className="flex items-start gap-8 text-caption font-bold"
        >
          <Icon name="alert" className={`${iconClass} mt-2`} />
          {message}
        </p>
      ) : (
        hint && (
          <p id={noteId} className="text-caption text-muted">
            {hint}
          </p>
        )
      )}
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
      className="flex items-start gap-11 border border-line bg-tint px-16 py-13 text-caption"
    >
      <Icon
        name={tone === "error" ? "alert" : "check"}
        className={`${iconClass} mt-2`}
      />
      <div>{children}</div>
    </div>
  );
}

/** Khung xương giữ chỗ cho form trong lúc chờ đọc tham số URL hoặc tải dữ liệu. */
export function FormSkeleton({ rows }: { rows: number }) {
  return (
    <div aria-hidden="true" className="grid gap-22 motion-safe:animate-pulse">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="grid gap-8">
          <div className="h-17 w-120 bg-tint" />
          <div className="h-55 border border-line bg-tint" />
        </div>
      ))}
      <div className="h-52 w-160 rounded-pill bg-tint" />
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
