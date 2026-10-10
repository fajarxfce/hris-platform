export type AccountId = string & { readonly accountId: unique symbol };
export type CompanyId = string & { readonly companyId: unique symbol };
export type OperationId = string & { readonly operationId: unique symbol };

export const isUuid = (value: string): boolean =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu.test(value);
