import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  apply: "Apply import",
  resume: "Resume import",
  cancel: "Cancel import",
  review: "Review import",
  refresh: "Reload review",
  open: "Open import",
  back: "Back to import",
  applyNote:
    "Create employees from ready rows. Employee numbers and references are checked again during processing.",
  resumeNote:
    "Queue the remaining work under your current access. Previously applied rows are retained.",
  cancelNote:
    "Request cancellation of further work. Previously applied rows remain in the company. Check progress after the request is accepted.",
  allowPartial: "Apply ready rows and skip invalid rows",
  unconfirmed: "The outcome is not confirmed. Retry the original request to recover its receipt.",
  retry: "Retry original request",
  applySaved: "Import queued.",
  resumeSaved: "Resume queued.",
  cancelSaved: "Cancellation requested.",
};
const id: typeof en = {
  apply: "Apply import",
  resume: "Lanjutkan import",
  cancel: "Batalkan import",
  review: "Tinjau import",
  refresh: "Muat ulang tinjauan",
  open: "Buka import",
  back: "Kembali ke import",
  applyNote:
    "Buat karyawan dari baris siap. Nomor karyawan dan referensi diperiksa kembali saat pemrosesan.",
  resumeNote:
    "Antrekan pekerjaan tersisa dengan akses Anda saat ini. Baris yang sudah diterapkan tetap tersimpan.",
  cancelNote:
    "Minta pembatalan pekerjaan berikutnya. Baris yang sudah diterapkan tetap tersimpan di perusahaan. Periksa progres setelah permintaan diterima.",
  allowPartial: "Terapkan baris siap dan lewati baris tidak valid",
  unconfirmed: "Hasil belum terkonfirmasi. Ulangi permintaan awal untuk mendapatkan receipt-nya.",
  retry: "Ulangi permintaan awal",
  applySaved: "Import diantrekan.",
  resumeSaved: "Kelanjutan import diantrekan.",
  cancelSaved: "Pembatalan diminta.",
};
export const employeeImportTransitionMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
