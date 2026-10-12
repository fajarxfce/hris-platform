import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Correction history",
  changes: "Corrections",
  overview: "Employee and work date",
  back: "Back to attendance day",
  empty: "No corrections for this work date.",
  revision: "Revision",
  recordedAt: "Recorded",
  type: "Correction type",
  absence: "Absence",
  workingTime: "Working time",
  view: "View correction",
  details: "Correction details",
  reference: "Correction ID",
  first: "First page",
  next: "Older corrections",
  pages: "Correction history pages",
};
const id: typeof en = {
  title: "Riwayat koreksi",
  changes: "Koreksi",
  overview: "Karyawan dan tanggal kerja",
  back: "Kembali ke detail presensi",
  empty: "Belum ada koreksi untuk tanggal kerja ini.",
  revision: "Revisi",
  recordedAt: "Dicatat",
  type: "Jenis koreksi",
  absence: "Tidak hadir",
  workingTime: "Waktu kerja",
  view: "Lihat koreksi",
  details: "Detail koreksi",
  reference: "ID koreksi",
  first: "Halaman pertama",
  next: "Koreksi sebelumnya",
  pages: "Halaman riwayat koreksi",
};
export const attendanceHistoryMessages = (locale: Locale): typeof en => (locale === "id" ? id : en);
