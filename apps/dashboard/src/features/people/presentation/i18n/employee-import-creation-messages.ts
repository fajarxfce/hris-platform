import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Import employees",
  create: "Import CSV",
  choose: "Choose CSV",
  clear: "Remove file",
  template: "Download template",
  templateRequested: "Template download requested.",
  selected: "Selected file",
  size: "Size",
  bytes: "bytes",
  empty: "No file selected.",
  requirements: "CSV · UTF-8 · Up to 512 KiB, 5,000 rows, and 1,024 characters per cell.",
  notice:
    "Preparing a preview does not create employees. Review the results before applying the import.",
  prepare: "Prepare preview",
  preparing: "Preparing preview",
  selecting: "Reading file",
  downloading: "Preparing template",
  saved: "Preview queued.",
  open: "Open import",
  unconfirmed:
    "The outcome is not confirmed. Keep this page open and retry the original request to recover its receipt.",
  retry: "Retry original request",
};
const id: typeof en = {
  title: "Import karyawan",
  create: "Import CSV",
  choose: "Pilih CSV",
  clear: "Hapus file",
  template: "Download template",
  templateRequested: "Download template diminta.",
  selected: "File terpilih",
  size: "Ukuran",
  bytes: "byte",
  empty: "Belum ada file terpilih.",
  requirements: "CSV · UTF-8 · Maksimal 512 KiB, 5.000 baris, dan 1.024 karakter per sel.",
  notice: "Menyiapkan preview belum membuat karyawan. Tinjau hasilnya sebelum menerapkan import.",
  prepare: "Siapkan preview",
  preparing: "Menyiapkan preview",
  selecting: "Membaca file",
  downloading: "Menyiapkan template",
  saved: "Preview diantrekan.",
  open: "Buka import",
  unconfirmed:
    "Hasil belum terkonfirmasi. Biarkan halaman terbuka dan ulangi permintaan awal untuk mendapatkan receipt-nya.",
  retry: "Ulangi permintaan awal",
};
export const employeeImportCreationMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
