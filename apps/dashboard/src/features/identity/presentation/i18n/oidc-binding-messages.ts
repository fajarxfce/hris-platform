import type { Locale } from "../../../../core/presentation/i18n/messages";

export function oidcBindingMessages(locale: Locale) {
  return locale === "id"
    ? {
        title: "Binding SSO",
        directory: "Daftar binding",
        editor: "Binding SSO",
        create: "Tambah binding SSO",
        back: "Kembali ke SSO",
        issuer: "Issuer",
        subject: "Subject",
        status: "Status",
        linkedAt: "Dibuat",
        active: "Aktif",
        inactive: "Nonaktif",
        enabled: "Binding aktif",
        view: "Tinjau binding",
        empty: "Belum ada binding SSO.",
        first: "Halaman pertama",
        next: "Halaman berikutnya",
        pages: "Halaman binding SSO",
        identityHint:
          "Gunakan issuer dan subject yang telah diverifikasi oleh administrator identity provider.",
        subjectHint: "Salin nilai persis dari provider, termasuk huruf besar dan kecil.",
        immutable: "Issuer dan subject tidak dapat diubah setelah binding dibuat.",
        disabledAccount:
          "Akun nonaktif. Binding dapat dinonaktifkan; aktivasi memerlukan akun aktif.",
        sessionsEnd: "Penyimpanan mengakhiri sesi akun ini. Pengguna perlu sign in kembali.",
        ownSessionEnds:
          "Penyimpanan mengakhiri sesi Anda. Sign in kembali setelah perubahan tersimpan.",
        save: "Simpan binding",
        saving: "Menyimpan binding…",
        saved: "Binding SSO tersimpan.",
        noChanges: "Belum ada perubahan status.",
        unconfirmed:
          "Hasil penyimpanan belum terkonfirmasi. Periksa hasilnya sebelum mengirim perubahan lain.",
        retry: "Periksa hasil penyimpanan",
      }
    : {
        title: "SSO bindings",
        directory: "Binding directory",
        editor: "SSO binding",
        create: "Add SSO binding",
        back: "Back to SSO",
        issuer: "Issuer",
        subject: "Subject",
        status: "Status",
        linkedAt: "Created",
        active: "Active",
        inactive: "Inactive",
        enabled: "Binding active",
        view: "Review binding",
        empty: "No SSO bindings yet.",
        first: "First page",
        next: "Next page",
        pages: "SSO binding pages",
        identityHint:
          "Use the issuer and subject verified by your identity provider administrator.",
        subjectHint: "Copy the provider value exactly, including its case.",
        immutable: "Issuer and subject cannot change after a binding is created.",
        disabledAccount:
          "This account is inactive. Bindings can be deactivated; activation requires an active account.",
        sessionsEnd: "Saving ends this account's sessions. The user must sign in again.",
        ownSessionEnds: "Saving ends your session. Sign in again after the change is saved.",
        save: "Save binding",
        saving: "Saving binding…",
        saved: "SSO binding saved.",
        noChanges: "No status changes yet.",
        unconfirmed:
          "The save outcome is unconfirmed. Check its status before making another change.",
        retry: "Check save outcome",
      };
}
