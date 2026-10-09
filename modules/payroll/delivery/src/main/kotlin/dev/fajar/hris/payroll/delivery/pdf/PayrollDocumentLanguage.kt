package dev.fajar.hris.payroll.delivery.pdf

import java.util.Locale

enum class PayrollDocumentLanguage(val locale: Locale) {
    ID(Locale.forLanguageTag("id-ID")),
    EN(Locale.forLanguageTag("en-US")),
}

internal enum class PayslipText(private val indonesian: String, private val english: String) {
    TITLE("Slip gaji", "Payslip"),
    EMPLOYEE("Karyawan", "Employee"),
    EMPLOYEE_NUMBER("Nomor karyawan", "Employee number"),
    PERIOD("Periode pajak", "Tax period"),
    PAYMENT_DATE("Rencana pembayaran", "Planned payment date"),
    EARNINGS("Pendapatan", "Earnings"),
    BASIC("Gaji pokok", "Basic salary"),
    FIXED("Tunjangan tetap", "Fixed allowance"),
    VARIABLE("Pendapatan variabel", "Variable earnings"),
    OVERTIME("Lembur", "Overtime"),
    HOLIDAY("THR", "THR"),
    TAX_ALLOWANCE("Tunjangan pajak", "Tax allowance"),
    DEDUCTION_ALLOWANCE("Tunjangan potongan", "Deduction allowance"),
    DEDUCTIONS("Potongan karyawan", "Employee deductions"),
    RETIREMENT("Iuran pensiun dan hari tua", "Retirement contributions"),
    DONATIONS("Zakat dan sumbangan yang memenuhi ketentuan", "Qualified donations"),
    OTHER_DEDUCTIONS("Potongan lainnya", "Other deductions"),
    TOTAL_DEDUCTIONS("Jumlah potongan karyawan", "Total employee deductions"),
    INCOME_TAX("PPh 21/26", "PPh 21/26"),
    TAKE_HOME("Gaji bersih", "Take-home pay"),
    EMPLOYER_CONTRIBUTIONS("Iuran perusahaan", "Employer contributions"),
    HEALTH("BPJS Kesehatan", "BPJS Health"),
    OLD_AGE("Jaminan Hari Tua (JHT)", "Old-age insurance (JHT)"),
    PENSION("Jaminan Pensiun (JP)", "Pension insurance (JP)"),
    ACCIDENT("Jaminan Kecelakaan Kerja (JKK)", "Work accident insurance (JKK)"),
    DEATH("Jaminan Kematian (JKM)", "Death insurance (JKM)"),
    TAX_SUMMARY("Ringkasan pajak", "Tax summary"),
    TAXABLE_GROSS("Penghasilan bruto pajak", "Taxable gross income"),
    NON_CASH("Penghasilan nonkas kena pajak", "Taxable non-cash income"),
    RULE("Aturan perhitungan", "Calculation rule"),
    REFERENCE("Referensi", "Reference"),
    PUBLISHED("Diterbitkan", "Published"),
    PAGE("Halaman", "Page");

    fun value(language: PayrollDocumentLanguage): String =
        if (language == PayrollDocumentLanguage.ID) indonesian else english
}
