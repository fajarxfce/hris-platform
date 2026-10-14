import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
/** The wire total permits fourteen integral digits; item amounts retain the assessment limit. */
export function payrollPaymentTotalMinor(amount: string): bigint {
  if (!/^(?:0|[1-9]\d{0,13})(?:\.\d{1,2})?$/u.test(amount)) throw new InvalidHttpResponseError();
  const [whole = "0", fraction = ""] = amount.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}
