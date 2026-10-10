# HRIS Platform

A self-hosted HRIS for multi-company organizations, built with Kotlin, Spring Boot, PostgreSQL, and a React dashboard using Fluent UI.

The dashboard follows Azure Portal interaction patterns. Employees use a separate Compose application; the web dashboard serves HR, managers, finance, administrators, and auditors.

## Project status

Implementation is in progress. [Delivery status](docs/delivery.md) distinguishes implemented capabilities from the roadmap. Payroll rules and integrations must pass their documented acceptance checks before operational use.

## Build

Requires JDK 21, Docker, and Python 3.

```sh
./gradlew check :apps:server:bootJar :apps:worker:bootJar
```

See [development](docs/development.md) for Docker access, migrations, and local runtime configuration.

The dashboard requires Node.js 24 or newer. From `apps/dashboard`, run `npm ci`, then `HRIS_API_PROXY=http://127.0.0.1:8088 npm run dev` for the Compose API. The development proxy forwards same-origin API and SSO requests; its default port 8080 also supports a backend started directly with Gradle. See [dashboard](docs/dashboard.md) for implemented screens and checks.

## Documentation

- [Product and module specification](docs/product.md)
- [Architecture and ownership](docs/architecture.md)
- [SSO configuration](docs/sso.md)
- [Outbound email](docs/mail.md)
- [Mobile push delivery](docs/push.md)
- [Private object storage](docs/storage.md)
- [API conventions](docs/api-conventions.md)
- [Generated OpenAPI contract](docs/openapi.md)
- [Mobile and offline clients](docs/mobile-api.md)
- [Company client policy](docs/client-policy.md)
- [Company jobs](docs/jobs.md)
- [Reporting definitions and API](docs/reporting.md)
- [Payroll configuration and calculation rules](docs/payroll-rules.md)
- [Payslip JSON and PDF delivery](docs/payroll-payslips.md)
- [Incremental synchronization](docs/synchronization.md)
- [Dashboard design](docs/dashboard.md)
- [Delivery and validation](docs/delivery.md)

## License

Apache-2.0.
