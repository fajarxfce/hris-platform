import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Personal profile",
  edit: "Edit profile",
  back: "Back",
  backToProfile: "Back to profile",
  overview: "Overview",
  history: "Profile history",
  sections: "Profile sections",
  privateData: "Personal data",
  legalName: "Legal name",
  birthDate: "Birth date",
  nationality: "Nationality",
  email: "Email",
  countryHint: "Two-letter country code, for example ID.",
  reason: "Reason for change",
  owner: "Owning company",
  personId: "Person ID",
  accountId: "Linked account ID",
  actor: "Changed by (account ID)",
  version: "Profile version",
  revision: "Revision",
  recorded: "Recorded (UTC)",
  none: "—",
  ownerNote: "This profile is managed by its owning company.",
  save: "Save",
  saving: "Saving profile",
  saved: "Profile saved.",
  view: "View profile",
  reload: "Reload latest profile",
  unconfirmed: "The save outcome is not confirmed. Retry the same submission to check its result.",
  retrySave: "Retry save",
  operation: "Operation ID",
  historyPages: "Profile revision pages",
  first: "First page",
  next: "Next page",
  emptyHistory: "No profile revisions are available on this page.",
  revisionDetails: "Profile revision details",
  viewRevision: "View revision",
  historyNote: "Profile revisions are independent of employment revisions.",
  invalid_name: "Enter a legal name of up to 200 characters.",
  invalid_country: "Enter a valid two-letter country code.",
  invalid_birth_date: "Enter a valid birth date that is not in the future.",
  invalid_email: "Enter a valid email of up to 254 characters.",
  invalid_value: "Check this value.",
};
const id: typeof en = {
  title: "Profil pribadi",
  edit: "Edit profil",
  back: "Kembali",
  backToProfile: "Kembali ke profil",
  overview: "Ringkasan",
  history: "Riwayat profil",
  sections: "Bagian profil",
  privateData: "Data pribadi",
  legalName: "Nama lengkap",
  birthDate: "Tanggal lahir",
  nationality: "Kewarganegaraan",
  email: "Email",
  countryHint: "Kode negara dua huruf, misalnya ID.",
  reason: "Alasan perubahan",
  owner: "Perusahaan pemilik",
  personId: "Person ID",
  accountId: "Account ID terhubung",
  actor: "Diubah oleh (Account ID)",
  version: "Versi profil",
  revision: "Revisi",
  recorded: "Dicatat (UTC)",
  none: "—",
  ownerNote: "Profil ini dikelola oleh perusahaan pemiliknya.",
  save: "Simpan",
  saving: "Menyimpan profil",
  saved: "Profil tersimpan.",
  view: "Lihat profil",
  reload: "Muat profil terbaru",
  unconfirmed:
    "Hasil penyimpanan belum dipastikan. Ulangi pengiriman yang sama untuk memeriksa hasilnya.",
  retrySave: "Coba simpan kembali",
  operation: "Operation ID",
  historyPages: "Halaman revisi profil",
  first: "Halaman pertama",
  next: "Halaman berikutnya",
  emptyHistory: "Tidak ada revisi profil pada halaman ini.",
  revisionDetails: "Detail revisi profil",
  viewRevision: "Lihat revisi",
  historyNote: "Revisi profil terpisah dari revisi employment.",
  invalid_name: "Masukkan nama lengkap, maksimal 200 karakter.",
  invalid_country: "Masukkan kode negara dua huruf yang valid.",
  invalid_birth_date: "Masukkan tanggal lahir yang valid dan tidak di masa depan.",
  invalid_email: "Masukkan email yang valid, maksimal 254 karakter.",
  invalid_value: "Periksa nilai ini.",
};
export const personProfileMessages = (locale: Locale) => (locale === "id" ? id : en);
export function profileFieldError(code: string | undefined, locale: Locale): string | undefined {
  if (!code) return undefined;
  const text = personProfileMessages(locale);
  switch (code) {
    case "invalid_name":
      return text.invalid_name;
    case "invalid_country":
      return text.invalid_country;
    case "invalid_birth_date":
      return text.invalid_birth_date;
    case "invalid_email":
      return text.invalid_email;
    default:
      return text.invalid_value;
  }
}
