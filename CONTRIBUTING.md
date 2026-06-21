# Contributing to Jarvis AI OS

Thank you for your interest in contributing! We welcome bug reports, feature requests, documentation improvements, and code contributions.

## Getting Started

1. Read the [README](README.md) for project overview and setup.
2. Check [open issues](https://github.com/anomalyco/Jarvis-AI/issues) for existing work.
3. Review [SECURITY.md](SECURITY.md) for security-related disclosures.
4. Fork the repository and create a feature branch:

```bash
git checkout -b feature/my-feature
# or
git checkout -b fix/my-bugfix
```

## Development Setup

### Backend

```bash
cd backend
cp .env.example .env
# Edit .env with your API keys
pip install -r requirements.txt
uvicorn app.main:app --reload
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

### Android App

Open the `app/` directory in Android Studio. Sync Gradle and run on a connected device or emulator.

## Code Style

- **Python**: Follow [PEP 8](https://peps.python.org/pep-0008/). Run `ruff check .` before committing.
- **TypeScript/React**: Use the existing patterns (functional components, hooks, Tailwind CSS classes).
- **Kotlin**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html). Use `private const val TAG` in companion objects for logging.
- **Imports**: Remove unused imports before committing. Keep imports organized (standard library first, then framework, then project).

## Testing

- **Backend**: New features should include unit tests. Run `pytest backend/tests/ -v`.
- **Frontend**: Ensure `npm run build` completes without errors.
- **Android**: Ensure the project compiles with `./gradlew assembleDebug`.

## Commit Guidelines

- Use clear, descriptive commit messages in the imperative mood.
- Structure: `category: brief description` (e.g., `backend: add SSRF allowlist validation`).
- Keep commits focused on a single logical change.
- Reference issue numbers when applicable: `fixes #123`.

## Pull Request Process

1. Ensure your branch is up to date with `main`.
2. Run tests and lint checks locally.
3. Use the [PR template](.github/PULL_REQUEST_TEMPLATE.md) when opening a PR.
4. A maintainer will review your changes within a few days.
5. Address any review feedback; squashing commits may be requested before merging.

## Code of Conduct

Please be respectful and professional. Harassment, trolling, and personal attacks will not be tolerated. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for the full policy.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
