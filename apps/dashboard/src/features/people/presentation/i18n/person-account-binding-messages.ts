import type { Locale } from "../../../../core/presentation/i18n/messages";

export function personAccountBindingMessages(locale: Locale) {
  return locale === "id"
    ? {
        title: "Hubungkan akun",
        back: "Kembali ke profil",
        notice: "Pilih akun milik karyawan. Pengaitan ini tidak dapat diganti melalui edit profil.",
        candidates: "Akun yang tersedia",
        select: "Pilih akun",
        selected: "Akun terpilih",
        name: "Nama",
        email: "Email",
        identifier: "ID akun",
        profileVersion: "Versi profil yang direview",
        first: "Halaman pertama",
        next: "Halaman berikutnya",
        pages: "Halaman akun",
        empty: "Tidak ada akun yang memenuhi syarat pada halaman ini.",
        reason: "Alasan pengaitan",
        bind: "Hubungkan akun",
        binding: "Menghubungkan akun",
        bound: "Akun terhubung.",
        reload: "Muat ulang profil",
        operation: "Operation ID",
        unconfirmed:
          "Hasil pengaitan belum dapat dipastikan. Periksa kembali dengan submission yang sama.",
        retry: "Periksa pengaitan",
        view: "Lihat profil",
      }
    : {
        title: "Link account",
        back: "Back to profile",
        notice:
          "Choose the employee's account. This binding cannot be replaced through profile editing.",
        candidates: "Available accounts",
        select: "Select account",
        selected: "Selected account",
        name: "Name",
        email: "Email",
        identifier: "Account ID",
        profileVersion: "Reviewed profile version",
        first: "First page",
        next: "Next page",
        pages: "Account pages",
        empty: "No eligible accounts on this page.",
        reason: "Reason for linking",
        bind: "Link account",
        binding: "Linking account",
        bound: "Account linked.",
        reload: "Reload profile",
        operation: "Operation ID",
        unconfirmed: "The binding outcome is not confirmed. Check it using the same submission.",
        retry: "Check binding",
        view: "View profile",
      };
}
