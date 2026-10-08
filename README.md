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

## Documentation

- [Product and module specification](docs/product.md)
- [Architecture and ownership](docs/architecture.md)
- [SSO configuration](docs/sso.md)
- [Outbound email](docs/mail.md)
- [API conventions](docs/api-conventions.md)
- [Dashboard design](docs/dashboard.md)
- [Delivery and validation](docs/delivery.md)

## License

Apache-2.0.
