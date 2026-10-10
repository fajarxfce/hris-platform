import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { FetchHttpClient } from "../core/data/http/fetch-http-client";
import { createBrowserFiles } from "../core/di/browser-files";
import type { OperationId } from "../core/domain/identifiers";
import { AppErrorBoundary } from "../core/presentation/components/app-error-boundary";
import { createAdministrationFeature } from "../features/administration/di/administration-feature";
import { createApprovalsFeature } from "../features/approvals/di/approvals-feature";
import { createIdentityFeature } from "../features/identity/di/identity-feature";
import { IdentityController } from "../features/identity/presentation/controllers/identity-controller";
import { createJobsFeature } from "../features/jobs/di/jobs-feature";
import { createLeaveFeature } from "../features/leave/di/leave-feature";
import { createLifecycleFeature } from "../features/lifecycle/di/lifecycle-feature";
import { createOrganizationFeature } from "../features/organization/di/organization-feature";
import { createPeopleFeature } from "../features/people/di/people-feature";
import { createReportingFeature } from "../features/reporting/di/reporting-feature";
import { Application } from "./application";
import "./styles.css";

const queries = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000, gcTime: 120_000 },
    mutations: { retry: false },
  },
});
const http = new FetchHttpClient(undefined, { clientBuild: __HRIS_DASHBOARD_BUILD__ });
const reporting = createReportingFeature(http);
const administration = createAdministrationFeature(http);
const approvals = createApprovalsFeature(http);
const jobs = createJobsFeature(http);
const files = createBrowserFiles();
const leave = createLeaveFeature(http, files);
const organization = createOrganizationFeature(http);
const people = createPeopleFeature(http, files);
const lifecycle = createLifecycleFeature(http);
const identity = new IdentityController(
  createIdentityFeature(http),
  () => crypto.randomUUID() as OperationId,
  () => queries.clear(),
);
const root = document.getElementById("root");
if (!root) throw new Error("Application root is missing");
const router = createBrowserRouter([
  {
    path: "*",
    element: (
      <AppErrorBoundary locale="en" onReload={() => window.location.reload()}>
        <Application
          clientBuild={__HRIS_DASHBOARD_BUILD__}
          identity={identity}
          reporting={reporting}
          administration={administration}
          approvals={approvals}
          jobs={jobs}
          leave={leave}
          organization={organization}
          people={people}
          lifecycle={lifecycle}
          nextIdentifier={() => crypto.randomUUID()}
        />
      </AppErrorBoundary>
    ),
  },
]);
const applicationRoot = createRoot(root);
applicationRoot.render(
  <StrictMode>
    <QueryClientProvider client={queries}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
if (import.meta.hot)
  import.meta.hot.dispose(() => {
    applicationRoot.unmount();
    router.dispose();
    queries.clear();
  });
