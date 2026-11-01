import { useEffect, useState } from "react";
import { apiFetch, type User } from "./api";

/**
 * Người đang đăng nhập, cho header. Cookie phiên là HttpOnly nên phải hỏi GET /me: undefined khi đang tải,
 * null khi chưa đăng nhập (mọi lỗi, thường là 401).
 */
export function useCurrentUser() {
  const [user, setUser] = useState<User | null>();
  useEffect(() => {
    let live = true;
    apiFetch<User>("/me").then(
      (u) => live && setUser(u),
      () => live && setUser(null),
    );
    return () => {
      live = false;
    };
  }, []);
  return user;
}
