# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 4.0.x   | :white_check_mark: |
| < 4.0   | :x:                |

## Reporting a Vulnerability

We take the security of Jarvis AI OS seriously. If you discover a security vulnerability, please follow these steps:

1. **Do not** disclose the vulnerability publicly (e.g., by opening a public GitHub issue).
2. Send a detailed report to the maintainers via a [GitHub Security Advisory](https://github.com/anomalyco/Jarvis-AI/security/advisories).
3. Include a description of the vulnerability, steps to reproduce, and potential impact.
4. Allow up to 72 hours for an initial response.

## Security Best Practices for Deployments

- **API Keys**: Never hardcode API keys in source code. Use environment variables (`.env`).
- **Network**: Bind the backend to `127.0.0.1` behind a reverse proxy (nginx, Caddy) in production.
- **HTTPS**: Always use TLS/HTTPS when exposing the service to a network.
- **Database**: Provide proper Room migration paths instead of relying on `fallbackToDestructiveMigration`.
- **Validation**: Keep Pydantic validation models up to date; never trust raw WebSocket input.
- **Dependencies**: Run `pip audit` and `npm audit` regularly to check for known vulnerabilities.
