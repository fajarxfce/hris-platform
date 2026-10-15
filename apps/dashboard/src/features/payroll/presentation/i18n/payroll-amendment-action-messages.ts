import type { Locale } from "../../../../core/presentation/i18n/messages";
export const payrollAmendmentActionMessages = (locale: Locale) =>
  locale === "id"
    ? {
        CALCULATE: "Hitung proposal",
        CANCEL: "Batalkan proposal",
        CALCULATE_NOTE:
          "Hitung revisi proposal ini dan simpan hasilnya dalam riwayat. Hasil pemeriksaan yang gagal tetap dapat ditinjau.",
        CANCEL_NOTE:
          "Batalkan proposal yang belum dipublikasikan ini. Revisi dan riwayat perhitungannya tetap tersedia.",
        CALCULATE_SAVED: "Percobaan perhitungan tersimpan.",
        CANCEL_SAVED: "Proposal dibatalkan.",
        open: "Lihat proposal",
        latestAttempt: "Percobaan perhitungan terakhir",
        corrections: "Bulan yang dikoreksi",
      }
    : {
        CALCULATE: "Calculate proposal",
        CANCEL: "Cancel proposal",
        CALCULATE_NOTE:
          "Calculate this proposal revision and record its result in history. Failed rule checks remain available for review.",
        CANCEL_NOTE:
          "Cancel this unpublished proposal. Its revisions and calculation history will remain available.",
        CALCULATE_SAVED: "Calculation attempt recorded.",
        CANCEL_SAVED: "Proposal cancelled.",
        open: "View proposal",
        latestAttempt: "Latest calculation attempt",
        corrections: "Corrected months",
      };
