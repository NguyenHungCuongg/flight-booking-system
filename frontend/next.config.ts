import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  // Trình duyệt chỉ gọi /api trên origin của frontend, nên cookie phiên và XSRF-TOKEN không cần CORS.
  // Giá trị đọc lúc build (rewrites nằm trong routes-manifest).
  async rewrites() {
    const backend = process.env.BACKEND_URL ?? "http://localhost:8080";
    const agent = process.env.AGENT_URL ?? "http://localhost:8000";
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/agent/:path*", destination: `${agent}/agent/:path*` },
    ];
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
