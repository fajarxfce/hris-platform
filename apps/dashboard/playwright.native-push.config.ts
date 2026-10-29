import { defineConfig } from "@playwright/test";
import base from "./playwright.native-integration.config";

export default defineConfig({ ...base, testMatch: "push-registration.spec.ts", timeout: 900_000 });
