# Contributing to Jarvis AI

Thank you for your interest in contributing to Jarvis AI! We welcome contributions from the community to help make this autonomous mobile agent even better.

## How to Contribute

### 1. Reporting Bugs
- Use the GitHub Issues tracker to report bugs.
- Provide a clear and descriptive title.
- Include steps to reproduce the issue, the expected behavior, and the actual behavior.
- Mention your Android version and device model.

### 2. Suggesting Enhancements
- Open a GitHub Issue with the tag "enhancement".
- Describe the feature you'd like to see and why it would be useful.

### 3. Pull Requests
- Fork the repository.
- Create a new branch for your feature or bugfix (`git checkout -b feature/your-feature-name`).
- Make your changes and ensure the code follows the project's style.
- Commit your changes with clear and descriptive commit messages.
- Push to your fork and submit a pull request.

## Development Setup

### Android App
- Open the `app` directory in Android Studio.
- Ensure you have the latest Android SDK and NDK installed.
- Use `./gradlew installDebug` to deploy to a connected device.

### Backend
- Navigate to the `backend` directory.
- Install dependencies: `pip install -r requirements.txt` (Note: Ensure a requirements.txt is created if not present).
- Run the FastAPI server: `python main.py`.

### Frontend
- Navigate to the `frontend` directory.
- Install dependencies: `npm install`.
- Run the development server: `npm run dev`.

## Code of Conduct
Please be respectful and professional in all interactions within the project.

## License
By contributing to Jarvis AI, you agree that your contributions will be licensed under the MIT License.
