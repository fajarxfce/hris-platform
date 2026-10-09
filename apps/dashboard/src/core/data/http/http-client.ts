export type HttpRequest = Readonly<{
  path: string;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  operationId?: string;
}>;

export interface HttpClient {
  request(request: HttpRequest, signal: AbortSignal): Promise<unknown>;
}
