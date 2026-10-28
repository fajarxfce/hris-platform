import type { HttpClient } from "../../../../core/data/http/http-client";
import type { WorkerIntegrationsDataSource } from "./worker-integrations-data-source";

export class HttpWorkerIntegrationsDataSource implements WorkerIntegrationsDataSource {
  constructor(private readonly http: HttpClient) {}
  read(signal: AbortSignal) {
    return this.http.request({ path: "/api/v1/administration/integrations/workers" }, signal);
  }
}
