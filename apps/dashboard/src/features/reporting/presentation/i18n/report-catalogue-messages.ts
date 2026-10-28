import type { Locale } from "../../../../core/presentation/i18n/messages";

const english = {
  title: "Report catalogue",
  available: "Available reports",
  report: "Report",
  description: "Coverage",
  open: "Open report",
  empty: "No reports are available with your current company permissions.",
  introduction: "Choose a report, then select its dates and filters.",
  HEADCOUNT: {
    title: "Headcount",
    description:
      "Employments and distinct people on a selected date, in one or more authorized companies.",
  },
  ATTENDANCE_EXCEPTIONS: {
    title: "Attendance exceptions",
    description: "Current attendance issues by day. Select up to 31 days.",
  },
  ATTENDANCE_CLOSINGS: {
    title: "Attendance closing report",
    description:
      "Published monthly attendance, comparing the original closing with later corrections. Select up to 12 months.",
  },
  LEAVE: {
    title: "Leave report",
    description: "Submitted working days and current leave status. Select up to 366 days.",
  },
  OVERTIME: {
    title: "Overtime report",
    description:
      "Requested, actual and approved minutes by work date and current status. Select up to 366 days.",
  },
  EXPENSES: {
    title: "Expense report",
    description: "Latest submitted transactions and current claim status. Select up to 366 days.",
  },
  PAYROLL_COSTS: {
    title: "Payroll cost report",
    description:
      "Latest published payslip costs, deductions and take-home pay by earnings month. Select up to 12 months.",
  },
};
const indonesian: typeof english = {
  title: "Katalog laporan",
  available: "Laporan tersedia",
  report: "Laporan",
  description: "Cakupan",
  open: "Buka laporan",
  empty: "Belum ada laporan yang tersedia dengan izin perusahaan Anda saat ini.",
  introduction: "Pilih laporan, lalu tentukan tanggal dan filternya.",
  HEADCOUNT: {
    title: "Jumlah karyawan",
    description:
      "Hubungan kerja dan orang unik pada tanggal pilihan, di satu atau beberapa perusahaan yang diizinkan.",
  },
  ATTENDANCE_EXCEPTIONS: {
    title: "Masalah presensi",
    description: "Masalah presensi terkini per hari. Pilih hingga 31 hari.",
  },
  ATTENDANCE_CLOSINGS: {
    title: "Laporan penutupan presensi",
    description:
      "Presensi bulanan yang sudah diterbitkan, membandingkan penutupan asli dengan koreksi berikutnya. Pilih hingga 12 bulan.",
  },
  LEAVE: {
    title: "Laporan cuti",
    description: "Hari kerja yang diajukan dan status cuti terkini. Pilih hingga 366 hari.",
  },
  OVERTIME: {
    title: "Laporan lembur",
    description:
      "Menit pengajuan, aktual, dan persetujuan menurut tanggal kerja serta status terkini. Pilih hingga 366 hari.",
  },
  EXPENSES: {
    title: "Laporan pengeluaran",
    description:
      "Transaksi terakhir yang diajukan dan status klaim terkini. Pilih hingga 366 hari.",
  },
  PAYROLL_COSTS: {
    title: "Laporan biaya penggajian",
    description:
      "Biaya, potongan, dan penghasilan bersih dari slip terakhir yang diterbitkan per bulan penghasilan. Pilih hingga 12 bulan.",
  },
};
export const reportCatalogueMessages = (locale: Locale) => (locale === "id" ? indonesian : english);
