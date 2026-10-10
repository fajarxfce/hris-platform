import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Complete offboarding",
  review: "Review offboarding",
  back: "Back to case",
  companyDate: "Company date at review",
  employmentVersion: "Employment version",
  lastWorkingDate: "Last working date",
  employmentEffect:
    "Completion ends this employment from the current company date and retains the last working date and employment history.",
  accessEffect: "Company access is deactivated if no other current or planned employment remains.",
  reload: "Reload review",
  complete: "Complete offboarding",
  completing: "Completing offboarding",
  completed: "Offboarding completed.",
  open: "Open case",
  retry: "Retry original completion",
  unconfirmed:
    "The result is unconfirmed. Retry the original completion before making another change.",
};
const id: typeof en = {
  title: "Selesaikan offboarding",
  review: "Tinjau offboarding",
  back: "Kembali ke proses",
  companyDate: "Tanggal perusahaan saat ditinjau",
  employmentVersion: "Versi employment",
  lastWorkingDate: "Hari kerja terakhir",
  employmentEffect:
    "Penyelesaian mengakhiri employment mulai tanggal perusahaan saat ini, dengan hari kerja terakhir dan riwayat employment tetap tersimpan.",
  accessEffect:
    "Akses perusahaan dinonaktifkan jika tidak ada employment lain yang aktif atau terjadwal.",
  reload: "Muat ulang tinjauan",
  complete: "Selesaikan offboarding",
  completing: "Menyelesaikan offboarding",
  completed: "Offboarding selesai.",
  open: "Buka proses",
  retry: "Ulangi penyelesaian awal",
  unconfirmed:
    "Hasil belum terkonfirmasi. Ulangi penyelesaian awal sebelum membuat perubahan lain.",
};
export const offboardingMessages = (locale: Locale): typeof en => (locale === "id" ? id : en);
