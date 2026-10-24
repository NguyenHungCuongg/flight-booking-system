import { pillPrimary } from "@/components/form";
import AfterSales from "./after-sales";
import Airlines from "./airlines";
import FinalCta from "./final-cta";
import Footer from "./footer";
import Hero from "./hero";
import Nav from "./nav";
import Routes from "./routes";
import { SearchProvider } from "./search-context";
import Story from "./story";
import Support from "./support";

/** C-01 Trang chủ / tìm chuyến (APP_FLOW §2.1). Công khai, không cần đăng nhập. */
export default function Landing() {
  return (
    <SearchProvider>
      <div id="top" className="min-h-dvh">
        <a
          href="#tim-chuyen"
          className={`sr-only focus:not-sr-only focus:fixed focus:top-14 focus:left-16 focus:z-50 ${pillPrimary}`}
        >
          Bỏ qua tới ô tìm chuyến bay
        </a>
        <Nav />
        <main>
          <Hero />
          <Routes />
          <Airlines />
          <Story />
          <AfterSales />
          <Support />
          <FinalCta />
        </main>
        <Footer />
      </div>
    </SearchProvider>
  );
}
