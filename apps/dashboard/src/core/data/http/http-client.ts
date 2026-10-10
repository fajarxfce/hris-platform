export type HttpRequest = Readonly<{
  path: string;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  operationId?: string;
  timeoutMilliseconds?: number;
  response?:
    | Readonly<{ type: "text"; mediaType: "text/csv"; maximumBytes: number }>
    | Readonly<{ type: "binary"; mediaType: string; byteLength: number; etag: string }>;
}>;

export interface HttpClient {
  request(request: HttpRequest, signal: AbortSignal): Promise<unknown>;
}
