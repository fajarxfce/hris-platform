import type { Failure } from "../../domain/result";
import type { Locale } from "./messages";

const errors: Readonly<Record<string, readonly [string, string]>> = {
  invalid_organization_unit: [
    "Enter a name and a code of 2–32 letters, numbers, underscores or hyphens.",
    "Masukkan nama dan kode 2–32 huruf, angka, underscore, atau tanda hubung.",
  ],
  invalid_unit_timezone: [
    "Enter a valid IANA timezone for a branch.",
    "Masukkan zona waktu IANA yang valid untuk cabang.",
  ],
  organization_cycle_or_depth: [
    "Choose a parent outside this unit’s descendants and within the hierarchy limit.",
    "Pilih induk di luar turunan unit ini dan dalam batas hierarki.",
  ],
  parent_unavailable: [
    "The parent is unavailable or inactive. Review the selection.",
    "Induk tidak tersedia atau nonaktif. Periksa pilihan induk.",
  ],
  invalid_parent_kind: [
    "The selected parent type is not allowed for this unit.",
    "Jenis induk yang dipilih tidak sesuai untuk unit ini.",
  ],
  unit_kind_immutable: [
    "An existing unit’s type cannot be changed.",
    "Jenis unit yang sudah ada tidak dapat diubah.",
  ],
  data_conflict: [
    "A record with these values already exists or conflicts with another record.",
    "Data dengan nilai ini sudah ada atau tidak sesuai dengan data lain.",
  ],
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
  mfa_setup_required: [
    "Set up an authenticator to continue.",
    "Siapkan authenticator untuk melanjutkan.",
  ],
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
  invalid_report_date: [
    "Choose a valid date between 1900 and 2100.",
    "Pilih tanggal yang valid antara tahun 1900 dan 2100.",
  ],
  invalid_employee_date: ["Choose a valid effective date.", "Pilih tanggal efektif yang valid."],
  invalid_employee_search: [
    "Use up to 120 characters for the search.",
    "Gunakan maksimal 120 karakter untuk pencarian.",
  ],
  invalid_organization_search: [
    "Use up to 120 characters for the search.",
    "Gunakan maksimal 120 karakter untuk pencarian.",
  ],
  invalid_organization_filter: [
    "Choose a valid unit type and status.",
    "Pilih jenis dan status unit yang valid.",
  ],
  organization_unit_not_found: [
    "The organization unit was not found or is no longer accessible.",
    "Unit organisasi tidak ditemukan atau tidak dapat diakses.",
  ],
  organization_structure_unavailable: [
    "The organization structure is unavailable. Please retry.",
    "Struktur organisasi tidak tersedia. Silakan coba lagi.",
  ],
  invalid_page: [
    "This page link is invalid. Return to the first page.",
    "Link halaman tidak valid. Kembali ke halaman pertama.",
  ],
  employee_not_found: [
    "The employee was not found for this date or is no longer accessible.",
    "Karyawan tidak ditemukan untuk tanggal ini atau tidak dapat diakses.",
  ],
  invalid_report_companies: [
    "Select between 1 and 32 different companies.",
    "Pilih 1 hingga 32 perusahaan yang berbeda.",
  ],
  invalid_audit_range: [
    "Choose a valid UTC time window of up to 90 days, ending no later than now.",
    "Pilih rentang waktu UTC yang valid, maksimal 90 hari, dengan akhir tidak melewati waktu saat ini.",
  ],
  invalid_audit_filter: [
    "Enter a valid action, resource type, or ID.",
    "Masukkan kode aksi, jenis resource, atau ID yang valid.",
  ],
  invalid_audit_cursor: [
    "This page is no longer available for these filters. Reset the filters to start again.",
    "Halaman tidak tersedia untuk filter ini. Reset filter untuk memulai kembali.",
  ],
  invalid_revision: ["Enter a revision from 0 to 9999.", "Masukkan revisi dari 0 hingga 9999."],
  invalid_pagination: [
    "This page link is invalid. Return to the first page.",
    "Link halaman tidak valid. Kembali ke halaman pertama.",
  ],
  job_not_found: [
    "The job was not found or is no longer accessible.",
    "Job tidak ditemukan atau tidak dapat diakses.",
  ],
  job_already_finished: [
    "This job has already finished. Refresh its status.",
    "Job ini sudah selesai. Muat ulang statusnya.",
  ],
  client_policy_revision_not_found: [
    "The configuration revision was not found.",
    "Revisi konfigurasi tidak ditemukan.",
  ],
  invalid_response: [
    "The service returned an invalid response. Please retry.",
    "Respons layanan tidak valid. Silakan coba lagi.",
  ],
  stale_version: [
    "This record has changed. Refresh before trying again.",
    "Data sudah berubah. Muat ulang sebelum mencoba lagi.",
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
