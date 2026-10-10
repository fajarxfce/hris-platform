import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpOrganizationDataSource } from "../data/datasources/http-organization-data-source";
import { RemoteOrganizationRepository } from "../data/repositories/remote-organization-repository";
import { LoadOrganizationUnit } from "../domain/usecases/load-organization-unit";
import { LoadOrganizationUnits } from "../domain/usecases/load-organization-units";
import { SaveOrganizationUnit } from "../domain/usecases/save-organization-unit";

export function createOrganizationFeature(http: HttpClient) {
  const units = new RemoteOrganizationRepository(new HttpOrganizationDataSource(http));
  return {
    loadUnits: new LoadOrganizationUnits(units),
    loadUnit: new LoadOrganizationUnit(units),
    saveUnit: new SaveOrganizationUnit(units),
  };
}
