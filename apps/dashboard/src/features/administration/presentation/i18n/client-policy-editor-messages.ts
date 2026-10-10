import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Edit client policy",
  edit: "Edit latest policy",
  back: "Back to client policy",
  save: "Save policy",
  saving: "Saving policy",
  saved: "Policy saved.",
  view: "View saved revision",
  reload: "Reload configuration",
  retry: "Retry original save",
  operation: "Operation",
  unconfirmed:
    "The save result is unconfirmed. Retry the original save before making another change.",
  basedOn: "Configured revision",
  effective: "Effective revision",
  default: "Default",
  clientBuild: "This dashboard build",
  activation: "Activation",
  immediate: "Immediately",
  scheduled: "Scheduled",
  activateAt: "Activate at (UTC)",
  reason: "Reason",
  enabledModules: "Enabled modules",
  moduleHint: "Disabled modules reject new requests. Permissions remain unchanged.",
  buildHint:
    "Use 0 to allow any build. Clients below the minimum cannot start new business requests.",
  webHint:
    "This minimum exceeds the current dashboard build. Settings remain available for recovery.",
  scheduleHint: "An immediate revision replaces older scheduled policies.",
  utcHint:
    "Enter UTC timestamps, for example 2027-01-01T09:00:00Z. Maintenance can last up to seven days.",
  maintenanceSection: "Maintenance",
  maintenance: "Schedule maintenance",
  maintenanceStarts: "Maintenance starts (UTC)",
  maintenanceEnds: "Maintenance ends (UTC)",
};
const id: typeof en = {
  title: "Edit policy client",
  edit: "Edit policy terbaru",
  back: "Kembali ke policy client",
  save: "Simpan policy",
  saving: "Menyimpan policy",
  saved: "Policy tersimpan.",
  view: "Lihat revisi tersimpan",
  reload: "Muat ulang konfigurasi",
  retry: "Ulangi penyimpanan awal",
  operation: "Operasi",
  unconfirmed:
    "Hasil penyimpanan belum terkonfirmasi. Ulangi penyimpanan awal sebelum membuat perubahan lain.",
  basedOn: "Revisi konfigurasi",
  effective: "Revisi efektif",
  default: "Default",
  clientBuild: "Build dashboard ini",
  activation: "Aktivasi",
  immediate: "Langsung",
  scheduled: "Terjadwal",
  activateAt: "Mulai berlaku (UTC)",
  reason: "Alasan",
  enabledModules: "Modul aktif",
  moduleHint: "Modul nonaktif menolak request baru. Hak akses tetap sama.",
  buildHint:
    "Gunakan 0 untuk mengizinkan semua build. Client di bawah minimum tidak dapat memulai request bisnis baru.",
  webHint:
    "Minimum ini melebihi build dashboard saat ini. Pengaturan tetap tersedia untuk pemulihan.",
  scheduleHint: "Revisi yang langsung berlaku menggantikan policy terjadwal yang lebih lama.",
  utcHint:
    "Masukkan timestamp UTC, misalnya 2027-01-01T09:00:00Z. Maintenance maksimal tujuh hari.",
  maintenanceSection: "Maintenance",
  maintenance: "Jadwalkan maintenance",
  maintenanceStarts: "Mulai maintenance (UTC)",
  maintenanceEnds: "Akhir maintenance (UTC)",
};
export const clientPolicyEditorMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;

const fieldMessages: Readonly<Record<string, readonly [string, string]>> = {
  invalid_build_number: [
    "Enter a whole number from 0 to 999,999,999.",
    "Masukkan bilangan bulat dari 0 hingga 999.999.999.",
  ],
  invalid_timestamp: [
    "Enter a valid UTC timestamp between 2024 and 2100.",
    "Masukkan timestamp UTC yang valid antara tahun 2024 dan 2100.",
  ],
  invalid_maintenance_window: [
    "The end must follow the start, within seven days.",
    "Waktu akhir harus setelah waktu mulai, maksimal tujuh hari.",
  ],
  invalid_reason: [
    "Enter a single-line reason of up to 1,000 characters.",
    "Masukkan alasan satu baris, maksimal 1.000 karakter.",
  ],
  invalid_module: [
    "Choose supported modules without duplicates.",
    "Pilih modul yang tersedia tanpa duplikasi.",
  ],
  invalid_revision: ["Reload the current configuration.", "Muat ulang konfigurasi terbaru."],
};
export function clientPolicyFieldMessage(
  failure: Failure | null,
  field: string,
  locale: Locale,
): string | undefined {
  const code = failure?.fields[field];
  return code ? fieldMessages[code]?.[locale === "id" ? 1 : 0] : undefined;
}
