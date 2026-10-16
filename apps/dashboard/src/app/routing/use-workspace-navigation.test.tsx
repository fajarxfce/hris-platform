import { act, cleanup, render } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, expect, it } from "vitest";
import type { WorkspaceNavigationController } from "../../core/presentation/navigation/workspace-navigation-controller";
import { useWorkspaceNavigation } from "./use-workspace-navigation";

afterEach(cleanup);

function fixture() {
  let navigation: WorkspaceNavigationController | undefined;
  function Workspace() {
    navigation = useWorkspaceNavigation(false).controller;
    return <div>Workspace</div>;
  }
  const router = createMemoryRouter([{ path: "*", element: <Workspace /> }], {
    initialEntries: ["/", "/account/sessions"],
  });
  const view = render(<RouterProvider router={router} />);
  if (!navigation) throw new Error("Navigation owner was not mounted");
  return {
    navigation,
    router,
    dispose: () => {
      view.unmount();
      router.dispose();
    },
  };
}

for (const action of ["link", "back"] as const) {
  it(`cancels a blocked ${action} when the operation finishes before the router effect`, async () => {
    const f = fixture();
    try {
      await act(async () => f.navigation.protect("sessions", "pending"));
      await act(async () => {
        const departure = action === "back" ? f.router.navigate(-1) : f.router.navigate("/");
        // The response can settle after the click was blocked but before React
        // registers that blocked departure with the workspace controller.
        f.navigation.protect("sessions", "none");
        await departure;
      });
      expect(f.router.state.location.pathname).toBe("/account/sessions");
      expect(f.navigation.getSnapshot().deciding).toBe(false);
      await act(async () => f.router.navigate("/"));
      expect(f.router.state.location.pathname).toBe("/");
    } finally {
      f.dispose();
    }
  });
}

it("keeps a pending departure guarded until an explicit decision", async () => {
  const f = fixture();
  try {
    await act(async () => f.navigation.protect("sessions", "pending"));
    await act(async () => f.router.navigate("/"));
    expect(f.navigation.getSnapshot().deciding).toBe(true);
    expect(f.router.state.location.pathname).toBe("/account/sessions");
    await act(async () => f.navigation.stay());
    expect(f.router.state.location.pathname).toBe("/account/sessions");
    await act(async () => f.router.navigate("/"));
    await act(async () => f.navigation.leave());
    expect(f.router.state.location.pathname).toBe("/");
  } finally {
    f.dispose();
  }
});
