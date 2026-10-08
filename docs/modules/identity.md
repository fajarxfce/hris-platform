# Identity

Own accounts, sessions, invitations, OIDC links, MFA, memberships, roles, and permission assignments.

Use verified issuer/subject for OIDC. Local invitations and recovery tokens are single-use. Web cookies use HttpOnly/Secure and CSRF; mobile refresh rotation detects reuse. Privileged accounts require MFA and sensitive actions recent authentication.

Group/company administration, HR, manager, finance, auditor, and employee have explicit action/resource scopes. Disabling account or membership revokes that access without granting payroll access through administrative role names.

Screens: users, roles, company assignments, SSO configuration, active sessions.

Acceptance: cross-company/resource denial, refresh replay/revocation, invitation reuse, stale memberships, CSRF, and secret-free diagnostics.
