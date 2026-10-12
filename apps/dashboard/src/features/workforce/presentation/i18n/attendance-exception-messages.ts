import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Attendance exceptions",
  directory: "Employee days",
  filters: "Attendance exception filters",
  kind: "Review status",
  all: "All exceptions",
  pending: "Pending review",
  incomplete: "Incomplete clock-in/out",
  pair: "Clock-in/out",
  received: "Last received",
  empty: "No attendance exceptions match these filters.",
  pages: "Attendance exception pages",
};
const id: typeof en = {
  title: "Pemeriksaan presensi",
  directory: "Presensi per hari",
  filters: "Filter pemeriksaan presensi",
  kind: "Status pemeriksaan",
  all: "Semua",
  pending: "Menunggu verifikasi",
  incomplete: "Jam masuk/pulang belum lengkap",
  pair: "Jam masuk/pulang",
  received: "Terakhir diterima",
  empty: "Tidak ada presensi yang perlu diperiksa sesuai filter ini.",
  pages: "Halaman pemeriksaan presensi",
};
export const attendanceExceptionMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
