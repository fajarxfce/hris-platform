import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Reassign current stage",
  action: "Reassign approvers",
  back: "Back to request",
  stage: "Current stage",
  assigned: "Current approvers",
  hint: "Replace the approvers for this stage. Other stages remain unchanged. Select 1–25 eligible accounts; one approver completes the stage.",
  exclusions: "The author, beneficiary, and retained makers cannot approve this request.",
  selection: "New approvers",
  save: "Confirm reassignment",
  saving: "Updating approvers…",
  saved: "Approvers updated.",
  view: "View request",
  review: "Request under review",
};
const id: typeof en = {
  title: "Ganti approver tahap aktif",
  action: "Ganti approver",
  back: "Kembali ke permintaan",
  stage: "Tahap aktif",
  assigned: "Approver saat ini",
  hint: "Ganti approver untuk tahap ini. Tahap lain tetap dipertahankan. Pilih 1–25 akun yang memenuhi syarat; satu approver menyelesaikan tahap.",
  exclusions:
    "Pembuat, penerima manfaat, dan maker yang tercatat tidak dapat menyetujui permintaan ini.",
  selection: "Approver baru",
  save: "Konfirmasi penggantian",
  saving: "Memperbarui approver…",
  saved: "Approver diperbarui.",
  view: "Lihat permintaan",
  review: "Permintaan yang ditinjau",
};
export const approvalReassignmentMessages = (locale: Locale) => (locale === "id" ? id : en);
