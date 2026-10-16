import type { Locale } from "../../../../core/presentation/i18n/messages";
export function accountInvitationMessages(locale: Locale) {
  return locale === "id"
    ? {
        invite: "Undang akun",
        resend: "Undang ulang",
        name: "Nama",
        email: "Email",
        reason: "Alasan",
        hint: "Penerima menetapkan password sebelum akun diaktifkan. Akses perusahaan diberikan secara terpisah.",
        resendHint: "Undangan baru membatalkan tautan undangan sebelumnya.",
        send: "Kirim undangan",
        sending: "Menyiapkan undangan…",
        queued: "Undangan masuk antrean pengiriman.",
        invalid: "Periksa nilai ini.",
        unconfirmed:
          "Hasil undangan belum terkonfirmasi. Periksa hasilnya sebelum mengirim undangan lain.",
        retry: "Periksa hasil undangan",
        reload: "Tinjau versi terbaru",
      }
    : {
        invite: "Invite account",
        resend: "Send new invitation",
        name: "Name",
        email: "Email",
        reason: "Reason",
        hint: "The recipient sets a password before activation. Company access is granted separately.",
        resendHint: "A new invitation invalidates previous invitation links.",
        send: "Send invitation",
        sending: "Preparing invitation…",
        queued: "Invitation queued for delivery.",
        invalid: "Check this value.",
        unconfirmed:
          "The invitation outcome is unconfirmed. Check its status before sending another invitation.",
        retry: "Check invitation outcome",
        reload: "Review current version",
      };
}
