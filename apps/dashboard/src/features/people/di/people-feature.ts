import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpEmployeeDataSource } from "../data/datasources/http-employee-data-source";
import { RemoteEmployeeRepository } from "../data/repositories/remote-employee-repository";
import { LoadEmployee } from "../domain/usecases/load-employee";
import { LoadEmployees } from "../domain/usecases/load-employees";
import { LoadEmploymentHistory } from "../domain/usecases/load-employment-history";

export function createPeopleFeature(http: HttpClient) {
  const employees = new RemoteEmployeeRepository(new HttpEmployeeDataSource(http));
  return {
    loadEmployees: new LoadEmployees(employees),
    loadEmployee: new LoadEmployee(employees),
    loadEmploymentHistory: new LoadEmploymentHistory(employees),
  };
}
