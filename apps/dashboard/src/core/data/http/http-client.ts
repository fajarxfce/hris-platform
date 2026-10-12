export type HttpRequest = Readonly<{
  path: string;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  binaryBody?: Readonly<{
    bytes: ArrayBuffer;
    headers: Readonly<{ "Upload-Offset": string; "Upload-Checksum-Sha256": string }>;
  }>;
  operationId?: string;
  timeoutMilliseconds?: number;
  response?:
    | Readonly<{ type: "text"; mediaType: "text/csv"; maximumBytes: number }>
    | Readonly<{ type: "binary"; mediaType: string; byteLength: number; etag: string }>;
}>;

export interface HttpClient {
  request(request: HttpRequest, signal: AbortSignal): Promise<unknown>;
}
