export type Failure = Readonly<{
  code: string;
  fields: Readonly<Record<string, string>>;
  parameters: Readonly<Record<string, string>>;
  correlationId?: string;
  retryAfterSeconds?: number;
}>;

export type Result<T> =
  | Readonly<{ ok: true; value: T }>
  | Readonly<{ ok: false; failure: Failure }>;

export const success = <T>(value: T): Result<T> => ({ ok: true, value });

export const failed = (code: string): Result<never> => ({
  ok: false,
  failure: { code, fields: {}, parameters: {} },
});
