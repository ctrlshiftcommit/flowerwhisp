# Security policy

## Supported versions

Until a first stable release is published, the default branch is the only
supported development line. There is no public signed release to report.

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Use a private
GitHub security advisory for the repository when available, or contact the
maintainers through the private channel configured by the repository owner.
Do not send API keys, recordings, user databases, or other secrets in a report.

Include the affected commit, operating-system/build context, reproduction steps,
impact, and a safe proof of concept. We will acknowledge receipt, triage the
report, and coordinate disclosure once a fix or mitigation is available.

## Security expectations

Credentials must use the Windows secret seam. Never log them or place them in
exports, prompts, issues, fixtures, or workflows. Releases must fail closed
when signing secrets are absent. CI includes dependency review, CodeQL, and a
secret-scanning job; findings are reviewed before publication.
