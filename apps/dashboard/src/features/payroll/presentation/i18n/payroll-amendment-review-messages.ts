import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Amendment reviews",
  review: "Amendment review",
  back: "All amendment reviews",
  proposal: "View proposal",
  basis: "Reviewed calculation",
  open: "View review",
  submit: "Submit for review",
  submission: "Submit amendment for review",
  submissionNote: "Submit this retained calculation for independent review.",
  submitted: "Amendment submitted for review.",
  empty: "No reviews have been submitted for this amendment.",
  APPROVE: "Approve",
  REJECT: "Reject",
  WITHDRAW: "Withdraw",
  APPROVE_NOTE: "Approve this stage of the retained correction.",
  REJECT_NOTE:
    "Reject this review. A revised proposal and calculation are required before resubmission.",
  WITHDRAW_NOTE: "Withdraw this review and retain its history.",
  decided: "Decision recorded.",
  withdrawn: "Review withdrawn.",
};
const id: typeof en = {
  title: "Review koreksi payroll",
  review: "Review koreksi",
  back: "Semua review koreksi",
  proposal: "Lihat proposal",
  basis: "Perhitungan yang ditinjau",
  open: "Lihat review",
  submit: "Ajukan review",
  submission: "Ajukan koreksi untuk review",
  submissionNote: "Ajukan hasil perhitungan tersimpan ini untuk review independen.",
  submitted: "Koreksi diajukan untuk review.",
  empty: "Belum ada review yang diajukan untuk koreksi ini.",
  APPROVE: "Setujui",
  REJECT: "Tolak",
  WITHDRAW: "Tarik",
  APPROVE_NOTE: "Setujui tahap review koreksi ini.",
  REJECT_NOTE:
    "Tolak review ini. Proposal dan perhitungan perlu diperbarui sebelum diajukan kembali.",
  WITHDRAW_NOTE: "Tarik review ini dan simpan riwayatnya.",
  decided: "Keputusan dicatat.",
  withdrawn: "Review ditarik.",
};
export const payrollAmendmentReviewMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
