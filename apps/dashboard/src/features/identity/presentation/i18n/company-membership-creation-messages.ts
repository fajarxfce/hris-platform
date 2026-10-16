import type { Locale } from "../../../../core/presentation/i18n/messages";

export function companyMembershipCreationMessages(locale: Locale) {
  return locale === "id"
    ? {
        title: "Tambahkan akun",
        review: "Tambah akses",
        back: "Kembali ke akun perusahaan",
        choose: "Pilih akun",
        selection: "Pilih akun aktif untuk perusahaan ini.",
        scope:
          "Akses ini berlaku untuk perusahaan yang dipilih. Profil karyawan ditautkan secara terpisah.",
        roles: "Pilih versi role yang masih aktif untuk akses baru.",
        save: "Berikan akses",
        changeAccount: "Pilih akun lain",
        existing: "Lihat akses yang sudah ada",
        current: "Akun Anda",
        name: "Nama",
        email: "Email",
        status: "Status",
      }
    : {
        title: "Add account",
        review: "Add access",
        back: "Back to company accounts",
        choose: "Choose account",
        selection: "Select an active account for this company.",
        scope:
          "This access applies to the selected company. Employee profiles are linked separately.",
        roles: "Select active role versions for the new access.",
        save: "Grant access",
        changeAccount: "Choose another account",
        existing: "View existing access",
        current: "Your account",
        name: "Name",
        email: "Email",
        status: "Status",
      };
}
