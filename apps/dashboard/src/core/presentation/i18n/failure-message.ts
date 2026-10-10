import type { Failure } from "../../domain/result";
import type { Locale } from "./messages";

const errors: Readonly<Record<string, readonly [string, string]>> = {
  approval_not_found: [
    "The approval request was not found or is no longer accessible.",
    "Permintaan approval tidak ditemukan atau tidak dapat diakses.",
  ],
  file_read_timeout: [
    "Reading the file timed out. Choose it again.",
    "Waktu pembacaan file habis. Pilih kembali file tersebut.",
  ],
  employee_import_file_required: ["Choose a CSV file first.", "Pilih file CSV terlebih dahulu."],
  employee_import_file_invalid: [
    "Choose a .csv file with a name of up to 120 characters.",
    "Pilih file .csv dengan nama maksimal 120 karakter.",
  ],
  employee_import_size_limit: [
    "The CSV must contain data and be no larger than 512 KiB.",
    "CSV harus berisi data dan berukuran maksimal 512 KiB.",
  ],
  invalid_employee_csv: [
    "Check the CSV headers and format. Use at most 5,000 rows and 1,024 characters per cell.",
    "Periksa header dan format CSV. Gunakan maksimal 5.000 baris dan 1.024 karakter per sel.",
  ],
  employee_csv_failure: [
    "The CSV could not be processed. Try again or contact your administrator.",
    "CSV tidak dapat diproses. Coba lagi atau hubungi administrator.",
  ],
  file_size_limit: [
    "The selected file exceeds the size limit.",
    "File terpilih melebihi batas ukuran.",
  ],
  file_encoding_invalid: [
    "Save the file as UTF-8 and choose it again.",
    "Simpan file sebagai UTF-8, lalu pilih kembali.",
  ],
  file_access_denied: [
    "The browser could not access the file. Check access and try again.",
    "Browser tidak dapat mengakses file. Periksa akses, lalu coba lagi.",
  ],
  file_unavailable: [
    "The file could not be read. Choose it again.",
    "File tidak dapat dibaca. Pilih kembali file tersebut.",
  ],
  request_body_too_large: [
    "The encoded request exceeds the API size limit. Use a smaller file.",
    "Permintaan yang dienkode melebihi batas API. Gunakan file yang lebih kecil.",
  ],
  invalid_employee_import: [
    "Review the import details and provide a reason of up to 1,000 characters.",
    "Periksa detail import dan masukkan alasan maksimal 1.000 karakter.",
  ],
  employee_import_action_unavailable: [
    "This action is unavailable. Reload the review to check current progress.",
    "Aksi ini tidak tersedia. Muat ulang tinjauan untuk memeriksa progres terbaru.",
  ],
  employee_import_not_ready: [
    "The import is not ready to apply. Reload the review.",
    "Import belum siap diterapkan. Muat ulang tinjauan.",
  ],
  employee_import_has_no_ready_rows: [
    "There are no ready rows to apply.",
    "Tidak ada baris siap untuk diterapkan.",
  ],
  employee_import_has_invalid_rows: [
    "Some rows are invalid. Confirm that they should be skipped before applying ready rows.",
    "Sebagian baris tidak valid. Konfirmasikan untuk melewatinya sebelum menerapkan baris siap.",
  ],
  employee_import_job_not_stopped: [
    "The job has not stopped. Reload the review before resuming.",
    "Job belum berhenti. Muat ulang tinjauan sebelum melanjutkan.",
  ],
  employee_import_is_terminal: [
    "This import is already completed or cancelled.",
    "Import ini sudah selesai atau dibatalkan.",
  ],
  employee_import_job_missing: [
    "The import job is unavailable. Contact your administrator.",
    "Job import tidak tersedia. Hubungi administrator.",
  ],
  employee_import_job_mismatch: [
    "The import job could not be verified. Contact your administrator.",
    "Job import tidak dapat diverifikasi. Hubungi administrator.",
  ],
  job_queue_full: [
    "The job queue is full. Try again later.",
    "Antrean job penuh. Coba lagi nanti.",
  ],
  stale_job_version: [
    "The job has changed. Reload the review.",
    "Job sudah berubah. Muat ulang tinjauan.",
  ],
  employee_import_access_required: [
    "You do not have access to employee imports.",
    "Anda tidak memiliki akses ke import karyawan.",
  ],
  employee_import_not_found: [
    "This import is unavailable in the selected company.",
    "Import ini tidak tersedia di perusahaan yang dipilih.",
  ],
  offboarding_access_required: [
    "You do not have access to review or complete offboarding.",
    "Anda tidak memiliki akses untuk meninjau atau menyelesaikan offboarding.",
  ],
  invalid_offboarding: [
    "Review the offboarding details and provide a reason of up to 1,000 characters.",
    "Periksa detail offboarding dan masukkan alasan maksimal 1.000 karakter.",
  ],
  stale_employment_version: [
    "This employment has changed. Reload the review before completing offboarding.",
    "Employment sudah berubah. Muat ulang tinjauan sebelum menyelesaikan offboarding.",
  ],
  offboarding_terms_changed: [
    "The last working date does not match the employment terms. Review the employment and case dates.",
    "Hari kerja terakhir tidak sesuai dengan ketentuan employment. Periksa tanggal employment dan proses.",
  ],
  other_lifecycle_cases_pending: [
    "Complete or cancel the other open lifecycle cases for this employment first.",
    "Selesaikan atau batalkan proses lifecycle lain yang masih terbuka untuk employment ini.",
  ],
  offboarding_date_not_reached: [
    "Offboarding can be completed after the last working date in the company timezone.",
    "Offboarding dapat diselesaikan setelah hari kerja terakhir dalam zona waktu perusahaan.",
  ],
  cannot_complete_own_offboarding: [
    "Another authorized operator must complete your offboarding.",
    "Offboarding Anda harus diselesaikan oleh operator lain yang berwenang.",
  ],
  scheduled_employment_changes_pending: [
    "Resolve the scheduled employment revisions before completing offboarding.",
    "Selesaikan revisi employment yang terjadwal sebelum menyelesaikan offboarding.",
  ],
  reporting_reassignment_required: [
    "Reassign current and scheduled reporting lines before completing offboarding.",
    "Alihkan hubungan pelaporan yang aktif dan terjadwal sebelum menyelesaikan offboarding.",
  ],
  last_company_administrator: [
    "Assign another active company administrator before removing this access.",
    "Tetapkan administrator perusahaan aktif lainnya sebelum mencabut akses ini.",
  ],
  invalid_lifecycle_case: [
    "Choose a template, a target date between 1900 and 2200, and a reason of up to 1,000 characters.",
    "Pilih template, tanggal target antara tahun 1900 dan 2200, serta alasan maksimal 1.000 karakter.",
  ],
  lifecycle_template_unavailable: [
    "Choose an active template from the selected company.",
    "Pilih template aktif dari perusahaan yang dipilih.",
  ],
  stale_template_version: [
    "This template has changed. Choose it again and review the updated checklist.",
    "Template sudah berubah. Pilih kembali dan periksa checklist terbaru.",
  ],
  employment_unavailable: [
    "This employment is unavailable on the target date. Review the date and employee details.",
    "Employment tidak tersedia pada tanggal target. Periksa tanggal dan detail karyawan.",
  ],
  invalid_lifecycle_assignment: [
    "Choose a member or remove the assignee, and provide a reason of up to 1,000 characters.",
    "Pilih anggota atau hapus penanggung jawab, lalu masukkan alasan maksimal 1.000 karakter.",
  ],
  lifecycle_assignee_unavailable: [
    "This member is no longer eligible. Choose another member.",
    "Anggota ini tidak lagi memenuhi syarat. Pilih anggota lain.",
  ],
  lifecycle_task_not_assignable: [
    "Only a pending task can be assigned to a different member. Reload the task list.",
    "Hanya tugas tertunda yang dapat dialihkan ke anggota lain. Muat ulang daftar tugas.",
  ],
  invalid_lifecycle_change: [
    "Check the lifecycle action and provide a reason of up to 1,000 characters.",
    "Periksa tindakan lifecycle dan masukkan alasan maksimal 1.000 karakter.",
  ],
  required_lifecycle_tasks_pending: [
    "Complete every required task before completing this case. Reload the checklist.",
    "Selesaikan semua tugas wajib sebelum menyelesaikan proses. Muat ulang checklist.",
  ],
  lifecycle_tasks_unresolved: [
    "Resolve the remaining tasks before completing this case. Reload the checklist.",
    "Selesaikan tugas yang tersisa sebelum menyelesaikan proses. Muat ulang checklist.",
  ],
  lifecycle_task_not_found: [
    "This task is no longer available. Reload the task list.",
    "Tugas ini tidak lagi tersedia. Muat ulang daftar tugas.",
  ],
  lifecycle_task_not_assigned: [
    "This task is no longer assigned to you. Reload the task list.",
    "Tugas ini tidak lagi ditetapkan untuk Anda. Muat ulang daftar tugas.",
  ],
  lifecycle_case_not_open: [
    "This case is no longer open. Reload the task list.",
    "Proses ini tidak lagi terbuka. Muat ulang daftar tugas.",
  ],
  lifecycle_task_cannot_be_waived: [
    "Only a lifecycle manager can waive an optional task.",
    "Hanya pengelola lifecycle yang dapat mengecualikan tugas opsional.",
  ],
  lifecycle_task_unchanged: [
    "This task already has that status. Reload the task list.",
    "Status tugas sudah sama. Muat ulang daftar tugas.",
  ],
  lifecycle_case_not_found: [
    "This lifecycle case is unavailable in the selected company.",
    "Proses lifecycle tidak tersedia di perusahaan yang dipilih.",
  ],
  lifecycle_access_required: [
    "You do not have access to these lifecycle tasks.",
    "Anda tidak memiliki akses ke tugas lifecycle ini.",
  ],
  invalid_lifecycle_template: [
    "Check the template name, code, reason, and checklist. Use 1–64 tasks with unique keys and due offsets from −90 to 365 days.",
    "Periksa nama, kode, alasan, dan checklist template. Gunakan 1–64 tugas dengan key unik dan offset −90 hingga 365 hari.",
  ],
  lifecycle_template_not_found: [
    "This template is unavailable in the selected company.",
    "Template tidak tersedia di perusahaan yang dipilih.",
  ],
  lifecycle_template_exists: [
    "This template already exists. Reload its current version.",
    "Template sudah tersedia. Muat ulang versi terbarunya.",
  ],
  lifecycle_template_limit: [
    "This company has reached its limit of 128 templates.",
    "Perusahaan sudah mencapai batas 128 template.",
  ],
  lifecycle_template_identity_immutable: [
    "The template code and type cannot be changed.",
    "Kode dan jenis template tidak dapat diubah.",
  ],
  invalid_client_policy: [
    "Check the highlighted policy values.",
    "Periksa nilai policy yang ditandai.",
  ],
  client_policy_activation_expired: [
    "The activation time has passed. Choose a new time or immediate activation.",
    "Waktu aktivasi sudah lewat. Pilih waktu baru atau aktivasi langsung.",
  ],
  client_policy_activation_too_late: [
    "Schedule activation within 365 days.",
    "Jadwalkan aktivasi dalam 365 hari.",
  ],
  client_policy_revision_limit: [
    "The company has reached its policy revision limit.",
    "Perusahaan sudah mencapai batas revisi policy.",
  ],
  client_policy_clock_regressed: [
    "The server clock must recover before another policy can be saved.",
    "Jam server harus pulih sebelum policy lain dapat disimpan.",
  ],
  invalid_employment_revision: [
    "Choose a valid employment revision.",
    "Pilih revisi employment yang valid.",
  ],
  invalid_revision_cancellation: [
    "Check the revision and reload its current version.",
    "Periksa revisi dan muat ulang versi terbarunya.",
  ],
  employment_revision_not_found: [
    "The employment revision is unavailable.",
    "Revisi employment tidak tersedia.",
  ],
  revision_already_cancelled: [
    "This revision has already been cancelled. Reload its details.",
    "Revisi ini sudah dibatalkan. Muat ulang detailnya.",
  ],
  effective_revision_cannot_be_cancelled: [
    "This revision has already taken effect and cannot be cancelled.",
    "Revisi ini sudah berlaku dan tidak dapat dibatalkan.",
  ],
  invalid_employment_change: [
    "Check the employment terms and use the latest version.",
    "Periksa data employment dan gunakan versi terbaru.",
  ],
  employment_start_immutable: [
    "The employment start date cannot be changed.",
    "Tanggal mulai employment tidak dapat diubah.",
  ],
  employment_offboarded: [
    "This employment has completed offboarding. Review its lifecycle record.",
    "Employment sudah menyelesaikan offboarding. Periksa data lifecycle-nya.",
  ],
  transferred_employment_closed: [
    "This employment was closed by a company transfer.",
    "Employment ini ditutup oleh transfer perusahaan.",
  ],
  reporting_cycle_or_depth: [
    "This employment change creates a reporting cycle or exceeds the hierarchy limit.",
    "Perubahan employment membentuk siklus pelaporan atau melewati batas hierarki.",
  ],
  invalid_employee_creation: [
    "Check the employee and assignment details.",
    "Periksa data karyawan dan penempatannya.",
  ],
  invalid_employee_number: [
    "Use an employee number of 2–32 letters, numbers, underscores or hyphens.",
    "Gunakan nomor karyawan 2–32 huruf, angka, underscore, atau tanda hubung.",
  ],
  reason_required: [
    "Enter a reason of up to 1,000 characters.",
    "Masukkan alasan maksimal 1.000 karakter.",
  ],
  invalid_employment_dates: [
    "Choose valid employment dates. The end cannot precede the start.",
    "Pilih tanggal employment yang valid. Tanggal berakhir tidak boleh sebelum tanggal mulai.",
  ],
  invalid_fixed_term_contract: [
    "A fixed-term contract requires an end date and cannot use probation status.",
    "Kontrak waktu tertentu memerlukan tanggal berakhir dan tidak dapat memakai status masa percobaan.",
  ],
  end_date_required: [
    "An ended employment requires an end date.",
    "Employment yang berakhir memerlukan tanggal berakhir.",
  ],
  organization_assignment_unavailable: [
    "An organization assignment is no longer available. Review the selection.",
    "Penempatan organisasi tidak tersedia. Periksa kembali pilihan penempatan.",
  ],
  manager_unavailable: [
    "Choose a manager who is working on the proposed effective date.",
    "Pilih manager yang bekerja pada tanggal efektif yang diajukan.",
  ],
  person_profile_not_found: [
    "The profile was not found or is no longer accessible.",
    "Profil tidak ditemukan atau tidak dapat diakses.",
  ],
  profile_owner_required: [
    "Edit this profile from its owning company.",
    "Edit profil melalui perusahaan pemiliknya.",
  ],
  invalid_person: [
    "Check the highlighted personal details.",
    "Periksa data pribadi yang ditandai.",
  ],
  invalid_profile_change: [
    "Enter a reason of up to 1,000 characters and use the latest profile version.",
    "Masukkan alasan maksimal 1.000 karakter dan gunakan versi profil terbaru.",
  ],
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
  invalid_revision: [
    "Enter a valid non-negative revision number.",
    "Masukkan nomor revisi nonnegatif yang valid.",
  ],
  invalid_decimal_amount: [
    "Enter an amount in decimal notation.",
    "Masukkan nominal dalam format desimal.",
  ],
  invalid_approval_template: [
    "Check the template name, dates, amount, stages, and reason.",
    "Periksa nama template, tanggal, nominal, tahap, dan alasan.",
  ],
  invalid_approval_stage: [
    "Check the assignment and approvers for each stage.",
    "Periksa penugasan dan approver pada setiap tahap.",
  ],
  approval_template_not_found: [
    "The approval template or revision was not found.",
    "Template approval atau revisinya tidak ditemukan.",
  ],
  approval_kind_immutable: [
    "The approval type cannot be changed. Reload the current template.",
    "Jenis approval tidak dapat diubah. Muat ulang template terkini.",
  ],
  approver_unavailable: [
    "An approver is no longer available. Review the selected accounts.",
    "Approver tidak lagi tersedia. Periksa akun yang dipilih.",
  ],
  payroll_approval_assignment_invalid: [
    "Payroll stages require selected accounts or payroll review permission.",
    "Tahap payroll memerlukan akun pilihan atau permission review payroll.",
  ],
  approval_template_limit: [
    "The company has reached the approval template limit.",
    "Jumlah template approval perusahaan telah mencapai batas.",
  ],
  approval_policy_capacity: [
    "The approval policy has reached its capacity. Contact an administrator.",
    "Kapasitas kebijakan approval telah tercapai. Hubungi administrator.",
  ],
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
