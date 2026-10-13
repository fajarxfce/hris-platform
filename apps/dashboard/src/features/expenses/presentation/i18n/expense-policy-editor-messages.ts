import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  create: "Create category",
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
  codeHint:
    "2–32 letters, numbers or underscores, starting with a letter. The code cannot be changed later.",
  amountHint: "Use a decimal point and up to two decimal places, without grouping separators.",
  limits: "Amount limits",
  identity: "Category",
  archiveHint:
    "An inactive revision prevents claims from using this category from its effective date.",
  invalid_value: "Check this value.",
  invalid_code: "Enter a valid code, 2–32 characters.",
  invalid_name: "Enter a name, up to 120 characters.",
  invalid_reason: "Enter a reason, up to 1,000 characters.",
  invalid_date: "Enter a valid date between 1900 and 2200.",
  invalid_contracts: "Select at least one contract type.",
  out_of_range: "Enter a whole number from 1 to 366.",
  invalid_amount: "Enter an amount from 0.01 to 999999999999.99.",
  claim_below_line: "The claim limit must be at least the line limit.",
} as const;
const id: Record<keyof typeof en, string> = {
  create: "Buat kategori",
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
    "2–32 huruf, angka, atau garis bawah, diawali huruf. Kode tidak dapat diubah setelah disimpan.",
  amountHint: "Gunakan titik desimal dan maksimal dua angka desimal, tanpa pemisah ribuan.",
  limits: "Batas nominal",
  identity: "Kategori",
  archiveHint: "Revisi nonaktif menghentikan penggunaan kategori mulai tanggal berlakunya.",
  invalid_value: "Periksa nilai ini.",
  invalid_code: "Masukkan kode yang valid, 2–32 karakter.",
  invalid_name: "Masukkan nama, maksimal 120 karakter.",
  invalid_reason: "Masukkan alasan, maksimal 1.000 karakter.",
  invalid_date: "Masukkan tanggal yang valid antara tahun 1900 dan 2200.",
  invalid_contracts: "Pilih minimal satu jenis kontrak.",
  out_of_range: "Masukkan bilangan bulat dari 1 sampai 366.",
  invalid_amount: "Masukkan nominal dari 0.01 sampai 999999999999.99.",
  claim_below_line: "Batas per klaim harus sama atau lebih besar dari batas per item.",
};
export const expensePolicyEditorMessages = (
  locale: Locale,
): Readonly<Record<keyof typeof en, string>> => (locale === "id" ? id : en);
export function expensePolicyFieldError(
  code: string | undefined,
  locale: Locale,
): string | undefined {
  if (!code) return undefined;
  const text = expensePolicyEditorMessages(locale);
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
    case "invalid_amount":
      return text.invalid_amount;
    case "claim_below_line":
      return text.claim_below_line;
    default:
      return text.invalid_value;
  }
}
