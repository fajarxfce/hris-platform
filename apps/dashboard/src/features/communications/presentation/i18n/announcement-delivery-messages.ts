import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Push delivery",
  recipients: "Inbox recipients",
  recipientCount: "Total recipients",
  pending: "Queued",
  processing: "Processing",
  completed: "Completed",
  failed: "Failed",
  superseded: "Stopped",
  unavailableRecipients: "History unavailable",
  devices: "Device processing",
  processedDevices: "Processed",
  acceptedDevices: "Accepted by push service",
  rejectedDevices: "Rejected by push service",
  observedAt: "Observed",
  notice: "Push is a reminder. Messages remain in Inbox when push is unavailable.",
  acceptance:
    "Service acceptance does not confirm receipt or display on a device. Completed processing can include recipients with no eligible device.",
  retention:
    "Delivery history is kept for seven days after processing ends. Older or untracked recipients may have no status.",
  back: "Back to announcement",
};
const id: typeof en = {
  title: "Pengiriman push",
  recipients: "Penerima Inbox",
  recipientCount: "Total penerima",
  pending: "Dalam antrean",
  processing: "Sedang diproses",
  completed: "Selesai",
  failed: "Gagal",
  superseded: "Dihentikan",
  unavailableRecipients: "Riwayat tidak tersedia",
  devices: "Pemrosesan perangkat",
  processedDevices: "Diproses",
  acceptedDevices: "Diterima layanan push",
  rejectedDevices: "Ditolak layanan push",
  observedAt: "Diamati",
  notice: "Push adalah pengingat. Pesan tetap tersedia di Inbox saat push tidak tersedia.",
  acceptance:
    "Penerimaan layanan tidak memastikan pesan diterima atau ditampilkan di perangkat. Pemrosesan selesai dapat mencakup penerima tanpa perangkat yang memenuhi syarat.",
  retention:
    "Riwayat pengiriman disimpan selama tujuh hari setelah pemrosesan selesai. Penerima lama atau yang tidak tercatat mungkin tidak memiliki status.",
  back: "Kembali ke pengumuman",
};
export const announcementDeliveryMessages = (locale: Locale) => (locale === "id" ? id : en);
