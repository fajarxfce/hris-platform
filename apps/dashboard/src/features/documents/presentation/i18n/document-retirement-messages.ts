import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Content retirement",
  review: "Review retirement",
  lookup: "Review a revision",
  reference: "Revision ID",
  referenceHint: "Use the reference from Revision details.",
  invalidReference: "Enter a valid revision ID.",
  overview: "Revision review",
  number: "Revision number",
  version: "Revision version",
  reviewedAt: "Reviewed on",
  published: "Current publication",
  yes: "Yes",
  no: "No",
  blocked: "Retirement unavailable",
  hint: "This permanently removes access to this revision and schedules its file for deletion. Metadata and history are retained.",
  publicationHint: "This is the published revision. Retirement also removes its publication.",
  confirm: "Retire revision",
  saved: "Content retired. File deletion is scheduled.",
};
const id: typeof en = {
  title: "Penghentian konten",
  review: "Tinjau penghentian",
  lookup: "Tinjau revisi",
  reference: "ID revisi",
  referenceHint: "Gunakan referensi dari Detail revisi.",
  invalidReference: "Masukkan ID revisi yang valid.",
  overview: "Tinjauan revisi",
  number: "Nomor revisi",
  version: "Versi revisi",
  reviewedAt: "Ditinjau pada",
  published: "Publikasi saat ini",
  yes: "Ya",
  no: "Tidak",
  blocked: "Penghentian belum tersedia",
  hint: "Akses ke revisi ini dihapus secara permanen dan file dijadwalkan untuk dihapus. Metadata dan riwayat tetap tersimpan.",
  publicationHint: "Revisi ini sedang dipublikasikan. Penghentian juga menghapus publikasinya.",
  confirm: "Hentikan revisi",
  saved: "Konten dihentikan. Penghapusan file dijadwalkan.",
};
export const documentRetirementMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
