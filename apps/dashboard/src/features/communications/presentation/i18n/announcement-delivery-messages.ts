import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Notification delivery",
  email: "Email reminders",
  accepted: "Accepted by mail server",
  emailNotRequested: "Email reminders were not requested.",
  emailAcceptance:
    "Mail server acceptance does not confirm delivery to a mailbox or that the message was read.",
  recipients: "Push recipients",
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
  notice: "Push and email are reminders. Messages remain in Inbox when either is unavailable.",
  acceptance:
    "Service acceptance does not confirm receipt or display on a device. Completed processing can include recipients with no eligible device.",
  retention:
    "Delivery history is kept for seven days after processing ends. Older or untracked recipients may have no status.",
  back: "Back to announcement",
};
const id: typeof en = {
  title: "Pengiriman notifikasi",
  email: "Pengingat email",
  accepted: "Diterima server email",
  emailNotRequested: "Pengingat email tidak diminta.",
  emailAcceptance:
    "Penerimaan server email tidak memastikan email masuk ke kotak surat atau pesan telah dibaca.",
  recipients: "Penerima push",
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
  notice:
    "Push dan email adalah pengingat. Pesan tetap tersedia di Inbox saat pengiriman tidak tersedia.",
  acceptance:
    "Penerimaan layanan tidak memastikan pesan diterima atau ditampilkan di perangkat. Pemrosesan selesai dapat mencakup penerima tanpa perangkat yang memenuhi syarat.",
  retention:
    "Riwayat pengiriman disimpan selama tujuh hari setelah pemrosesan selesai. Penerima lama atau yang tidak tercatat mungkin tidak memiliki status.",
  back: "Kembali ke pengumuman",
};
export const announcementDeliveryMessages = (locale: Locale) => (locale === "id" ? id : en);
