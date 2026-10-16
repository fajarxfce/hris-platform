import type { Locale } from "../../../../core/presentation/i18n/messages";
export function accountCredentialMessages(locale: Locale) {
  return locale === "id"
    ? {
        setPassword: "Atur password",
        resetPassword: "Reset password",
        newPassword: "Password baru",
        confirmation: "Konfirmasi password",
        length: "Gunakan 12–128 karakter.",
        mismatch: "Password belum sama.",
        save: "Simpan password",
        saving: "Menyimpan password…",
        saved: "Password tersimpan. Silakan sign in.",
        unconfirmed:
          "Perubahan password belum terkonfirmasi. Coba sign in dengan password yang baru dimasukkan.",
        signIn: "Sign in",
        forgot: "Lupa password?",
        recover: "Pemulihan password",
        request: "Kirim tautan pemulihan",
        requesting: "Mengirim permintaan…",
        requested: "Jika akun mendukung pemulihan password, email akan dikirim.",
        email: "Email",
        invalidEmail: "Masukkan alamat email yang valid.",
        back: "Kembali",
        anotherLink: "Minta tautan pemulihan",
        unavailableHint: "Untuk undangan akun, minta administrator mengirim undangan baru.",
      }
    : {
        setPassword: "Set password",
        resetPassword: "Reset password",
        newPassword: "New password",
        confirmation: "Confirm password",
        length: "Use 12–128 characters.",
        mismatch: "Passwords do not match.",
        save: "Save password",
        saving: "Saving password…",
        saved: "Password saved. Sign in to continue.",
        unconfirmed:
          "The password change was not confirmed. Try signing in with the password you entered.",
        signIn: "Sign in",
        forgot: "Forgot password?",
        recover: "Password recovery",
        request: "Send recovery link",
        requesting: "Sending request…",
        requested: "If the account supports password recovery, an email will be sent.",
        email: "Email",
        invalidEmail: "Enter a valid email address.",
        back: "Back",
        anotherLink: "Request a recovery link",
        unavailableHint:
          "For an account invitation, ask your administrator to send a new invitation.",
      };
}
