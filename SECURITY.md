# Security Policy

## Supported versions

Security fixes, when offered, target the **latest** source on the default branch
of this repository. Older release tags or sideloaded APKs may not receive
backports unless maintainers explicitly say so.

| Version / track | Support status |
|-----------------|----------------|
| Default branch (`main`) | Actively maintained |
| Older tags / random APKs | Best-effort only; no guarantee |

## Reporting a vulnerability

**Do not** open a public GitHub Issue, Pull Request, or Discussion for security
vulnerabilities.

### Preferred private path

1. Use **GitHub Security Advisories** on this repository:  
   **Security → Advisories → Report a vulnerability**  
   (available when the repository owner has enabled private vulnerability reporting).
2. If that UI is not available yet, wait for maintainers to publish a private
   channel in a future update of this file.

### Contact placeholder

| Field | Status |
|-------|--------|
| Security email / form | **Not published yet** |
| Public issue tracker | **Not** for vulnerabilities |

Do **not** invent, guess, or scrape a maintainer email. When maintainers add a
dedicated reporting address or process, it will be documented **here**.

### What to include

- Affected component (e.g. import path, EPUB/TXT decoder, backup restore)
- Impact summary (data exposure, crash-only, privilege, etc.)
- Steps to reproduce or a minimal proof of concept
- App version / commit SHA if known
- Whether the issue is already public elsewhere

### Expectations

- Acknowledge receipt **after** a private channel exists and a report is received
  through it (target: within a few business days when maintainers are active).
- Coordinate disclosure when a fix is ready; please do not publish exploit details
  until maintainers have had a reasonable chance to patch.
- Purely local crash bugs with no security impact can use the normal bug issue
  template.

## Non-security secrets hygiene

Never commit signing keys, `keystore.properties`, API tokens, or
`local.properties`. See [CONTRIBUTING.md](./CONTRIBUTING.md). Accidental secret
commits should be reported privately the same way as vulnerabilities so keys can
be rotated.
