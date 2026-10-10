import { NextResponse, type NextRequest } from "next/server";

/**
 * Route cần đăng nhập mà chưa có cookie phiên thì chuyển về /login?next=. Chỉ kiểm tra cookie có hay không:
 * phiên hết hạn nhưng cookie còn thì trang tự chuyển về đăng nhập khi API trả 401. Quyền theo vai trò
 * vẫn do backend kiểm tra (SecurityConfig).
 */
export function proxy(request: NextRequest) {
  if (request.cookies.has("JSESSIONID")) return NextResponse.next();
  const { pathname, search } = request.nextUrl;
  const login = new URL("/login", request.url);
  login.searchParams.set("next", pathname + search);
  return NextResponse.redirect(login);
}

export const config = {
  matcher: [
    "/account/:path*",
    "/bookings/:path*",
    "/staff/:path*",
    "/admin/:path*",
  ],
};
