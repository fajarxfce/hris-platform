export class HttpResponseError extends Error {
  constructor(
    readonly status: number,
    readonly problem: unknown,
  ) {
    super(`HTTP response ${status}`);
    this.name = "HttpResponseError";
  }
}

export class InvalidHttpResponseError extends Error {
  constructor() {
    super("Invalid API response");
    this.name = "InvalidHttpResponseError";
  }
}

export class RequestBodyTooLargeError extends Error {
  constructor() {
    super("Request body exceeds the transport limit");
    this.name = "RequestBodyTooLargeError";
  }
}
