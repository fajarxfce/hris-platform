import type { Failure } from "../../domain/result";
import type { Locale } from "./messages";

const errors: Readonly<Record<string, readonly [string, string]>> = {
  invalid_oidc_identity: [
    "Review the configured provider, identity values and reason.",
    "Periksa konfigurasi provider, nilai identitas, dan alasan perubahan.",
  ],
  oidc_identity_not_found: [
    "This SSO binding is unavailable for this account.",
    "Binding SSO ini tidak tersedia untuk akun ini.",
  ],
  oidc_binding_immutable: [
    "Issuer and subject cannot change. Review the current binding.",
    "Issuer dan subject tidak dapat diubah. Tinjau binding terbaru.",
  ],
  oidc_identity_limit: [
    "This account has reached its active SSO binding limit.",
    "Akun ini telah mencapai batas binding SSO aktif.",
  ],
  account_disabled: [
    "This account is inactive. Review its account status before activation.",
    "Akun ini nonaktif. Tinjau status akun sebelum aktivasi.",
  ],
  invalid_invitation: [
    "Review the recipient, name and reason.",
    "Periksa penerima, nama, dan alasan undangan.",
  ],
  account_cannot_be_invited: [
    "This account cannot receive an invitation. Review its current status.",
    "Akun ini tidak dapat menerima undangan. Tinjau status terbarunya.",
  ],
  mail_not_configured: [
    "Email delivery is not configured. Contact your administrator.",
    "Pengiriman email belum dikonfigurasi. Hubungi administrator.",
  ],
  credential_link_unavailable: [
    "This link is invalid, expired or already used.",
    "Tautan ini tidak valid, kedaluwarsa, atau sudah digunakan.",
  ],
  invalid_recovery_request: ["Enter a valid email address.", "Masukkan alamat email yang valid."],
  password_length: [
    "Use a password of 12–128 characters.",
    "Gunakan password sepanjang 12–128 karakter.",
  ],
  invalid_account_access: [
    "Review the account status, platform permissions and reason.",
    "Periksa status akun, permission platform, dan alasan perubahan.",
  ],
  account_not_found: [
    "This account is unavailable for your current access.",
    "Akun ini tidak tersedia untuk akses Anda saat ini.",
  ],
  invitation_acceptance_required: [
    "The invitation must be accepted before this account can be activated.",
    "Undangan harus diterima sebelum akun ini dapat diaktifkan.",
  ],
  invalid_membership: [
    "Review the permissions, role versions and reason.",
    "Periksa permission, versi role, dan alasan perubahan.",
  ],
  account_unavailable: [
    "This account cannot receive active company access. Review its account status.",
    "Akun ini belum dapat menerima akses perusahaan aktif. Periksa status akunnya.",
  ],
  company_member_already_exists: [
    "This account already has company access. Review its existing access before changing it.",
    "Akun ini sudah memiliki akses perusahaan. Tinjau akses yang sudah ada sebelum mengubahnya.",
  ],
  role_template_unavailable: [
    "Choose an active role from this company, or retain an already applied version.",
    "Pilih role aktif dalam perusahaan ini, atau pertahankan versi yang sudah diterapkan.",
  ],
  role_template_review_required: [
    "Review active role versions before granting or reactivating company access.",
    "Tinjau versi role yang aktif sebelum memberikan atau mengaktifkan kembali akses perusahaan.",
  ],
  stale_role_template: [
    "A selected role version changed. Review its current definition or retain the applied version.",
    "Versi role yang dipilih berubah. Tinjau definisi terbaru atau pertahankan versi yang sudah diterapkan.",
  ],
  cannot_remove_own_administration: [
    "You cannot remove your own administration access.",
    "Anda tidak dapat mencabut akses administrasi Anda sendiri.",
  ],
  cannot_self_grant_sensitive_access: [
    "An independent platform administrator must approve this sensitive access.",
    "Administrator platform lain harus menyetujui akses sensitif ini.",
  ],
  platform_administrator_required: [
    "This action requires platform administration access.",
    "Tindakan ini memerlukan akses administrasi platform.",
  ],
  invalid_role_template: [
    "Check the role name, code, permissions and reason.",
    "Periksa nama, kode, permission, dan alasan perubahan role.",
  ],
  role_template_not_found: [
    "This role is unavailable for your current company access.",
    "Role ini tidak tersedia untuk akses perusahaan saat ini.",
  ],
  role_template_limit: [
    "This company has reached its limit of 128 role definitions. Review an existing role.",
    "Perusahaan ini sudah memiliki 128 definisi role. Tinjau role yang sudah ada.",
  ],
  role_code_immutable: [
    "A saved role code cannot change. Reload its current definition.",
    "Kode role yang sudah disimpan tidak dapat diubah. Muat ulang definisinya.",
  ],

  invalid_leave_calendar_month: [
    "Choose a valid month between 1900 and 2200.",
    "Pilih bulan yang valid antara 1900 dan 2200.",
  ],
  invalid_leave_calendar_date: [
    "Choose a valid date in the displayed month.",
    "Pilih tanggal yang valid pada bulan yang ditampilkan.",
  ],
  invalid_leave_calendar_scope: ["Choose Company or My team.", "Pilih Perusahaan atau Tim saya."],
  invalid_onboarding_schedule: [
    "Check the preparation date, reviewed checklist and reason.",
    "Periksa tanggal persiapan, checklist yang ditinjau, dan alasannya.",
  ],
  onboarding_access_required: [
    "Onboarding schedule access is unavailable.",
    "Akses jadwal onboarding tidak tersedia.",
  ],
  onboarding_already_completed: [
    "This employee has already completed onboarding.",
    "Karyawan ini sudah menyelesaikan onboarding.",
  ],
  onboarding_schedule_changed: [
    "The employment, template or company timezone changed. Reload and review the schedule.",
    "Employment, template, atau zona waktu perusahaan berubah. Muat ulang dan tinjau jadwal.",
  ],
  onboarding_schedule_active: [
    "This employee already has an active schedule. Review its status first.",
    "Karyawan ini sudah memiliki jadwal aktif. Periksa statusnya terlebih dahulu.",
  ],
  onboarding_schedule_not_future: [
    "Choose a future preparation date. Use manual checklist creation for a date that is already due.",
    "Pilih tanggal persiapan mendatang. Buat checklist secara manual jika tanggalnya sudah tiba.",
  ],
  onboarding_schedule_too_far: [
    "The first working day must be within the next 365 days.",
    "Hari pertama bekerja harus berada dalam 365 hari ke depan.",
  ],
  onboarding_schedule_limit: [
    "This employee has reached the limit of eight scheduled attempts.",
    "Karyawan ini sudah mencapai batas delapan percobaan terjadwal.",
  ],
  onboarding_schedule_finished: [
    "This schedule has finished. Reload its current status.",
    "Jadwal ini sudah selesai. Muat ulang statusnya.",
  ],
  onboarding_schedule_obsolete: [
    "This authorization no longer matches the current process. Reload its status.",
    "Otorisasi ini tidak sesuai lagi dengan proses saat ini. Muat ulang statusnya.",
  ],
  onboarding_schedule_not_found: [
    "This schedule is unavailable for your current access.",
    "Jadwal ini tidak tersedia untuk akses Anda saat ini.",
  ],
  onboarding_job_not_stopped: [
    "The job has not stopped. Reload its status before recovery.",
    "Proses belum berhenti. Muat ulang statusnya sebelum pemulihan.",
  ],
  onboarding_job_result_invalid: [
    "The job result needs administrator review.",
    "Hasil proses perlu ditinjau administrator.",
  ],
  onboarding_progress_invalid: [
    "The job progress needs administrator review.",
    "Progres proses perlu ditinjau administrator.",
  ],
  onboarding_revision_limit: [
    "The employment has reached its revision limit. Contact an administrator.",
    "Employment telah mencapai batas revisi. Hubungi administrator.",
  ],
  onboarding_template_unavailable: [
    "Choose an active onboarding template.",
    "Pilih template onboarding yang aktif.",
  ],
  onboarding_terms_changed: [
    "The reviewed first working day changed. Reload the employment before scheduling.",
    "Hari pertama bekerja yang ditinjau berubah. Muat ulang employment sebelum menjadwalkan.",
  ],
  onboarding_schedule_review_required: [
    "The execution window ended. Review the employee for manual checklist creation.",
    "Batas waktu eksekusi sudah lewat. Tinjau karyawan untuk pembuatan checklist manual.",
  ],
  onboarding_schedule_not_due: [
    "The scheduled preparation time has not been reached.",
    "Waktu persiapan terjadwal belum tiba.",
  ],
  onboarding_execution_failed: [
    "The scheduled checklist could not be created. Review the job result.",
    "Checklist terjadwal tidak dapat dibuat. Tinjau hasil prosesnya.",
  ],
  invalid_offboarding_schedule: [
    "Check the reviewed schedule and provide a reason.",
    "Periksa jadwal yang ditinjau dan isi alasannya.",
  ],
  offboarding_schedule_changed: [
    "The case, employment or company timezone changed. Reload and review before scheduling.",
    "Proses, employment, atau zona waktu perusahaan berubah. Muat ulang dan tinjau sebelum menjadwalkan.",
  ],
  offboarding_identity_changed: [
    "The employee account changed. Review the current employment before continuing.",
    "Akun karyawan berubah. Periksa employment saat ini sebelum melanjutkan.",
  ],
  offboarding_schedule_active: [
    "This case already has an active schedule. Review its status first.",
    "Proses ini sudah memiliki jadwal aktif. Periksa statusnya terlebih dahulu.",
  ],
  offboarding_schedule_not_future: [
    "The scheduling boundary has passed. Review manual offboarding completion.",
    "Batas penjadwalan sudah lewat. Tinjau penyelesaian offboarding manual.",
  ],
  offboarding_schedule_too_far: [
    "The last working date must be within the next 365 days.",
    "Hari kerja terakhir harus berada dalam 365 hari ke depan.",
  ],
  offboarding_schedule_limit: [
    "This case has reached its limit of eight scheduled attempts.",
    "Proses ini sudah mencapai batas delapan percobaan terjadwal.",
  ],
  offboarding_schedule_finished: [
    "This schedule has finished. Reload its current status.",
    "Jadwal ini sudah selesai. Muat ulang statusnya.",
  ],
  offboarding_schedule_obsolete: [
    "This authorization no longer matches the current process. Reload its status.",
    "Otorisasi ini tidak sesuai lagi dengan proses saat ini. Muat ulang statusnya.",
  ],
  offboarding_schedule_not_found: [
    "This schedule is unavailable for your current access.",
    "Jadwal ini tidak tersedia untuk akses Anda saat ini.",
  ],
  offboarding_job_not_stopped: [
    "The worker has not stopped. Request cancellation or wait for completion.",
    "Worker belum berhenti. Ajukan pembatalan atau tunggu hingga selesai.",
  ],
  offboarding_job_result_invalid: [
    "The stopped job requires investigation before recovery. Contact an administrator.",
    "Hasil job perlu diperiksa sebelum dipulihkan. Hubungi administrator.",
  ],
  offboarding_revision_limit: [
    "This case or employment has reached its revision limit. Contact an administrator.",
    "Proses atau employment telah mencapai batas revisi. Hubungi administrator.",
  ],
  offboarding_schedule_review_required: [
    "The execution window ended. Review the case for manual completion.",
    "Batas waktu eksekusi sudah lewat. Tinjau proses untuk penyelesaian manual.",
  ],
  offboarding_schedule_not_due: [
    "The scheduled execution time has not been reached.",
    "Waktu eksekusi terjadwal belum tiba.",
  ],
  offboarding_execution_failed: [
    "Scheduled execution stopped. Review the case and job status.",
    "Eksekusi terjadwal berhenti. Periksa proses dan status job.",
  ],
  offboarding_progress_invalid: [
    "Execution progress is inconsistent. Contact an administrator.",
    "Progres eksekusi tidak konsisten. Hubungi administrator.",
  ],
  offboarding_execution_date_changed: [
    "The company date changed during execution. Review the current job status.",
    "Tanggal perusahaan berubah saat eksekusi. Periksa status job saat ini.",
  ],

  invalid_payroll_amendment_publication: [
    "Check the approved amendment, observed versions and publication reason.",
    "Periksa koreksi yang disetujui, versi yang ditinjau, dan alasan publikasi.",
  ],
  invalid_job_version: [
    "Reload worker progress before continuing.",
    "Muat ulang progres worker sebelum melanjutkan.",
  ],
  payroll_amendment_approved_review_required: [
    "Publication requires an approved current amendment review.",
    "Publikasi memerlukan peninjauan koreksi terbaru yang sudah disetujui.",
  ],
  payroll_amendment_publication_active: [
    "This amendment already has an active publication. Review its progress.",
    "Koreksi ini sudah memiliki publikasi aktif. Periksa progresnya.",
  ],
  payroll_amendment_publication_limit: [
    "This amendment has reached its limit of eight publication attempts.",
    "Koreksi ini sudah mencapai batas delapan percobaan publikasi.",
  ],
  payroll_amendment_publication_not_found: [
    "This amendment publication is unavailable for your current access.",
    "Publikasi koreksi ini tidak tersedia untuk akses Anda saat ini.",
  ],
  payroll_amendment_publication_changed: [
    "The amendment or publication changed. Reload before continuing.",
    "Koreksi atau publikasi sudah berubah. Muat ulang sebelum melanjutkan.",
  ],
  payroll_amendment_publication_obsolete: [
    "This attempt no longer matches the approved amendment. Review its current state.",
    "Percobaan ini tidak lagi sesuai dengan koreksi yang disetujui. Tinjau status terbaru.",
  ],
  payroll_amendment_recovery_unavailable: [
    "Recovery requires the latest pending publication and a stopped worker with no completed items.",
    "Pemulihan memerlukan publikasi tertunda terbaru dan worker terhenti tanpa item selesai.",
  ],
  payroll_amendment_review_not_found: [
    "This amendment review is unavailable for your current access.",
    "Review koreksi ini tidak tersedia untuk akses Anda saat ini.",
  ],
  payroll_amendment_calculation_required: [
    "A successful current calculation is required before submitting this amendment.",
    "Koreksi ini memerlukan perhitungan terbaru yang berhasil sebelum diajukan.",
  ],
  payroll_amendment_review_active: [
    "This amendment already has an active review. Reload its current state.",
    "Koreksi ini sudah memiliki review aktif. Muat ulang status terbaru.",
  ],
  payroll_amendment_recalculation_required: [
    "Update the proposal and calculate it again before resubmitting.",
    "Perbarui proposal dan hitung kembali sebelum mengajukan ulang.",
  ],
  payroll_amendment_review_limit: [
    "This amendment has reached its limit of eight reviews.",
    "Koreksi ini sudah mencapai batas delapan review.",
  ],
  payroll_amendment_review_not_pending: [
    "This review is no longer awaiting a decision. Reload its current state.",
    "Review ini tidak lagi menunggu keputusan. Muat ulang status terbaru.",
  ],
  payroll_amendment_review_not_active: [
    "This review can no longer be withdrawn. Reload its current state.",
    "Review ini tidak dapat ditarik lagi. Muat ulang status terbaru.",
  ],
  payroll_amendment_review_not_current: [
    "The amendment review changed. Reload it before continuing.",
    "Review koreksi sudah berubah. Muat ulang sebelum melanjutkan.",
  ],
  payroll_amendment_review_action_unavailable: [
    "This action is unavailable for the current amendment review.",
    "Tindakan ini tidak tersedia pada review koreksi saat ini.",
  ],
  invalid_payroll_amendment: [
    "Check the proposed changes and amendment reason.",
    "Periksa perubahan yang diusulkan dan alasan koreksi.",
  ],
  invalid_payroll_amendment_corrections: [
    "Select corrections for the first month and any affected later months.",
    "Pilih koreksi untuk bulan pertama dan bulan berikutnya yang terdampak.",
  ],
  payroll_amendment_sources_changed: [
    "Published payroll has changed. Reload and review its current revisions.",
    "Payroll terpublikasi sudah berubah. Muat ulang dan tinjau revisi terbaru.",
  ],
  payroll_amendment_tax_evidence_changed: [
    "Tax registration must match the published payroll.",
    "Registrasi pajak harus sesuai dengan payroll terpublikasi.",
  ],
  payroll_amendment_not_editable: [
    "This proposal no longer allows the requested change. Reload its current state.",
    "Proposal ini tidak lagi mengizinkan perubahan tersebut. Muat ulang status terbaru.",
  ],
  payroll_amendment_basis_changed: [
    "The first payroll month cannot be changed for an existing proposal.",
    "Bulan payroll pertama tidak dapat diubah pada proposal yang sudah ada.",
  ],
  payroll_amendment_sources_invalid: [
    "The published payroll basis cannot be used. Reload it before continuing.",
    "Dasar payroll terpublikasi tidak dapat digunakan. Muat ulang sebelum melanjutkan.",
  ],
  payroll_amendment_revision_limit: [
    "This amendment has reached its revision limit.",
    "Koreksi ini sudah mencapai batas revisi.",
  ],
  payroll_amendment_calculation_limit: [
    "This proposal has reached its calculation attempt limit. It can still be cancelled.",
    "Proposal ini sudah mencapai batas percobaan perhitungan. Proposal masih dapat dibatalkan.",
  ],
  payroll_amendment_not_found: [
    "This amendment or revision is unavailable for your current access.",
    "Koreksi atau revisi ini tidak tersedia untuk akses Anda saat ini.",
  ],
  payroll_amendment_calculation_not_found: [
    "This retained calculation is unavailable.",
    "Hasil perhitungan tersimpan ini tidak tersedia.",
  ],
  payroll_amendment_self_preparation_forbidden: [
    "An independent operator must review amendments to your payroll.",
    "Operator independen harus meninjau koreksi payroll Anda.",
  ],
  invalid_payroll_payslip_search: [
    "Enter up to 120 characters without control characters.",
    "Masukkan maksimal 120 karakter tanpa karakter kontrol.",
  ],
  invalid_payslip_revision: [
    "Select a valid payslip revision.",
    "Pilih revisi payslip yang valid.",
  ],
  payroll_payment_observation_required: [
    "Review the current payslip revision and payment balance before preparing instructions.",
    "Periksa revisi payslip dan saldo pembayaran saat ini sebelum menyiapkan instruksi.",
  ],
  invalid_payroll_payment_observation: [
    "Review the selected payslip versions and amounts.",
    "Periksa versi dan nominal payslip yang dipilih.",
  ],
  payroll_payment_balance_changed: [
    "The payment balance changed. Review the current amount before continuing.",
    "Saldo pembayaran berubah. Periksa nominal saat ini sebelum melanjutkan.",
  ],
  invalid_payroll_payment_recovery: [
    "Review the return amount, bank reference, confirmation time and note.",
    "Periksa nominal pengembalian, referensi bank, waktu konfirmasi, dan catatan.",
  ],
  payroll_payment_recovery_unavailable: [
    "Recording a return is unavailable. Reload the current review.",
    "Pengembalian belum dapat dicatat. Muat ulang tinjauan saat ini.",
  ],
  payroll_payment_recovery_exceeds_balance: [
    "The return exceeds the current balance or predates its publication. Reload the review.",
    "Pengembalian melebihi saldo saat ini atau mendahului publikasinya. Muat ulang tinjauan.",
  ],
  payroll_payment_recovery_limit: [
    "This payslip has reached the limit of 20 return records.",
    "Payslip ini telah mencapai batas 20 catatan pengembalian.",
  ],
  payroll_payment_recovery_exists: [
    "This return is already recorded. Review its history.",
    "Pengembalian ini sudah dicatat. Periksa riwayatnya.",
  ],
  payroll_self_recovery_forbidden: [
    "Another authorized finance operator must record your return.",
    "Operator finance lain yang berwenang harus mencatat pengembalian Anda.",
  ],
  payroll_amendment_payment_pending: [
    "Resolve prepared or pending payments before continuing.",
    "Selesaikan pembayaran yang disiapkan atau tertunda sebelum melanjutkan.",
  ],
  payroll_payslip_not_found: [
    "This payslip is unavailable for your current access.",
    "Payslip tidak tersedia untuk akses Anda saat ini.",
  ],
  payroll_scope_limit: [
    "The account has too many linked employments. Contact your administrator.",
    "Akun terhubung ke terlalu banyak data kepegawaian. Hubungi administrator.",
  ],
  payroll_pdf_text_unsupported: [
    "The PDF font does not support some document text. The payslip remains available on this page.",
    "Font PDF tidak mendukung sebagian teks dokumen. Payslip tetap tersedia di halaman ini.",
  ],
  payroll_pdf_capacity: [
    "PDF generation is busy. Try downloading again shortly.",
    "Pembuatan PDF sedang penuh. Coba download kembali sebentar lagi.",
  ],
  payroll_pdf_generation_limit: [
    "The payslip exceeds the PDF generation limit. Contact your administrator.",
    "Payslip melebihi batas pembuatan PDF. Hubungi administrator.",
  ],
  payroll_pdf_generation_timeout: [
    "PDF generation timed out. Try downloading again.",
    "Pembuatan PDF melewati batas waktu. Coba download kembali.",
  ],
  invalid_document_language: [
    "Select a supported PDF language.",
    "Pilih bahasa PDF yang didukung.",
  ],
  invalid_payroll_finalization: [
    "Review the approved payroll versions and reason.",
    "Periksa versi payroll yang disetujui dan alasan.",
  ],
  payroll_finalization_not_found: ["The publication was not found.", "Publikasi tidak ditemukan."],
  payroll_finalization_obsolete: [
    "The publication references are unavailable. Reload its current status.",
    "Referensi publikasi tidak tersedia. Muat ulang status saat ini.",
  ],
  payroll_finalization_action_unavailable: [
    "Publication is unavailable. Review the current payroll status.",
    "Publikasi tidak tersedia. Periksa status payroll saat ini.",
  ],
  payroll_already_finalized: [
    "This payroll has already been finalized.",
    "Payroll ini sudah difinalisasi.",
  ],
  payroll_successful_calculation_required: [
    "Complete a successful calculation before finalizing payroll.",
    "Selesaikan perhitungan yang berhasil sebelum finalisasi payroll.",
  ],
  payroll_approved_review_required: [
    "An approved review of the current calculation is required.",
    "Diperlukan tinjauan yang disetujui untuk perhitungan saat ini.",
  ],
  payroll_finalization_capacity: [
    "This run has reached its limit of eight publication attempts.",
    "Proses ini telah mencapai batas delapan percobaan publikasi.",
  ],
  payroll_person_duplicated: [
    "Payroll contains a duplicate person for this tax month. Review the employee references.",
    "Payroll memuat orang yang sama lebih dari sekali pada bulan pajak ini. Periksa referensi karyawan.",
  ],
  payroll_holiday_already_assessed: [
    "This holiday allowance has already been assessed. Withdraw the review and recalculate.",
    "THR ini sudah diperhitungkan. Tarik tinjauan lalu hitung ulang.",
  ],
  payroll_tax_history_changed: [
    "Tax history changed. Withdraw the review and recalculate.",
    "Riwayat pajak berubah. Tarik tinjauan lalu hitung ulang.",
  ],

  invalid_payroll_review: [
    "Review the payroll versions and reason.",
    "Periksa versi payroll dan alasan.",
  ],
  payroll_review_not_found: [
    "The payroll review was not found.",
    "Tinjauan payroll tidak ditemukan.",
  ],
  payroll_review_action_unavailable: [
    "This action is unavailable. Return to the review for its current status.",
    "Tindakan tidak tersedia. Kembali ke tinjauan untuk memeriksa status saat ini.",
  ],
  payroll_calculation_required: [
    "Complete a successful calculation before submitting it for review.",
    "Selesaikan perhitungan yang berhasil sebelum mengajukan tinjauan.",
  ],
  payroll_calculation_has_failures: [
    "Resolve the failed employee results and recalculate before submitting.",
    "Perbaiki hasil karyawan yang gagal lalu hitung ulang sebelum mengajukan.",
  ],
  payroll_review_active: [
    "This run already has a pending or approved review.",
    "Proses ini sudah memiliki tinjauan yang menunggu atau disetujui.",
  ],
  payroll_recalculation_required: [
    "This run was rejected. Start a new calculation before submitting another review.",
    "Proses ini ditolak. Mulai perhitungan baru sebelum mengajukan tinjauan lain.",
  ],
  payroll_review_capacity: [
    "This run has reached its limit of eight review rounds.",
    "Proses ini telah mencapai batas delapan pengajuan tinjauan.",
  ],
  payroll_review_not_pending: [
    "This review is no longer awaiting a decision. Reload its current status.",
    "Tinjauan tidak lagi menunggu keputusan. Muat ulang status saat ini.",
  ],
  payroll_review_not_active: [
    "This review is no longer active. Reload its current status.",
    "Tinjauan tidak lagi aktif. Muat ulang status saat ini.",
  ],
  payroll_review_not_current: [
    "The calculation changed. Return to its current review.",
    "Perhitungan berubah. Kembali ke tinjauan saat ini.",
  ],
  payroll_finalization_active: [
    "A publication is queued or running. Wait for it to finish or cancel its job.",
    "Publikasi sedang dalam antrean atau berjalan. Tunggu hingga selesai atau batalkan job-nya.",
  ],

  invalid_payroll_run: [
    "Review the income due date, reference and reason.",
    "Periksa tanggal penghasilan terutang, referensi, dan alasan.",
  ],
  invalid_payroll_run_action: [
    "Review the current run and enter a reason.",
    "Periksa proses saat ini dan isi alasan.",
  ],
  payroll_calculation_action_unavailable: [
    "This action is unavailable. Return to the run to review its current status.",
    "Tindakan tidak tersedia. Kembali ke proses untuk memeriksa status saat ini.",
  ],
  payroll_run_not_found: [
    "The calculation run was not found.",
    "Proses perhitungan tidak ditemukan.",
  ],
  payroll_run_result_not_found: [
    "This employee result is unavailable. Return to the run.",
    "Hasil karyawan tidak tersedia. Kembali ke proses perhitungan.",
  ],
  payroll_run_limit: [
    "This period has reached its limit of twenty calculation runs.",
    "Periode ini mencapai batas dua puluh proses perhitungan.",
  ],
  payroll_run_attempt_limit: [
    "This run has reached its recovery limit. Review whether it should be abandoned.",
    "Proses ini mencapai batas pemulihan. Tinjau apakah proses perlu dibatalkan.",
  ],
  payroll_run_author_required: [
    "Only the original preparer can resume this run.",
    "Hanya penyusun awal yang dapat melanjutkan proses ini.",
  ],
  payroll_run_not_resumable: [
    "This run cannot be resumed. Reload its current status.",
    "Proses tidak dapat dilanjutkan. Muat ulang status saat ini.",
  ],
  payroll_job_not_stopped: [
    "The current job must stop before this action.",
    "Job saat ini harus berhenti sebelum tindakan ini dilakukan.",
  ],
  payroll_run_job_obsolete: [
    "The active run or job changed. Reload its current status.",
    "Proses atau job aktif berubah. Muat ulang status saat ini.",
  ],
  payroll_run_already_abandoned: [
    "This run has already been abandoned.",
    "Proses ini telah dibatalkan.",
  ],
  payroll_review_withdrawal_required: [
    "Withdraw the pending or approved review before abandoning this run.",
    "Tarik tinjauan yang menunggu atau telah disetujui sebelum membatalkan proses.",
  ],
  payroll_tax_month_review_required: [
    "The income due date must fall in the earnings month.",
    "Tanggal penghasilan terutang harus berada dalam bulan penghasilan.",
  ],
  payroll_timezone_review_required: [
    "The closed attendance timezone differs from the payroll period. Review the sources.",
    "Zona waktu attendance yang ditutup berbeda dari periode payroll. Periksa sumbernya.",
  ],
  payroll_leave_decision_pending: [
    "A selected employee has a pending leave decision for this month.",
    "Ada keputusan cuti yang belum selesai untuk karyawan terpilih pada bulan ini.",
  ],
  payroll_compensation_required: [
    "Prepare effective compensation before recalculating this employee.",
    "Siapkan kompensasi yang berlaku sebelum menghitung ulang karyawan ini.",
  ],
  payroll_input_required: [
    "Prepare this employee’s monthly payroll input.",
    "Siapkan input payroll bulanan karyawan ini.",
  ],
  payroll_input_verification_required: [
    "An independent reviewer must verify the monthly input.",
    "Reviewer independen harus memverifikasi input bulanan.",
  ],
  payroll_tax_opening_required: [
    "Prepare verified opening tax history for this employee.",
    "Siapkan riwayat pajak awal terverifikasi untuk karyawan ini.",
  ],
  payroll_tax_opening_verification_required: [
    "An independent reviewer must verify the opening tax history.",
    "Reviewer independen harus memverifikasi riwayat pajak awal.",
  ],
  payroll_target_missing: [
    "A retained employee reference is unavailable. Ask an administrator to investigate.",
    "Referensi karyawan tersimpan tidak tersedia. Hubungi administrator untuk pemeriksaan.",
  ],
  payroll_policy_snapshot_missing: [
    "The retained payroll policy is unavailable. Ask an administrator to investigate.",
    "Kebijakan payroll tersimpan tidak tersedia. Hubungi administrator untuk pemeriksaan.",
  ],
  payroll_compensation_snapshot_missing: [
    "The retained compensation revision is unavailable. Ask an administrator to investigate.",
    "Revisi kompensasi tersimpan tidak tersedia. Hubungi administrator untuk pemeriksaan.",
  ],
  payroll_pay_basis_required: [
    "Configure the employee’s proration, overtime and holiday allowance basis.",
    "Atur dasar prorata, lembur, dan THR karyawan.",
  ],

  invalid_payroll_input: [
    "Review the monthly input fields and references.",
    "Periksa isian input bulanan dan referensinya.",
  ],
  payroll_amount_limit: [
    "The combined amount exceeds IDR 50,000,000,000.",
    "Total nominal melebihi IDR 50.000.000.000.",
  ],
  payroll_tax_opening_action_unavailable: [
    "This opening action is unavailable. Reload its current review.",
    "Tindakan riwayat awal tidak tersedia. Muat ulang peninjauannya.",
  ],
  payroll_tax_opening_not_found: [
    "The opening history was not found.",
    "Riwayat pajak awal tidak ditemukan.",
  ],
  payroll_tax_opening_future: [
    "History may only include completed months.",
    "Riwayat hanya boleh mencakup bulan yang sudah selesai.",
  ],
  payroll_tax_opening_already_verified: [
    "This opening has already been verified. Reload its current revision.",
    "Riwayat awal telah diverifikasi. Muat ulang revisi saat ini.",
  ],
  payroll_tax_opening_author_cannot_verify: [
    "An opening preparer cannot verify its history.",
    "Penyusun riwayat awal tidak dapat memverifikasinya.",
  ],
  payroll_tax_opening_revision_limit: [
    "This opening has reached its revision limit.",
    "Riwayat awal ini telah mencapai batas revisi.",
  ],
  invalid_payroll_tax_opening: [
    "Review the opening history values.",
    "Periksa nilai pada riwayat pajak awal.",
  ],
  payroll_input_action_unavailable: [
    "This action is unavailable. Return to the input to review its source and status.",
    "Tindakan tidak tersedia. Kembali ke input untuk memeriksa sumber dan statusnya.",
  ],
  payroll_input_not_found: ["The monthly input was not found.", "Input bulanan tidak ditemukan."],
  self_payroll_input_change_denied: [
    "Your input must be prepared by another authorized operator.",
    "Input Anda harus disiapkan operator lain yang berwenang.",
  ],
  self_payroll_input_review_denied: [
    "Your input must be verified by another authorized reviewer.",
    "Input Anda harus diverifikasi reviewer lain yang berwenang.",
  ],
  payroll_input_author_cannot_verify: [
    "An independent reviewer must verify this input.",
    "Input harus diverifikasi reviewer yang tidak menyiapkannya.",
  ],
  payroll_input_already_verified: [
    "This input has already been verified. Reload its current status.",
    "Input sudah diverifikasi. Muat ulang statusnya.",
  ],
  payroll_input_revision_limit: [
    "This input has reached its revision limit.",
    "Input telah mencapai batas revisi.",
  ],
  payroll_workforce_not_closed: [
    "Attendance must be closed before preparing payroll inputs or starting a calculation.",
    "Attendance harus ditutup sebelum menyiapkan input payroll atau memulai perhitungan.",
  ],
  payroll_workforce_employee_missing: [
    "This employee is absent from the closed attendance source.",
    "Karyawan tidak tercakup dalam sumber attendance yang ditutup.",
  ],
  stale_workforce_version: [
    "The attendance source has changed. Reload the review.",
    "Sumber attendance berubah. Muat ulang tinjauan.",
  ],

  invalid_compensation: [
    "Review the compensation fields and verification references.",
    "Periksa isian kompensasi dan referensi verifikasinya.",
  ],
  invalid_compensation_amount: [
    "Total earnings exceed the supported amount, or deductions exceed earnings.",
    "Total penghasilan melebihi batas, atau potongan melebihi penghasilan.",
  ],
  invalid_payroll_pay_basis: [
    "Review the proration, overtime and THR calculation basis.",
    "Periksa dasar perhitungan prorata, lembur, dan THR.",
  ],
  compensation_change_unavailable: [
    "Compensation cannot be changed with this account. Return to its details.",
    "Kompensasi tidak dapat diubah dengan akun ini. Kembali ke detailnya.",
  ],
  self_compensation_change_denied: [
    "Your compensation must be changed by another authorized operator.",
    "Kompensasi Anda harus diubah oleh operator lain yang berwenang.",
  ],
  compensation_revision_limit: [
    "This compensation has reached its revision limit.",
    "Kompensasi ini mencapai batas revisi.",
  ],
  invalid_payroll_policy: [
    "Review the policy dates, contribution bases and references.",
    "Periksa periode kebijakan, dasar iuran, dan referensinya.",
  ],
  invalid_payroll_month: [
    "Select a payroll month from 2024 through 2100.",
    "Pilih bulan payroll antara tahun 2024 dan 2100.",
  ],
  payroll_policy_not_found: [
    "No payroll policy has been configured for this period.",
    "Kebijakan payroll belum diatur untuk periode ini.",
  ],
  payroll_policy_not_effective: [
    "The saved policy does not cover this month.",
    "Kebijakan tersimpan tidak berlaku pada bulan ini.",
  ],
  payroll_policy_revision_limit: [
    "The policy has reached its revision limit.",
    "Kebijakan mencapai batas revisi.",
  ],
  approval_group_too_large: [
    "The approval group exceeds the supported size. Ask an administrator to review the policy.",
    "Grup persetujuan melebihi batas. Minta administrator memeriksa kebijakannya.",
  ],
  approval_exclusion_capacity: [
    "This request has too many contributors for approval. Ask an administrator to review it.",
    "Kontributor permintaan ini melebihi batas persetujuan. Minta administrator memeriksanya.",
  ],
  expense_claim_action_unavailable: [
    "This action is unavailable for the current claim. Return to its details.",
    "Tindakan tidak tersedia untuk klaim saat ini. Kembali ke detailnya.",
  ],
  invalid_expense_action: [
    "Review the claim and enter a reason of up to 1,000 characters.",
    "Periksa klaim dan isi alasan maksimal 1.000 karakter.",
  ],
  expense_lines_required: [
    "Add at least one expense item before submitting.",
    "Tambahkan minimal satu item expense sebelum mengajukan.",
  ],
  expense_transaction_date_invalid: [
    "Expense dates must be within the past 366 days and cannot be in the future.",
    "Tanggal expense harus berada dalam 366 hari terakhir dan tidak boleh di masa depan.",
  ],
  expense_employment_ineligible: [
    "The employee is ineligible for a selected expense category or date.",
    "Karyawan tidak memenuhi ketentuan kategori atau tanggal expense yang dipilih.",
  ],
  expense_transaction_expired: [
    "An expense item exceeds its category's submission period.",
    "Item expense melewati batas waktu pengajuan kategorinya.",
  ],
  expense_line_limit_exceeded: [
    "An item exceeds its category's amount limit.",
    "Jumlah item melebihi batas kategori.",
  ],
  expense_category_limit_exceeded: [
    "The claim total for a category exceeds its limit.",
    "Total klaim untuk suatu kategori melebihi batas.",
  ],
  expense_cost_center_required: [
    "Add the cost center required by the selected expense category.",
    "Tambahkan cost center yang diwajibkan kategori expense.",
  ],
  expense_receipt_required: [
    "Add the receipt required by the selected expense category.",
    "Tambahkan receipt yang diwajibkan kategori expense.",
  ],
  expense_receipt_not_ready: [
    "A selected receipt is not ready. Review its validation status before submitting.",
    "Receipt yang dipilih belum siap. Periksa status validasinya sebelum mengajukan.",
  ],
  expense_submission_limit: [
    "This claim has reached its submission limit.",
    "Klaim ini mencapai batas pengajuan.",
  ],
  expense_approval_requires_split: [
    "These categories require different approval routes. Separate them into different claims.",
    "Kategori ini memerlukan alur persetujuan berbeda. Pisahkan ke klaim yang berbeda.",
  ],
  invalid_expense_draft: [
    "Review the claim title, items and change note.",
    "Periksa judul klaim, item, dan catatan perubahan.",
  ],
  invalid_expense_line: [
    "Check the item date, category, amount, description and receipts.",
    "Periksa tanggal, kategori, jumlah, deskripsi, dan receipt item.",
  ],
  duplicate_expense_line: [
    "Each expense item must be distinct.",
    "Setiap item expense harus berbeda.",
  ],
  expense_claim_amount_limit: [
    "The claim total exceeds the supported amount.",
    "Total klaim melebihi batas jumlah yang didukung.",
  ],
  expense_access_denied: [
    "You no longer have access to this expense claim.",
    "Anda tidak lagi memiliki akses ke klaim expense ini.",
  ],
  expense_identity_immutable: [
    "The employee on an existing claim cannot be changed.",
    "Karyawan pada klaim yang sudah dibuat tidak dapat diubah.",
  ],
  expense_not_editable: [
    "This claim can no longer be edited. Reload its details.",
    "Klaim ini tidak dapat diedit lagi. Muat ulang detailnya.",
  ],
  expense_draft_revision_limit: [
    "This claim has reached its draft revision limit.",
    "Klaim ini mencapai batas revisi draft.",
  ],
  expense_draft_capacity: [
    "Resolve outstanding drafts before creating another claim.",
    "Selesaikan draft tertunda sebelum membuat klaim lain.",
  ],
  expense_category_unavailable: [
    "A selected category is unavailable. Review the expense items.",
    "Kategori yang dipilih tidak tersedia. Periksa item expense.",
  ],
  expense_cost_center_unavailable: [
    "A selected cost center is unavailable. Review the expense items.",
    "Cost center yang dipilih tidak tersedia. Periksa item expense.",
  ],
  invalid_payroll_payment_batch: [
    "Review the title, note and selected payslips before preparing the batch.",
    "Periksa judul, catatan, dan slip gaji yang dipilih sebelum menyiapkan batch.",
  ],
  invalid_payroll_payment_destination: [
    "Enter a valid bank code, account number and account name.",
    "Isi kode bank, nomor rekening, dan nama rekening yang valid.",
  ],
  payroll_payment_preparation_unavailable: [
    "This payslip cannot be prepared by the current operator. Refresh the payable payslips.",
    "Slip gaji tidak dapat disiapkan oleh operator saat ini. Muat ulang slip gaji siap bayar.",
  ],
  payroll_payment_selection_limit: [
    "A batch can contain up to 100 payslips.",
    "Satu batch dapat berisi maksimal 100 slip gaji.",
  ],
  payroll_payment_duplicate_assessment: [
    "This payslip is already selected.",
    "Slip gaji sudah dipilih.",
  ],
  payroll_assessment_not_payable: [
    "A selected payslip is no longer payable. Refresh the payable payslips and review your selection.",
    "Slip gaji yang dipilih tidak lagi siap bayar. Muat ulang slip gaji siap bayar dan periksa pilihan.",
  ],
  payroll_payment_batch_exists: [
    "This batch already exists. Return to the payment list and review its details.",
    "Batch sudah ada. Kembali ke daftar pembayaran dan periksa detailnya.",
  ],
  payroll_payment_capacity: [
    "The company payment batch limit has been reached. Resolve outstanding batches before preparing another.",
    "Batas batch pembayaran perusahaan telah tercapai. Selesaikan batch tertunda sebelum menyiapkan batch lain.",
  ],
  payroll_assessment_payment_reserved: [
    "A selected payslip is reserved by another batch. Remove it and review the remaining instructions.",
    "Slip gaji yang dipilih sudah digunakan batch lain. Hapus slip gaji tersebut dan periksa instruksi lainnya.",
  ],
  payroll_payment_attempt_limit: [
    "A selected payslip has reached its payment attempt limit. Contact finance before preparing another batch.",
    "Slip gaji yang dipilih mencapai batas percobaan pembayaran. Hubungi finance sebelum menyiapkan batch lain.",
  ],
  payroll_reconciliation_unavailable: [
    "Reconciliation is unavailable. Return to the batch details.",
    "Rekonsiliasi tidak tersedia. Kembali ke detail batch.",
  ],
  invalid_payroll_payment_results: [
    "Enter between one and 100 distinct payment results.",
    "Isi satu hingga 100 hasil pembayaran yang berbeda.",
  ],
  invalid_payroll_payment_result: [
    "Review the bank result and note. The confirmation time must fall between batch release and now.",
    "Periksa hasil bank dan catatan. Waktu konfirmasi harus berada antara waktu rilis batch dan saat ini.",
  ],
  payroll_payment_not_released: [
    "This batch is no longer awaiting payment results. Reload its details.",
    "Batch tidak lagi menunggu hasil pembayaran. Muat ulang detailnya.",
  ],
  payroll_payment_item_not_pending: [
    "A selected payment already has a result. Reload the batch review.",
    "Pembayaran yang dipilih sudah memiliki hasil. Muat ulang tinjauan batch.",
  ],
  payroll_payment_action_unavailable: [
    "This action is unavailable. Return to the batch details.",
    "Tindakan tidak tersedia. Kembali ke detail batch.",
  ],
  invalid_payroll_payment_action: [
    "Enter a reason of up to 1,000 characters and review the payment batch.",
    "Isi alasan maksimal 1.000 karakter dan periksa batch pembayaran.",
  ],
  payroll_payment_not_prepared: [
    "This batch is no longer prepared. Reload its details.",
    "Batch tidak lagi dalam tahap persiapan. Muat ulang detailnya.",
  ],
  payroll_payment_evidence_unavailable: [
    "Payment evidence has changed. Reload the batch review.",
    "Bukti pembayaran telah berubah. Muat ulang tinjauan batch.",
  ],
  payroll_payment_batch_not_found: [
    "This payment batch is unavailable. Return to the payment list.",
    "Batch pembayaran tidak tersedia. Kembali ke daftar pembayaran.",
  ],
  payroll_payment_instructions_unavailable: [
    "This worksheet is no longer available. Reload the batch to check its payment results.",
    "Instruksi pembayaran tidak lagi tersedia. Muat ulang batch untuk memeriksa hasil pembayaran.",
  ],
  invalid_payroll_payment_page: [
    "Select a valid date range of up to 366 days.",
    "Pilih rentang tanggal yang valid, maksimal 366 hari.",
  ],
  invalid_expense_payment_batch: [
    "Review the title, note and selected claims before preparing the batch.",
    "Periksa judul, catatan, dan klaim yang dipilih sebelum menyiapkan batch.",
  ],
  invalid_expense_payment_destination: [
    "Enter a valid bank code, account number and account name.",
    "Isi kode bank, nomor rekening, dan nama rekening yang valid.",
  ],
  expense_payment_preparation_unavailable: [
    "This claim cannot be prepared by the current operator. Refresh the payable claims.",
    "Klaim tidak dapat disiapkan oleh operator saat ini. Muat ulang klaim siap bayar.",
  ],
  expense_payment_selection_limit: [
    "A batch can contain up to 100 claims.",
    "Satu batch dapat berisi maksimal 100 klaim.",
  ],
  expense_payment_duplicate_claim: ["This claim is already selected.", "Klaim sudah dipilih."],
  expense_payment_requires_approved_claims: [
    "A selected claim is no longer payable. Refresh the payable claims and review your selection.",
    "Klaim yang dipilih tidak lagi siap bayar. Muat ulang klaim siap bayar dan periksa pilihan.",
  ],
  expense_payment_batch_exists: [
    "This batch already exists. Return to the payment list and review its details.",
    "Batch sudah ada. Kembali ke daftar pembayaran dan periksa detailnya.",
  ],
  expense_payment_capacity: [
    "The company payment batch limit has been reached. Resolve outstanding batches before preparing another.",
    "Batas batch pembayaran perusahaan telah tercapai. Selesaikan batch tertunda sebelum menyiapkan batch lain.",
  ],
  expense_claim_payment_reserved: [
    "A selected claim is reserved by another batch. Remove it and review the remaining instructions.",
    "Klaim yang dipilih sudah digunakan batch lain. Hapus klaim tersebut dan periksa instruksi lainnya.",
  ],
  expense_payment_attempt_limit: [
    "A selected claim has reached its payment attempt limit. Contact finance before preparing another batch.",
    "Klaim yang dipilih mencapai batas percobaan pembayaran. Hubungi finance sebelum menyiapkan batch lain.",
  ],
  expense_reconciliation_unavailable: [
    "Reconciliation is unavailable. Return to the batch details.",
    "Rekonsiliasi tidak tersedia. Kembali ke detail batch.",
  ],
  invalid_expense_payment_results: [
    "Enter between one and 100 distinct payment results.",
    "Isi satu hingga 100 hasil pembayaran yang berbeda.",
  ],
  invalid_expense_payment_result: [
    "Review the bank result and note. The confirmation time must fall between batch release and now.",
    "Periksa hasil bank dan catatan. Waktu konfirmasi harus berada antara waktu rilis batch dan saat ini.",
  ],
  duplicate_payment_transaction_reference: [
    "Each successful payment requires a distinct bank transaction reference.",
    "Setiap pembayaran berhasil memerlukan referensi transaksi bank yang berbeda.",
  ],
  expense_payment_not_released: [
    "This batch is no longer awaiting payment results. Reload its details.",
    "Batch tidak lagi menunggu hasil pembayaran. Muat ulang detailnya.",
  ],
  expense_payment_item_not_pending: [
    "A selected payment already has a result. Reload the batch review.",
    "Pembayaran yang dipilih sudah memiliki hasil. Muat ulang tinjauan batch.",
  ],
  expense_payment_action_unavailable: [
    "This action is unavailable. Return to the batch details.",
    "Tindakan tidak tersedia. Kembali ke detail batch.",
  ],
  invalid_expense_payment_action: [
    "Enter a reason of up to 1,000 characters and review the payment batch.",
    "Isi alasan maksimal 1.000 karakter dan periksa batch pembayaran.",
  ],
  expense_payment_not_prepared: [
    "This batch is no longer prepared. Reload its details.",
    "Batch tidak lagi dalam tahap persiapan. Muat ulang detailnya.",
  ],
  expense_payment_evidence_unavailable: [
    "Payment evidence has changed. Reload the batch review.",
    "Bukti pembayaran telah berubah. Muat ulang tinjauan batch.",
  ],
  independent_payment_release_required: [
    "Another authorized finance operator must release this batch.",
    "Operator finance lain yang berwenang harus merilis batch ini.",
  ],
  independent_expense_payment_required: [
    "An independent finance operator must handle this payment.",
    "Pembayaran harus ditangani operator finance yang independen.",
  ],
  expense_payment_batch_not_found: [
    "This payment batch is unavailable. Return to the payment list.",
    "Batch pembayaran tidak tersedia. Kembali ke daftar pembayaran.",
  ],
  expense_payment_instructions_unavailable: [
    "This worksheet is no longer available. Reload the batch to check its payment results.",
    "Instruksi pembayaran tidak lagi tersedia. Muat ulang batch untuk memeriksa hasil pembayaran.",
  ],
  invalid_expense_decision: [
    "Review the decision and enter a reason of up to 1,000 characters.",
    "Periksa keputusan dan isi alasan maksimal 1.000 karakter.",
  ],
  expense_decision_unavailable: [
    "You cannot decide this submission at its current stage. Return to its details.",
    "Anda tidak dapat memberi keputusan pada tahap ini. Kembali ke detail pengajuan.",
  ],
  expense_submission_not_current: [
    "This submission is no longer pending. Reload its details.",
    "Pengajuan ini tidak lagi menunggu keputusan. Muat ulang detailnya.",
  ],
  expense_duplicate_acknowledgement_required: [
    "Review the current duplicate receipt signals before approving.",
    "Periksa indikasi duplikat bukti terbaru sebelum menyetujui.",
  ],
  invalid_expense_page: [
    "Choose valid dates within 366 days and review the filters.",
    "Pilih tanggal dengan rentang maksimal 366 hari dan periksa filter.",
  ],
  expense_claim_not_found: [
    "This expense claim is unavailable. Return to the claim list.",
    "Klaim tidak tersedia. Kembali ke daftar klaim.",
  ],
  expense_submission_not_found: [
    "This submission is unavailable or your access has changed.",
    "Pengajuan tidak tersedia atau akses Anda telah berubah.",
  ],
  expense_receipt_not_found: [
    "This receipt is unavailable. Reload the submission to check current access.",
    "Bukti tidak tersedia. Muat ulang pengajuan untuk memeriksa akses terbaru.",
  ],
  expense_receipt_unavailable: [
    "This receipt is unavailable. Review the selected evidence.",
    "Receipt tidak tersedia. Periksa bukti yang dipilih.",
  ],
  invalid_expense_policy: [
    "Review the category requirements and amount limits.",
    "Periksa persyaratan kategori dan batas nominal.",
  ],
  invalid_expense_category: [
    "Review the category requirements and amount limits.",
    "Periksa persyaratan kategori dan batas nominal.",
  ],
  expense_category_not_found: [
    "This expense category is unavailable. Return to the policy list.",
    "Kategori biaya tidak tersedia. Kembali ke daftar kebijakan.",
  ],
  expense_category_code_immutable: [
    "The category code cannot be changed. Reload the current policy.",
    "Kode kategori tidak dapat diubah. Muat ulang kebijakan terbaru.",
  ],
  expense_category_limit: [
    "This company has reached its limit of 500 expense categories.",
    "Perusahaan telah mencapai batas 500 kategori biaya.",
  ],
  expense_policy_revision_limit: [
    "This category has reached its revision limit. Contact an administrator.",
    "Kategori telah mencapai batas revisi. Hubungi administrator.",
  ],
  invalid_document_retirement: [
    "Review the selected revision and provide a reason of up to 1,000 characters without line breaks.",
    "Periksa revisi yang dipilih dan isi alasan maksimal 1.000 karakter tanpa baris baru.",
  ],
  document_retirement_unavailable: [
    "This revision cannot be retired. Reload its retention review.",
    "Revisi belum dapat dihentikan. Muat ulang tinjauan retensinya.",
  ],
  document_revision_not_ready: [
    "Only an accepted revision can be retired. Review its processing status.",
    "Hanya revisi yang telah diterima yang dapat dihentikan. Periksa status pemrosesannya.",
  ],
  document_legal_hold: [
    "An active legal hold protects this document from retirement.",
    "Legal hold yang aktif melindungi dokumen dari penghentian.",
  ],
  document_retention_indefinite: [
    "This archive has an indefinite retention period.",
    "Arsip ini memiliki masa simpan tanpa batas waktu.",
  ],
  document_retention_pending: [
    "The archive's retention period has not ended.",
    "Masa simpan arsip belum berakhir.",
  ],
  document_evidence_referenced: [
    "A business record references this revision. Its content must be retained.",
    "Revisi digunakan sebagai bukti dalam catatan bisnis. Kontennya harus tetap disimpan.",
  ],
  invalid_document_retention_change: [
    "Review the selected document and provide a reason of up to 1,000 characters without line breaks.",
    "Periksa dokumen yang dipilih dan isi alasan maksimal 1.000 karakter tanpa baris baru.",
  ],
  document_retention_action_unavailable: [
    "This retention action is no longer available. Reload the document's retention status.",
    "Tindakan retensi ini sudah tidak tersedia. Muat ulang status retensi dokumen.",
  ],
  document_retention_state_changed: [
    "Document retention changed during this review. Refresh to view its current status and history.",
    "Retensi dokumen berubah saat ditinjau. Muat ulang untuk melihat status dan riwayat terbaru.",
  ],
  document_already_archived: [
    "This document is already archived. Reload its retention status.",
    "Dokumen sudah diarsipkan. Muat ulang status retensinya.",
  ],
  document_not_archived: [
    "This document is not archived. Reload its retention status.",
    "Dokumen belum diarsipkan. Muat ulang status retensinya.",
  ],
  document_already_held: [
    "This document already has a legal hold. Reload its retention status.",
    "Dokumen sudah memiliki legal hold. Muat ulang status retensinya.",
  ],
  document_not_held: [
    "This document has no legal hold. Reload its retention status.",
    "Dokumen tidak memiliki legal hold. Muat ulang status retensinya.",
  ],
  invalid_document_retention_policy: [
    "Use 1–36,500 retention days or leave the field blank. Provide a reason of up to 1,000 characters without line breaks.",
    "Isi 1–36.500 hari atau kosongkan masa simpan. Masukkan alasan maksimal 1.000 karakter tanpa baris baru.",
  ],
  document_retention_policy_not_found: [
    "This retention policy is unavailable. Return to the policy list.",
    "Kebijakan retensi tidak tersedia. Kembali ke daftar kebijakan.",
  ],
  document_retention_policy_exists: [
    "This classification already has a policy. Reload to review its current version.",
    "Klasifikasi ini sudah memiliki kebijakan. Muat ulang untuk memeriksa versi terbarunya.",
  ],
  document_retention_classification_immutable: [
    "The policy classification cannot be changed. Reload the selected policy.",
    "Klasifikasi kebijakan tidak dapat diubah. Muat ulang kebijakan yang dipilih.",
  ],
  document_retention_history_limit: [
    "The retention history limit has been reached. Contact an administrator.",
    "Batas riwayat retensi tercapai. Hubungi administrator.",
  ],
  document_retention_review_changed: [
    "The policy changed during this review. Refresh to view the current policy and history.",
    "Kebijakan berubah saat ditinjau. Muat ulang untuk melihat kebijakan dan riwayat terbaru.",
  ],
  object_storage_not_configured: [
    "Document storage has not been configured. Contact an administrator.",
    "Penyimpanan dokumen belum dikonfigurasi. Hubungi administrator.",
  ],
  object_storage_unavailable: [
    "Document storage is unavailable. Try again later.",
    "Penyimpanan dokumen belum tersedia. Coba lagi nanti.",
  ],
  object_storage_busy: [
    "Document storage is busy. Wait briefly, then retry.",
    "Penyimpanan dokumen sedang sibuk. Tunggu sebentar, lalu coba lagi.",
  ],
  object_storage_failure: [
    "The file could not be stored. Retry the same upload.",
    "File belum dapat disimpan. Coba lagi upload yang sama.",
  ],
  document_file_required: [
    "Select a file before starting the upload.",
    "Pilih file sebelum memulai upload.",
  ],
  invalid_document_file: [
    "Select a PDF, JPG or PNG of up to 100 MiB with a valid file name.",
    "Pilih PDF, JPG, atau PNG maksimal 100 MiB dengan nama file yang valid.",
  ],
  document_file_mismatch: [
    "Select the same file used to start this upload. Its contents must match.",
    "Pilih file yang digunakan untuk memulai upload ini. Isi file harus sama.",
  ],
  invalid_document_upload: [
    "Enter a title of up to 160 characters, choose a category and provide a reason of up to 1,000 characters.",
    "Isi judul maksimal 160 karakter, pilih kategori, dan masukkan alasan maksimal 1.000 karakter.",
  ],
  document_upload_active: [
    "Another revision is still being processed. Complete or cancel it before continuing.",
    "Revisi lain masih diproses. Selesaikan atau batalkan sebelum melanjutkan.",
  ],
  document_upload_capacity: [
    "The upload limit has been reached. Complete or cancel an unfinished upload first.",
    "Batas upload tercapai. Selesaikan atau batalkan upload yang belum selesai.",
  ],
  document_revision_limit: [
    "This document has reached its revision limit.",
    "Dokumen sudah mencapai batas jumlah revisi.",
  ],
  document_storage_quota: [
    "The company document storage quota has been reached.",
    "Kuota penyimpanan dokumen perusahaan sudah penuh.",
  ],
  document_identity_mismatch: [
    "The document details changed. Reload before uploading a revision.",
    "Detail dokumen berubah. Muat ulang sebelum meng-upload revisi.",
  ],
  document_chunk_in_progress: [
    "The previous upload request is still active. Wait briefly, then retry.",
    "Request upload sebelumnya masih aktif. Tunggu sebentar, lalu coba lagi.",
  ],
  document_chunk_reserved: [
    "A file part is reserved by another request. Retry to check the current upload.",
    "Bagian file sedang ditangani request lain. Coba lagi untuk memeriksa upload terbaru.",
  ],
  document_chunk_attempts_exhausted: [
    "The upload attempt limit has been reached. Cancel this revision before replacing it.",
    "Batas percobaan upload tercapai. Batalkan revisi ini sebelum menggantinya.",
  ],
  document_offset_mismatch: [
    "Upload progress changed. Retry to continue from the confirmed position.",
    "Progres upload berubah. Coba lagi untuk melanjutkan dari posisi yang sudah diterima.",
  ],
  document_chunk_digest_mismatch: [
    "The file part could not be verified. Retry with the same file.",
    "Bagian file tidak dapat diverifikasi. Coba lagi dengan file yang sama.",
  ],
  storage_unavailable: [
    "Document storage is unavailable. Retry when the service is available.",
    "Penyimpanan dokumen belum tersedia. Coba lagi saat layanan tersedia.",
  ],
  file_capacity_exceeded: [
    "Close another upload before selecting a new file.",
    "Tutup upload lain sebelum memilih file baru.",
  ],
  file_in_use: [
    "A file read is already running. Wait for it to finish.",
    "Pembacaan file masih berjalan. Tunggu hingga selesai.",
  ],
  document_action_unavailable: [
    "This action is no longer available. Reload to check the revision.",
    "Tindakan ini sudah tidak tersedia. Muat ulang untuk memeriksa revisi.",
  ],
  invalid_document_validation: [
    "Enter a reason of up to 1,000 characters and review the selected revision.",
    "Isi alasan maksimal 1.000 karakter dan periksa revisi yang dipilih.",
  ],
  invalid_document_cancellation: [
    "Enter a reason of up to 1,000 characters and review the selected upload.",
    "Isi alasan maksimal 1.000 karakter dan periksa upload yang dipilih.",
  ],
  document_upload_closed: [
    "This upload is closed. Reload to check its current status.",
    "Upload sudah ditutup. Muat ulang untuk memeriksa status terbaru.",
  ],
  document_upload_incomplete: [
    "The file has not finished uploading.",
    "File belum selesai di-upload.",
  ],
  document_validation_active: [
    "Validation is already in progress.",
    "Validasi sedang berlangsung.",
  ],
  document_validation_attempts_exhausted: [
    "The validation attempt limit has been reached. Cancel this upload before replacing it.",
    "Batas percobaan validasi tercapai. Batalkan upload ini sebelum menggantinya.",
  ],
  document_content_expired: [
    "The upload deadline has passed. Cancel this upload before replacing it.",
    "Batas waktu upload telah lewat. Batalkan upload ini sebelum menggantinya.",
  ],
  document_archived: [
    "This document is archived and cannot receive new content.",
    "Dokumen sudah diarsipkan dan tidak dapat menerima konten baru.",
  ],
  document_not_found: [
    "This document is unavailable in the current company.",
    "Dokumen tidak tersedia di perusahaan ini.",
  ],
  document_revision_not_found: [
    "This revision is unavailable for the selected document.",
    "Revisi tidak tersedia untuk dokumen ini.",
  ],
  document_access_denied: [
    "You no longer have access to this document.",
    "Anda tidak lagi memiliki akses ke dokumen ini.",
  ],
  document_not_ready: [
    "This revision is not available for download. Refresh to check its current status.",
    "Revisi belum tersedia untuk di-download. Muat ulang untuk memeriksa status terbaru.",
  ],
  invalid_document_page: [
    "This page reference is invalid. Return to the first page.",
    "Referensi halaman tidak valid. Kembali ke halaman pertama.",
  ],
  invalid_schedule_change: [
    "Review the effective date, selected shifts and reason.",
    "Periksa tanggal berlaku, pilihan shift, dan alasan.",
  ],
  invalid_roster_change: [
    "Review the work date, selected shift and reason.",
    "Periksa tanggal kerja, pilihan shift, dan alasan.",
  ],
  shift_changed: [
    "A selected shift has changed. Reload and select its current revision.",
    "Shift yang dipilih telah berubah. Muat ulang dan pilih revisi terbarunya.",
  ],
  shift_unavailable: [
    "A selected shift is no longer active in this company.",
    "Shift yang dipilih sudah tidak aktif di perusahaan ini.",
  ],
  employee_unavailable: [
    "The employee is not eligible for this action on the selected date.",
    "Karyawan tidak memenuhi syarat untuk tindakan ini pada tanggal yang dipilih.",
  ],
  holiday_not_found: [
    "This holiday is unavailable in the current company.",
    "Hari libur tidak tersedia di perusahaan ini.",
  ],
  invalid_holiday: [
    "Enter a valid date, a name of up to 200 characters and a reason of up to 1,000 characters.",
    "Isi tanggal yang valid, nama maksimal 200 karakter, dan alasan maksimal 1.000 karakter.",
  ],
  invalid_calendar_range: [
    "Review the date range and its allowed length. The end must not precede the start.",
    "Periksa rentang tanggal dan batas durasinya. Tanggal akhir tidak boleh sebelum tanggal awal.",
  ],
  work_period_locked: [
    "This attendance period is locked. Review its closing status.",
    "Periode presensi terkunci. Periksa status penutupannya.",
  ],
  invalid_work_period_range: [
    "Select a valid month between January 2000 and December 2100.",
    "Pilih bulan yang valid antara Januari 2000 dan Desember 2100.",
  ],
  invalid_work_period_close: [
    "Review the period and enter a reason of up to 1,000 characters.",
    "Periksa periode dan isi alasan maksimal 1.000 karakter.",
  ],
  invalid_work_period_recovery: [
    "Review the stopped attempt and enter a reason of up to 1,000 characters.",
    "Periksa proses yang berhenti dan isi alasan maksimal 1.000 karakter.",
  ],
  work_period_not_ended: [
    "Only a completed month in the company timezone can be closed.",
    "Penutupan hanya tersedia untuk bulan yang sudah berakhir di zona waktu perusahaan.",
  ],
  work_period_recovery_unavailable: [
    "This period has no stopped attempt that needs recovery. Refresh its status.",
    "Tidak ada proses berhenti yang perlu dipulihkan pada periode ini. Muat ulang statusnya.",
  ],
  work_period_snapshot_unavailable: [
    "Published attendance results are unavailable for this employee and month.",
    "Hasil presensi yang sudah ditutup belum tersedia untuk karyawan dan bulan ini.",
  ],
  work_period_employee_limit: [
    "A closing attempt supports up to 5,000 employees.",
    "Satu proses penutupan mendukung maksimal 5.000 karyawan.",
  ],
  work_period_snapshot_incomplete: [
    "The attendance results are incomplete. Review the closing attempt.",
    "Hasil presensi belum lengkap. Periksa proses penutupannya.",
  ],
  overtime_resolution_required: [
    "Resolve outstanding overtime plans and reviews before closing this month.",
    "Selesaikan pengajuan dan review lembur sebelum menutup bulan ini.",
  ],
  attendance_incomplete: [
    "Complete the attendance clock-in and clock-out pairs before closing this month.",
    "Lengkapi pasangan presensi masuk dan pulang sebelum menutup bulan ini.",
  ],
  shift_not_found: [
    "This shift is unavailable in the current company.",
    "Shift tidak tersedia di perusahaan ini.",
  ],
  invalid_shift: [
    "Review the shift code, name, work mode and timezone.",
    "Periksa kode, nama, mode kerja, dan zona waktu shift.",
  ],
  invalid_shift_change: [
    "Review the shift version and enter a reason of up to 1,000 characters.",
    "Periksa versi shift dan isi alasan maksimal 1.000 karakter.",
  ],
  invalid_shift_duration: [
    "Use whole-minute times and a break shorter than the shift. Start and end times must differ.",
    "Gunakan jam dalam menit penuh dan durasi istirahat lebih pendek dari shift. Jam mulai dan selesai harus berbeda.",
  ],
  invalid_location_accuracy: [
    "GPS accuracy must be between 1 and 10,000 metres.",
    "Akurasi GPS harus antara 1 dan 10.000 meter.",
  ],
  invalid_geofence: [
    "Review the coordinates and area radius. An area restriction requires location capture.",
    "Periksa koordinat dan radius area. Pembatasan area memerlukan lokasi wajib.",
  ],

  invalid_announcement_command: ["Review the publication fields.", "Periksa isian publikasi."],
  invalid_announcement_schedule: [
    "Choose a publication time within the next 365 days.",
    "Pilih waktu publikasi dalam 365 hari ke depan.",
  ],
  announcement_action_unavailable: [
    "This action is unavailable in the current publication state.",
    "Tindakan ini tidak tersedia pada kondisi publikasi saat ini.",
  ],
  announcement_publication_unavailable: [
    "Publication status could not be loaded.",
    "Status publikasi belum dapat dimuat.",
  ],
  announcement_preview_unavailable: [
    "Audience preview is only available for draft or queued announcements.",
    "Pratinjau penerima tersedia untuk draft atau pengumuman dalam antrean.",
  ],
  announcement_archived: ["This announcement is archived.", "Pengumuman ini sudah diarsipkan."],
  announcement_attempt_limit: [
    "This announcement has reached its publication attempt limit.",
    "Pengumuman ini telah mencapai batas percobaan publikasi.",
  ],
  announcement_publication_active: [
    "Stop the publication job before archiving.",
    "Hentikan job publikasi sebelum mengarsipkan.",
  ],
  announcement_publication_not_stopped: [
    "The publication job must finish cancellation or fail before returning to draft.",
    "Job publikasi harus selesai dibatalkan atau gagal sebelum dikembalikan ke draft.",
  ],
  announcement_audience_empty: [
    "No eligible recipients were available at publication.",
    "Tidak ada penerima yang memenuhi syarat saat publikasi.",
  ],
  announcement_audience_limit: [
    "The audience exceeds the publication limit.",
    "Jumlah penerima melebihi batas publikasi.",
  ],
  invalid_announcement: ["Review the announcement fields.", "Periksa isian pengumuman."],
  invalid_audience_group: ["Review the audience group fields.", "Periksa isian grup penerima."],
  audience_group_not_found: ["The audience group is unavailable.", "Grup penerima tidak tersedia."],
  audience_group_employee_unavailable: [
    "A selected employee is unavailable in this company.",
    "Karyawan yang dipilih tidak tersedia di perusahaan ini.",
  ],
  audience_group_limit: [
    "This company has reached its audience group limit.",
    "Jumlah grup penerima perusahaan telah mencapai batas.",
  ],
  audience_group_revision_limit: [
    "This group has reached its revision limit.",
    "Grup ini telah mencapai batas revisi.",
  ],
  invalid_audience_reference_search: [
    "Review the search or selected references.",
    "Periksa pencarian atau referensi terpilih.",
  ],
  announcement_audience_unavailable: [
    "One or more audience references are unavailable. Review the selection.",
    "Sebagian referensi penerima tidak tersedia. Periksa pilihan.",
  ],
  announcement_not_draft: [
    "Only a draft announcement can be edited.",
    "Hanya draft pengumuman yang dapat diedit.",
  ],
  announcement_revision_limit: [
    "This announcement has reached its revision limit.",
    "Pengumuman ini telah mencapai batas revisi.",
  ],
  announcement_not_found: [
    "This announcement is unavailable in the current company.",
    "Pengumuman tidak tersedia di perusahaan ini.",
  ],
  person_account_link_access_required: [
    "Your account cannot link employee accounts.",
    "Akun Anda tidak dapat menghubungkan akun karyawan.",
  ],
  independent_account_binding_required: [
    "Another administrator must link your account.",
    "Akun Anda harus dihubungkan oleh administrator lain.",
  ],
  person_account_already_bound: [
    "This employee already has a linked account. Reload the profile.",
    "Karyawan ini sudah memiliki akun terhubung. Muat ulang profil.",
  ],
  invalid_account_binding: [
    "Choose an account and enter a reason of up to 1,000 characters.",
    "Pilih akun dan isi alasan maksimal 1.000 karakter.",
  ],
  account_membership_required: [
    "The selected account needs active company access. Reload the review.",
    "Akun terpilih memerlukan akses perusahaan yang aktif. Muat ulang review.",
  ],
  company_member_not_found: [
    "This company account is no longer available. Refresh the directory.",
    "Akun perusahaan ini tidak lagi tersedia. Muat ulang daftar akun.",
  ],
  leave_adjustment_unavailable: [
    "This balance cannot be adjusted. Check your access, employee and balance year.",
    "Saldo ini tidak dapat disesuaikan. Periksa akses, karyawan, dan tahun saldo.",
  ],
  invalid_leave_adjustment: [
    "Check the adjustment amount and reason.",
    "Periksa jumlah penyesuaian dan alasannya.",
  ],
  invalid_leave_days: [
    "Enter a valid number of whole or half-days.",
    "Masukkan jumlah hari atau setengah hari yang valid.",
  ],
  stale_balance_version: [
    "The balance changed. Reload the review before recording this adjustment.",
    "Saldo berubah. Muat ulang review sebelum mencatat penyesuaian ini.",
  ],
  insufficient_leave_balance: [
    "The available leave balance is insufficient for this action.",
    "Saldo cuti yang tersedia tidak mencukupi untuk tindakan ini.",
  ],
  leave_balance_limit: ["The balance limit has been reached.", "Batas saldo telah tercapai."],
  self_adjustment_denied: [
    "An independent administrator must adjust your balance.",
    "Saldo Anda harus disesuaikan oleh administrator lain.",
  ],
  leave_type_unavailable: [
    "This leave type is unavailable for the selected dates.",
    "Jenis cuti tidak tersedia untuk tanggal yang dipilih.",
  ],
  invalid_leave_selection: [
    "Check the employee, leave type and selected dates.",
    "Periksa karyawan, jenis cuti, dan tanggal yang dipilih.",
  ],
  invalid_leave_submission: [
    "Enter a reason of up to 1,000 characters and review the request.",
    "Isi alasan maksimal 1.000 karakter dan review pengajuan.",
  ],
  invalid_leave_request: [
    "Check the selected leave dates and day lengths.",
    "Periksa tanggal cuti dan bagian hari yang dipilih.",
  ],
  invalid_leave_preview: [
    "Review the request again before submitting.",
    "Review kembali pengajuan sebelum mengirim.",
  ],
  leave_preview_changed: [
    "The schedule, policy or balance changed. Review the request again.",
    "Jadwal, kebijakan, atau saldo berubah. Review kembali pengajuan.",
  ],
  invalid_leave_attachments: [
    "Choose up to three different documents belonging to this employee.",
    "Pilih maksimal tiga dokumen berbeda milik karyawan ini.",
  ],
  leave_attachment_required: [
    "This leave type requires a supporting document.",
    "Jenis cuti ini memerlukan dokumen pendukung.",
  ],
  no_leave_working_days: [
    "No working days are included in the selected dates.",
    "Tidak ada hari kerja dalam tanggal yang dipilih.",
  ],
  leave_range_too_large: [
    "Choose a leave range of up to 366 days.",
    "Pilih rentang cuti maksimal 366 hari.",
  ],
  partial_leave_unavailable: [
    "This leave type requires full days.",
    "Jenis cuti ini memerlukan hari penuh.",
  ],
  leave_schedule_missing: [
    "The employee needs a work schedule for these dates.",
    "Karyawan memerlukan jadwal kerja pada tanggal tersebut.",
  ],
  leave_employee_ineligible: [
    "The employee is not eligible for this leave type on the selected dates.",
    "Karyawan belum memenuhi syarat jenis cuti ini pada tanggal yang dipilih.",
  ],
  leave_duration_too_short: [
    "The selected shift is too short for this day length.",
    "Shift yang dipilih terlalu singkat untuk bagian hari tersebut.",
  ],
  leave_overlap: [
    "These dates overlap an existing leave request. Check the request history.",
    "Tanggal ini bertumpang tindih dengan pengajuan cuti lain. Periksa riwayat pengajuan.",
  ],
  leave_request_limit: [
    "The request exceeds this leave type's day limit.",
    "Pengajuan melewati batas hari jenis cuti ini.",
  ],
  leave_ledger_conflict: [
    "The leave balance changed. Review the request again.",
    "Saldo cuti berubah. Review kembali pengajuan.",
  ],
  leave_type_not_found: [
    "This leave policy is no longer available.",
    "Kebijakan cuti ini tidak lagi tersedia.",
  ],
  invalid_leave_policy: ["Check the leave policy fields.", "Periksa isian kebijakan cuti."],
  invalid_leave_carry_expiry_recovery: [
    "Enter a reason and review the current expiry process.",
    "Isi alasan dan tinjau proses kedaluwarsa saat ini.",
  ],
  leave_carry_expiry_not_found: [
    "This carry expiry process is unavailable.",
    "Proses kedaluwarsa carryover ini tidak tersedia.",
  ],
  leave_carry_expiry_changed: [
    "The process changed. Reload its current details.",
    "Proses berubah. Muat ulang detail terbarunya.",
  ],
  leave_carry_expiry_job_obsolete: [
    "This job has been replaced. Reload the expiry process.",
    "Job ini sudah digantikan. Muat ulang proses kedaluwarsa.",
  ],
  leave_carry_expiry_not_stopped: [
    "This process has not stopped. Review its current progress.",
    "Proses ini belum berhenti. Tinjau progres terbarunya.",
  ],
  leave_carry_expiry_attempt_limit: [
    "This process has reached its limit of eight attempts.",
    "Proses ini telah mencapai batas delapan percobaan.",
  ],
  leave_carry_expiry_checkpoint_inconsistent: [
    "The recorded progress needs administrator review before this process can continue.",
    "Progres tercatat perlu diperiksa administrator sebelum proses dilanjutkan.",
  ],
  invalid_leave_batch: [
    "Check the leave type, period and reason.",
    "Periksa jenis cuti, periode, dan alasan.",
  ],
  invalid_leave_accrual_schedule: [
    "Choose a leave type, first period and 1–12 periods, then enter a reason.",
    "Pilih jenis cuti, periode pertama dan 1–12 periode, lalu isi alasan.",
  ],
  leave_accrual_schedule_not_found: [
    "This accrual schedule is unavailable.",
    "Jadwal akrual ini tidak tersedia.",
  ],
  leave_schedule_frequency_changed: [
    "The selected periods use different accrual frequencies. Review a shorter period range.",
    "Periode terpilih memakai frekuensi akrual berbeda. Tinjau rentang periode yang lebih pendek.",
  ],
  leave_batch_not_found: ["This leave process is unavailable.", "Proses cuti ini tidak tersedia."],
  leave_batch_active: [
    "Another process is active for this leave type and period. Review its progress first.",
    "Proses lain masih aktif untuk jenis cuti dan periode ini. Tinjau progresnya terlebih dahulu.",
  ],
  leave_batch_employee_limit: [
    "The reviewed selection must contain between 1 and 5,000 employees.",
    "Pilihan yang ditinjau harus berisi 1 sampai 5.000 karyawan.",
  ],
  leave_batch_employee_unavailable: [
    "An employee in the reviewed selection is no longer available. Review the selection again.",
    "Karyawan dalam pilihan yang ditinjau tidak lagi tersedia. Tinjau kembali pilihan.",
  ],
  leave_batch_policy_changed: [
    "The leave policy changed. Review the process before continuing.",
    "Kebijakan cuti berubah. Tinjau proses sebelum melanjutkan.",
  ],
  leave_batch_not_stopped: [
    "This process has not stopped. Review its current progress.",
    "Proses ini belum berhenti. Tinjau progres terbarunya.",
  ],
  leave_batch_changed: [
    "The process changed. Reload its current details.",
    "Proses berubah. Muat ulang detail terbarunya.",
  ],
  leave_batch_job_obsolete: [
    "This job has been replaced. Reload the leave process.",
    "Job ini sudah digantikan. Muat ulang proses cuti.",
  ],
  leave_batch_attempt_limit: [
    "This process has reached its limit of eight attempts.",
    "Proses ini telah mencapai batas delapan percobaan.",
  ],
  leave_batch_checkpoint_inconsistent: [
    "The recorded progress needs administrator review before this process can continue.",
    "Progres tercatat perlu diperiksa administrator sebelum proses dilanjutkan.",
  ],
  leave_batch_resume_owner_required: [
    "Only the account that submitted this process can resume it.",
    "Hanya akun yang mengajukan proses ini yang dapat melanjutkannya.",
  ],
  leave_accrual_not_configured: [
    "This leave type has no scheduled accrual policy for the selected period.",
    "Jenis cuti ini belum memiliki kebijakan akrual terjadwal untuk periode terpilih.",
  ],
  leave_accrual_not_due: [
    "Accrual is not due for the selected period.",
    "Akrual belum jatuh tempo untuk periode terpilih.",
  ],
  leave_accrual_frequency_changed: [
    "The accrual frequency differs from an existing posting in this year.",
    "Frekuensi akrual berbeda dari pencatatan yang sudah ada pada tahun ini.",
  ],
  leave_year_not_ended: [
    "The selected leave year has not ended in the company timezone.",
    "Tahun cuti terpilih belum berakhir pada zona waktu perusahaan.",
  ],
  stale_policy_version: [
    "The policy changed. Review the current version before continuing.",
    "Kebijakan berubah. Tinjau versi terbaru sebelum melanjutkan.",
  ],
  leave_account_inconsistent: [
    "The recorded leave balance needs administrator review.",
    "Saldo cuti tercatat perlu diperiksa administrator.",
  ],
  leave_resolution_required: [
    "Resolve pending leave requests and reservations before closing this year.",
    "Selesaikan pengajuan cuti dan reservasi yang masih aktif sebelum menutup tahun ini.",
  ],
  leave_destination_year_closed: [
    "The next leave year is already closed and cannot receive carryover.",
    "Tahun cuti berikutnya sudah ditutup dan tidak dapat menerima sisa cuti.",
  ],
  employment_history_capacity: [
    "The employment history exceeds the processing limit. Contact your administrator.",
    "Riwayat hubungan kerja melewati batas pemrosesan. Hubungi administrator.",
  ],
  invalid_leave_accrual_policy: [
    "Check the entitlement and carryover limits.",
    "Periksa hak cuti dan batas carryover.",
  ],
  leave_type_code_immutable: [
    "The policy code cannot be changed. Load its current version.",
    "Kode kebijakan tidak dapat diubah. Muat versi terbarunya.",
  ],
  leave_attachment_not_found: [
    "This evidence is no longer accessible. Refresh the request.",
    "Bukti ini tidak lagi dapat diakses. Muat ulang permintaan.",
  ],
  leave_attachment_unavailable: [
    "This evidence is unavailable. Refresh the request or contact your administrator.",
    "Bukti tidak tersedia. Muat ulang permintaan atau hubungi administrator.",
  ],
  document_content_integrity_failure: [
    "The file could not be verified. Try again or contact your administrator.",
    "File tidak dapat diverifikasi. Coba lagi atau hubungi administrator.",
  ],
  leave_action_unavailable: [
    "This action is no longer available. Review the current leave request.",
    "Tindakan tidak lagi tersedia. Periksa permintaan cuti terkini.",
  ],
  invalid_leave_decision: [
    "Enter a reason when rejecting leave, using at most 1,000 characters.",
    "Isi alasan penolakan cuti, maksimal 1.000 karakter.",
  ],
  invalid_leave_withdrawal: [
    "Enter a withdrawal reason of up to 1,000 characters.",
    "Isi alasan penarikan, maksimal 1.000 karakter.",
  ],
  invalid_leave_cancellation: [
    "Enter a cancellation reason of up to 1,000 characters.",
    "Isi alasan pembatalan, maksimal 1.000 karakter.",
  ],
  decision_reason_required: [
    "Enter a decision reason of up to 1,000 characters.",
    "Isi alasan keputusan, maksimal 1.000 karakter.",
  ],
  leave_not_pending: [
    "This leave request is no longer pending. Reload the current version.",
    "Permintaan cuti tidak lagi menunggu. Muat versi terkini.",
  ],
  leave_not_approved: [
    "Cancellation requires approved leave. Reload the current request.",
    "Pembatalan memerlukan cuti yang disetujui. Muat permintaan terkini.",
  ],
  approval_not_pending: [
    "This approval stage is no longer pending. Reload the request.",
    "Tahap approval tidak lagi menunggu. Muat ulang permintaan.",
  ],
  approval_unavailable: [
    "The approval cannot be reviewed. Reload or contact an administrator.",
    "Approval tidak dapat ditinjau. Muat ulang atau hubungi administrator.",
  ],
  self_approval_denied: [
    "This request requires an independent approver.",
    "Permintaan ini memerlukan approver independen.",
  ],
  not_assigned_approver: [
    "You are no longer assigned to this approval stage. Reload the request.",
    "Anda tidak lagi ditugaskan pada tahap ini. Muat ulang permintaan.",
  ],
  invalid_payroll_period: [
    "Review the period, planned payment date, employee selection and reason.",
    "Periksa periode, rencana tanggal pembayaran, pilihan karyawan, dan alasan.",
  ],
  payroll_period_not_found: [
    "This payroll period is unavailable.",
    "Periode payroll tidak tersedia.",
  ],
  payroll_period_exists: [
    "This period identifier is already in use. Review the saved period.",
    "ID periode sudah digunakan. Periksa periode yang tersimpan.",
  ],
  payroll_period_active: [
    "An active payroll period already exists for this month.",
    "Periode payroll aktif sudah ada untuk bulan ini.",
  ],
  payroll_period_attempt_limit: [
    "This month has reached its draft limit.",
    "Batas draft untuk bulan ini sudah tercapai.",
  ],
  payroll_period_employee_unavailable: [
    "An employee is no longer available in this company. Review the selection.",
    "Karyawan tidak lagi tersedia di perusahaan ini. Periksa pilihan karyawan.",
  ],
  payroll_period_not_draft: [
    "This action requires a draft payroll period.",
    "Tindakan ini memerlukan periode payroll berstatus Draft.",
  ],
  payroll_period_revision_limit: [
    "This period has reached its revision limit.",
    "Batas revisi periode sudah tercapai.",
  ],
  payroll_period_frozen: [
    "This change is blocked by a frozen payroll period.",
    "Perubahan terhalang periode payroll yang telah dikunci.",
  ],
  leave_year_closed: [
    "The leave year is closed. Contact your administrator.",
    "Tahun cuti telah ditutup. Hubungi administrator.",
  ],
  approval_policy_missing: [
    "No applicable approval policy is configured. Contact your administrator.",
    "Kebijakan approval yang sesuai belum tersedia. Hubungi administrator.",
  ],
  approval_policy_empty: [
    "The approval policy needs at least one stage. Contact your administrator.",
    "Kebijakan approval memerlukan minimal satu tahap. Hubungi administrator.",
  ],
  ambiguous_approval_policy: [
    "Multiple approval policies match. Contact your administrator.",
    "Beberapa kebijakan approval berlaku bersamaan. Hubungi administrator.",
  ],
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
    "This employment has changed. Reload the review before continuing.",
    "Employment sudah berubah. Muat ulang tinjauan sebelum melanjutkan.",
  ],
  offboarding_terms_changed: [
    "The last working date does not match the employment terms. Review the employment and case dates.",
    "Hari kerja terakhir tidak sesuai dengan ketentuan employment. Periksa tanggal employment dan proses.",
  ],
  other_lifecycle_cases_pending: [
    "Complete or cancel the other open lifecycle cases for this employment first.",
    "Selesaikan atau batalkan proses lifecycle lain yang masih terbuka untuk employment ini.",
  ],
  offboarding_cancellation_required: [
    "Cancel the active offboarding schedule before continuing.",
    "Batalkan jadwal offboarding yang masih aktif sebelum melanjutkan.",
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
    "Resolve the scheduled employment revisions before ending this employment.",
    "Selesaikan revisi employment yang terjadwal sebelum mengakhiri employment ini.",
  ],
  reporting_reassignment_required: [
    "Reassign current and scheduled reporting lines before ending this employment.",
    "Alihkan hubungan pelaporan yang aktif dan terjadwal sebelum mengakhiri employment ini.",
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
  transfer_access_required: [
    "You need employee management and transfer access in both companies.",
    "Anda memerlukan izin pengelolaan dan transfer karyawan di kedua perusahaan.",
  ],
  invalid_employment_transfer: [
    "Choose a different destination company and reload the current employment.",
    "Pilih perusahaan tujuan yang berbeda dan muat ulang employment saat ini.",
  ],
  invalid_transfer_terms: [
    "Review the destination employment terms. The employment must start on the transfer date.",
    "Periksa ketentuan employment tujuan. Employment harus dimulai pada tanggal transfer.",
  ],
  transfer_requires_current_company_date: [
    "Both companies must have the same current date. Reload the review before transferring.",
    "Tanggal saat ini di kedua perusahaan harus sama. Muat ulang tinjauan sebelum transfer.",
  ],
  source_employment_not_transferable: [
    "Only a current employment that started before today can be transferred.",
    "Hanya employment aktif yang dimulai sebelum hari ini yang dapat ditransfer.",
  ],
  destination_employment_exists: [
    "This person already has a current or planned employment in the destination company.",
    "Orang ini sudah memiliki employment aktif atau terencana di perusahaan tujuan.",
  ],
  destination_membership_required: [
    "Assign the employee's account access to the destination company first.",
    "Berikan akses akun karyawan ke perusahaan tujuan terlebih dahulu.",
  ],
  open_lifecycle_cases_pending: [
    "Complete or cancel the open lifecycle cases before transferring this employment.",
    "Selesaikan atau batalkan proses lifecycle yang masih terbuka sebelum transfer employment ini.",
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
  invalid_overtime_page: [
    "Choose valid dates covering up to 62 days and a valid overtime status.",
    "Pilih tanggal yang valid dengan rentang maksimal 62 hari dan status lembur yang valid.",
  ],
  invalid_overtime_request: [
    "Check the employee, requested time and reason.",
    "Periksa karyawan, waktu yang diajukan, dan alasan.",
  ],
  invalid_overtime_planning_revision: [
    "Review the work schedule again before creating this request.",
    "Tinjau kembali jadwal kerja sebelum membuat pengajuan.",
  ],
  overtime_planning_changed: [
    "The reviewed schedule has changed. Load the current schedule and check the requested time.",
    "Jadwal yang ditinjau telah berubah. Muat jadwal terbaru dan periksa waktu yang diajukan.",
  ],
  overtime_date_out_of_range: [
    "Choose a work date within the preceding 31 days or the next 90 days in the company timezone.",
    "Pilih tanggal kerja dalam 31 hari sebelumnya atau 90 hari berikutnya sesuai timezone perusahaan.",
  ],
  overtime_employment_inactive: [
    "This employee is not eligible for overtime on the selected date.",
    "Karyawan tidak memenuhi syarat lembur pada tanggal yang dipilih.",
  ],
  overtime_schedule_missing: [
    "Assign a work schedule or day off before creating an overtime request for this date.",
    "Tetapkan jadwal kerja atau hari libur sebelum membuat pengajuan lembur untuk tanggal ini.",
  ],
  overtime_month_capacity: [
    "This employee has reached the monthly overtime request limit.",
    "Jumlah pengajuan lembur karyawan telah mencapai batas bulanan.",
  ],
  overtime_overlap: [
    "The requested time overlaps another active overtime request.",
    "Waktu yang diajukan tumpang tindih dengan pengajuan lembur aktif lainnya.",
  ],
  overtime_overlaps_shift: [
    "Requested overtime must be outside the regular work shift.",
    "Waktu lembur harus berada di luar shift kerja reguler.",
  ],
  overtime_work_date_mismatch: [
    "Check the requested time against the selected work date and company timezone.",
    "Periksa waktu yang diajukan sesuai tanggal kerja dan timezone perusahaan.",
  ],
  overtime_request_not_found: [
    "The overtime request was not found or is no longer accessible.",
    "Pengajuan lembur tidak ditemukan atau tidak dapat diakses.",
  ],
  overtime_action_unavailable: [
    "This action is no longer available. Refresh the request.",
    "Tindakan ini tidak lagi tersedia. Muat ulang pengajuan.",
  ],
  invalid_overtime_actual: [
    "Check the actual time and enter a reason.",
    "Periksa waktu aktual dan masukkan alasan.",
  ],
  invalid_overtime_interval: [
    "Check the start, end and break. Use whole minutes within a 12-hour interval.",
    "Periksa waktu mulai, selesai, dan istirahat. Gunakan menit penuh dalam rentang maksimal 12 jam.",
  ],
  overtime_outside_requested_window: [
    "Actual time must fit within the requested interval and duration.",
    "Waktu aktual harus berada dalam rentang dan durasi yang diajukan.",
  ],
  overtime_not_ended: [
    "Actual time can be submitted after the work has ended.",
    "Waktu aktual dapat diajukan setelah pekerjaan selesai.",
  ],
  invalid_overtime_decision: [
    "Check the review decision and reason.",
    "Periksa keputusan dan alasan peninjauan.",
  ],
  invalid_overtime_withdrawal: [
    "Enter a reason for withdrawing this request.",
    "Masukkan alasan penarikan pengajuan.",
  ],
  overtime_not_planned: [
    "Actual time has already been submitted or the request has ended. Refresh the request.",
    "Waktu aktual sudah diajukan atau pengajuan telah selesai. Muat ulang pengajuan.",
  ],
  overtime_not_pending: [
    "This request is no longer pending. Refresh its status.",
    "Pengajuan ini tidak lagi menunggu keputusan. Muat ulang statusnya.",
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
  invalid_approval_reassignment: [
    "Select 1–25 different approvers and enter a reason.",
    "Pilih 1–25 approver berbeda dan masukkan alasan.",
  ],
  invalid_leave_query: [
    "Choose a valid employee, status and page.",
    "Pilih karyawan, status, dan halaman yang valid.",
  ],
  leave_request_not_found: [
    "This leave request is unavailable.",
    "Permintaan cuti ini tidak tersedia.",
  ],
  employee_scope_required: [
    "Open leave requests from an employee record.",
    "Buka permintaan cuti dari data karyawan.",
  ],
  approval_changed: [
    "The approval changed or is no longer pending. Reload the current request.",
    "Approval berubah atau tidak lagi menunggu. Muat ulang permintaan terkini.",
  ],
  invalid_delegation: [
    "Check both accounts, the reason, and a period of up to 90 days. An enabled delegation must end in the future.",
    "Periksa kedua akun, alasan, dan periode maksimal 90 hari. Delegasi yang diaktifkan harus berakhir setelah waktu saat ini.",
  ],
  delegator_immutable: [
    "The delegator changed. Reload the current delegation.",
    "Pemberi delegasi berubah. Muat ulang delegasi terkini.",
  ],
  approval_delegation_not_found: [
    "The delegation is unavailable in this company.",
    "Delegasi tidak tersedia di perusahaan ini.",
  ],
  approval_delegation_limit: [
    "An account has reached its delegation limit. Review existing delegations.",
    "Akun telah mencapai batas delegasi. Periksa delegasi yang ada.",
  ],
  approval_delegation_capacity: [
    "An account has reached its enabled delegation limit. Disable an existing delegation first.",
    "Akun telah mencapai batas delegasi yang diaktifkan. Nonaktifkan delegasi yang ada terlebih dahulu.",
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
  invalid_attendance_range: ["Select a range of up to 31 days.", "Pilih rentang maksimal 31 hari."],
  invalid_attendance_filter: [
    "Select a valid attendance status.",
    "Pilih status attendance yang valid.",
  ],
  invalid_attendance_review: [
    "Select a decision and provide a reason.",
    "Pilih keputusan dan isi alasannya.",
  ],
  invalid_attendance_correction: [
    "Check the attendance times, break and reason.",
    "Periksa waktu presensi, istirahat, dan alasan.",
  ],
  invalid_attendance_local_time: [
    "Enter valid, unambiguous times in the company timezone.",
    "Masukkan waktu yang valid dan tidak ambigu dalam zona waktu perusahaan.",
  ],
  attendance_not_found: [
    "The attendance record is no longer available.",
    "Catatan presensi tidak tersedia.",
  ],
  attendance_review_denied: [
    "You cannot review this attendance record or your own attendance.",
    "Anda tidak dapat memverifikasi catatan ini atau presensi sendiri.",
  ],
  self_correction_denied: [
    "Your attendance must be corrected by another authorized reviewer.",
    "Koreksi presensi Anda harus dilakukan reviewer lain yang berwenang.",
  ],
  attendance_sequence_conflict: [
    "Review the clock-in and clock-out sequence first.",
    "Periksa urutan jam masuk dan pulang terlebih dahulu.",
  ],
  attendance_day_corrected: [
    "This day has a correction. Review the latest record.",
    "Hari ini sudah memiliki koreksi. Periksa catatan terbaru.",
  ],
  attendance_verification_required: [
    "Resolve pending attendance verification before continuing.",
    "Selesaikan verifikasi presensi yang tertunda terlebih dahulu.",
  ],
  future_attendance_correction: [
    "Attendance cannot be corrected for a future work date.",
    "Presensi untuk tanggal kerja mendatang belum dapat dikoreksi.",
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
