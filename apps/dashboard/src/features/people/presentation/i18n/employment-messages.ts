import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  edit: "Edit employment",
  back: "Back to employee",
  save: "Save revision",
  saved: "Employment revision saved.",
  saving: "Saving revision…",
  reload: "Reload employment",
  retry: "Retry revision",
  loadedDate: "Values loaded for",
  startHint: "The employment start date cannot be changed.",
  effectiveHint: "The new revision applies from this date. Existing revisions remain in history.",
  unconfirmed: "The revision result is not confirmed. Retry the same request to check its outcome.",
  inactive: "Inactive",
  unavailable: "Reference unavailable",
  notWorking: "Not working on this date",
};
const id: typeof en = {
  edit: "Edit employment",
  back: "Kembali ke karyawan",
  save: "Simpan revisi",
  saved: "Revisi employment tersimpan.",
  saving: "Menyimpan revisi…",
  reload: "Muat ulang employment",
  retry: "Coba revisi kembali",
  loadedDate: "Data dimuat untuk",
  startHint: "Tanggal mulai employment tidak dapat diubah.",
  effectiveHint:
    "Revisi baru berlaku sejak tanggal ini. Revisi sebelumnya tetap tersimpan dalam riwayat.",
  unconfirmed:
    "Hasil revisi belum terkonfirmasi. Ulangi request yang sama untuk memeriksa hasilnya.",
  inactive: "Nonaktif",
  unavailable: "Referensi tidak tersedia",
  notWorking: "Tidak bekerja pada tanggal ini",
};
export const employmentMessages = (locale: Locale) => (locale === "id" ? id : en);
