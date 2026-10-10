import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  cancel: "Cancel case",
  completeOnboarding: "Complete onboarding",
  cancelHint:
    "Cancel the checklist and remove its pending tasks from active queues. Its history is retained.",
  completeOnboardingHint: "Mark the checklist as complete and retain its history.",
  pendingTasks: "Resolve every task before completing onboarding.",
  reload: "Reload case",
  saving: "Saving case",
  retry: "Retry original action",
  unconfirmed: "The result is unconfirmed. Retry the original action before making another change.",
};
const id: typeof en = {
  cancel: "Batalkan proses",
  completeOnboarding: "Selesaikan onboarding",
  cancelHint:
    "Batalkan checklist dan hapus tugas tertundanya dari antrean aktif. Riwayat tetap tersimpan.",
  completeOnboardingHint: "Tandai checklist sebagai selesai dan simpan riwayatnya.",
  pendingTasks: "Selesaikan semua tugas sebelum menyelesaikan onboarding.",
  reload: "Muat ulang proses",
  saving: "Menyimpan proses",
  retry: "Ulangi tindakan awal",
  unconfirmed: "Hasil belum terkonfirmasi. Ulangi tindakan awal sebelum membuat perubahan lain.",
};
export const lifecycleCaseActionMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
