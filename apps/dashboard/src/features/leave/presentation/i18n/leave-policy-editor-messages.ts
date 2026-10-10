import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  create: "Create policy",
  edit: "Edit policy",
  save: "Save policy",
  saved: "Policy saved.",
  view: "View policy",
  basedOn: "Editing version",
  currentVersion: "Saved version",
  retry: "Retry save",
  reload: "Load current policy",
  operation: "Operation ID",
  unconfirmed: "The save result is unconfirmed. Retry the same submission to check its outcome.",
  codeHint: "Letters, numbers, underscores or hyphens. The code cannot be changed later.",
  accrualHint: "Saving a policy does not post leave balances.",
  daysHint: "Use 0.5 for half a day. Whole-day policies require whole days.",
  monthlyHint: "0.5–31 days per month.",
  annualHint: "0.5–366 days per year.",
  contracts: "Eligible contracts",
  invalid_value: "Check this value.",
  invalid_code: "Enter a code starting with a letter, up to 32 characters.",
  invalid_name: "Enter a name, up to 200 characters.",
  invalid_reason: "Enter a reason, up to 1,000 characters.",
  invalid_date: "Enter a valid date between 1900 and 2200.",
  invalid_contracts: "Select at least one contract type.",
  out_of_range: "Enter a value within the permitted range.",
} as const;
const id: Record<keyof typeof en, string> = {
  create: "Buat kebijakan",
  edit: "Edit kebijakan",
  save: "Simpan kebijakan",
  saved: "Kebijakan disimpan.",
  view: "Lihat kebijakan",
  basedOn: "Versi yang diedit",
  currentVersion: "Versi tersimpan",
  retry: "Coba simpan kembali",
  reload: "Muat kebijakan terbaru",
  operation: "Operation ID",
  unconfirmed:
    "Hasil penyimpanan belum dipastikan. Ulangi pengiriman yang sama untuk memeriksa hasilnya.",
  codeHint:
    "Huruf, angka, garis bawah, atau tanda hubung. Kode tidak dapat diubah setelah disimpan.",
  accrualHint: "Menyimpan kebijakan tidak menambahkan saldo cuti.",
  daysHint: "Gunakan 0.5 untuk setengah hari. Kebijakan hari penuh memerlukan jumlah hari bulat.",
  monthlyHint: "0.5–31 hari per bulan.",
  annualHint: "0.5–366 hari per tahun.",
  contracts: "Kontrak yang memenuhi syarat",
  invalid_value: "Periksa nilai ini.",
  invalid_code: "Masukkan kode yang diawali huruf, maksimal 32 karakter.",
  invalid_name: "Masukkan nama, maksimal 200 karakter.",
  invalid_reason: "Masukkan alasan, maksimal 1.000 karakter.",
  invalid_date: "Masukkan tanggal yang valid antara tahun 1900 dan 2200.",
  invalid_contracts: "Pilih minimal satu jenis kontrak.",
  out_of_range: "Masukkan nilai dalam rentang yang diizinkan.",
};
export const leavePolicyEditorMessages = (
  locale: Locale,
): Readonly<Record<keyof typeof en, string>> => (locale === "id" ? id : en);
export function leavePolicyFieldError(
  code: string | undefined,
  locale: Locale,
): string | undefined {
  if (!code) return undefined;
  const text = leavePolicyEditorMessages(locale);
  switch (code) {
    case "invalid_code":
      return text.invalid_code;
    case "invalid_name":
      return text.invalid_name;
    case "invalid_reason":
      return text.invalid_reason;
    case "invalid_date":
      return text.invalid_date;
    case "invalid_contracts":
      return text.invalid_contracts;
    case "out_of_range":
      return text.out_of_range;
    default:
      return text.invalid_value;
  }
}
