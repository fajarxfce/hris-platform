export type HttpRequest = Readonly<{
  path: string;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  operationId?: string;
  response?: Readonly<{ type: "text"; mediaType: "text/csv"; maximumBytes: number }>;
}>;

export interface HttpClient {
  request(request: HttpRequest, signal: AbortSignal): Promise<unknown>;
}
