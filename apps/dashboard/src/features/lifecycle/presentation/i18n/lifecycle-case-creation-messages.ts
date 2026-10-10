import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Start lifecycle case",
  back: "Back to employee",
  employee: "Employee",
  employeeNumber: "Employee number",
  effectiveDate: "Employment as of",
  template: "Template",
  choose: "Choose template",
  select: "Select",
  empty: "No active templates on this page.",
  targetDate: "Target date",
  dueDate: "Due date",
  tasksNote: "Tasks start unassigned. Assign members after creating the case.",
  create: "Start case",
  saving: "Starting case",
  saved: "Lifecycle case started.",
  view: "Open case",
  retry: "Retry original request",
  unconfirmed:
    "The result is unconfirmed. Retry the original request before starting another case.",
};
const id: typeof en = {
  title: "Mulai proses lifecycle",
  back: "Kembali ke karyawan",
  employee: "Karyawan",
  employeeNumber: "Nomor karyawan",
  effectiveDate: "Employment per tanggal",
  template: "Template",
  choose: "Pilih template",
  select: "Pilih",
  empty: "Tidak ada template aktif pada halaman ini.",
  targetDate: "Tanggal target",
  dueDate: "Jatuh tempo",
  tasksNote: "Tugas dibuat tanpa penanggung jawab. Tetapkan anggota setelah proses dibuat.",
  create: "Mulai proses",
  saving: "Memulai proses",
  saved: "Proses lifecycle dimulai.",
  view: "Buka proses",
  retry: "Ulangi request awal",
  unconfirmed: "Hasil belum terkonfirmasi. Ulangi request awal sebelum memulai proses lain.",
};
export const lifecycleCaseCreationMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
