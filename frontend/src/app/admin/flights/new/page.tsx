import FlightCreateForm from "./flight-form";

export const metadata = {
  title: "Tạo chuyến bay | SkyLine Admin",
};

/** A-05 (APP_FLOW §2.3): tạo một chuyến lẻ hoặc lịch bay định kỳ hàng loạt. */
export default function AdminNewFlightPage() {
  return <FlightCreateForm />;
}
