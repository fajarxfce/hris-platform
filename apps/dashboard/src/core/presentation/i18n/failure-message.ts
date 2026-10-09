import type { Failure } from "../../domain/result";
import type { Locale } from "./messages";

const errors: Readonly<Record<string, readonly [string, string]>> = {
  authentication_required: ["Sign in to continue.", "Masuk untuk melanjutkan."],
  invalid_credentials: ["The email or password is incorrect.", "Email atau password tidak sesuai."],
  invalid_credentials_input: [
    "Enter a valid email and password.",
    "Masukkan email dan password yang valid.",
  ],
  session_revoked: [
    "Your session has ended. Sign in again.",
    "Sesi berakhir. Silakan masuk kembali.",
  ],
  recent_authentication_required: [
    "Verify your account to continue.",
    "Verifikasi akun untuk melanjutkan.",
  ],
  mfa_required: ["Verify your account to continue.", "Verifikasi akun untuk melanjutkan."],
  invalid_mfa_code: ["Enter a valid verification code.", "Masukkan kode verifikasi yang valid."],
  mfa_code_invalid: [
    "The verification code is incorrect or has expired.",
    "Kode verifikasi tidak sesuai atau sudah kedaluwarsa.",
  ],
  mfa_enrollment_expired: [
    "The setup key has expired. Start setup again.",
    "Setup key sudah kedaluwarsa. Mulai pengaturan kembali.",
  ],
  mfa_enrollment_changed: [
    "Authenticator setup has changed. Start setup again.",
    "Pengaturan authenticator berubah. Mulai kembali.",
  ],
  access_denied: [
    "You do not have access to this resource.",
    "Anda tidak memiliki akses ke resource ini.",
  ],
  company_access_denied: [
    "Company access is unavailable. Select another company or contact an administrator.",
    "Akses perusahaan tidak tersedia. Pilih perusahaan lain atau hubungi administrator.",
  ],
  client_version_required: [
    "Reload the application to continue.",
    "Muat ulang aplikasi untuk melanjutkan.",
  ],
  client_update_required: [
    "Update the application to continue.",
    "Perbarui aplikasi untuk melanjutkan.",
  ],
  company_module_disabled: [
    "This module is disabled for the selected company.",
    "Modul ini dinonaktifkan untuk perusahaan yang dipilih.",
  ],
  company_maintenance: [
    "Company services are under maintenance. Try again later.",
    "Layanan perusahaan sedang dalam maintenance. Coba lagi nanti.",
  ],
  invalid_client_version: [
    "Reload the application or contact support.",
    "Muat ulang aplikasi atau hubungi support.",
  ],
  connection_unavailable: [
    "Unable to connect. Check your connection and retry.",
    "Tidak dapat terhubung. Periksa koneksi dan coba lagi.",
  ],
  request_timeout: [
    "The request timed out. Retry when the connection is available.",
    "Request melewati batas waktu. Coba lagi setelah koneksi tersedia.",
  ],
  invalid_response: [
    "The service returned an invalid response. Please retry.",
    "Respons layanan tidak valid. Silakan coba lagi.",
  ],
  stale_version: [
    "This record has changed. Refresh before saving again.",
    "Data sudah berubah. Muat ulang sebelum menyimpan kembali.",
  ],
  request_conflict: [
    "The action conflicts with the current record. Refresh and review it.",
    "Tindakan tidak sesuai dengan kondisi data saat ini. Muat ulang dan periksa kembali.",
  ],
  request_rate_limited: [
    "Too many requests. Wait before trying again.",
    "Terlalu banyak request. Tunggu sebelum mencoba kembali.",
  ],
  mfa_rate_limited: [
    "Too many verification attempts. Wait before trying again.",
    "Terlalu banyak percobaan verifikasi. Tunggu sebelum mencoba kembali.",
  ],
  sign_in_rate_limited: [
    "Too many sign-in attempts. Wait before trying again.",
    "Terlalu banyak percobaan masuk. Tunggu sebelum mencoba kembali.",
  ],
};

export function failureMessage(failure: Failure, locale: Locale): string {
  const text = errors[failure.code];
  return (
    text?.[locale === "id" ? 1 : 0] ??
    (locale === "id"
      ? "Tindakan tidak dapat diselesaikan. Silakan coba lagi."
      : "The action could not be completed. Please retry.")
  );
}
