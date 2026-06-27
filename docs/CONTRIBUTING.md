# Contributing to GigRun

Thank you for your interest in contributing! This document outlines the process for contributing to GigRun.

## Code of Conduct

By participating, you are expected to uphold our code of conduct:
- Be respectful and inclusive
- Welcome newcomers and help them get started
- Focus on constructive feedback
- Respect differing viewpoints and experiences

## Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17+
- Android SDK 34+
- Google Maps API key (for map features)

### Development Setup

1. **Fork and clone the repository**
```bash
git clone https://github.com/yourusername/GigRun.git
cd GigRun/app-root
```

2. **Add your Google Maps API key**
Create `local.properties` in the `app-root` directory:
```properties
MAPS_API_KEY=your_google_maps_api_key_here
```

3. **Build the project**
```bash
./gradlew assembleDebug
```

4. **Run tests**
```bash
./gradlew test
```

## Project Structure

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architecture documentation.

## Branching Strategy

- `main` - Stable release branch
- `develop` - Integration branch for features
- `feature/*` - Feature branches (e.g., `feature/add-uber-support`)
- `fix/*` - Bug fix branches (e.g., `fix/crash-detection-threshold`)
- `docs/*` - Documentation updates

## Commit Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation changes
- `style` - Code style changes (formatting, etc.)
- `refactor` - Code refactoring
- `test` - Adding or modifying tests
- `chore` - Maintenance tasks (build, deps, etc.)

### Examples
```
feat(dashboard): add weekly earnings chart
fix(fsm): correct state transition from WAITING to DELIVERING
docs(architecture): update module structure diagram
test(utils): add HaversineCalculator edge case tests
```

## Pull Request Process

1. **Create a feature branch** from `develop`
2. **Make your changes** with clear, focused commits
3. **Write tests** for new functionality
4. **Run the test suite** and ensure all tests pass
5. **Update documentation** if needed
6. **Open a PR** against `develop` with:
   - Clear title following commit convention
   - Description of changes
   - Screenshots for UI changes
   - Link to related issues

## Code Style

### Kotlin
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `ktlint` for formatting (configured in Gradle)
- Prefer `val` over `var`
- Use meaningful names, avoid abbreviations
- Document public APIs with KDoc

### Compose
- Follow [Compose Guidelines](https://developer.android.com/jetpack/compose/guidelines)
- Keep composables small and focused
- Use `remember` and `derivedStateOf` correctly
- Extract reusable components to `ui/components/`

### Architecture
- Keep layers separated (presentation, data, core)
- Use interfaces for repository contracts
- Dependency injection via Hilt
- No Android dependencies in `core/utils/`

## Testing Requirements

- **New features**: Add unit tests for business logic
- **Bug fixes**: Add regression test
- **Target**: 80%+ coverage for core/utils and data layers
- Run tests before submitting: `./gradlew test`

## Documentation

- Update `README.md` for user-facing changes
- Update `ARCHITECTURE.md` for structural changes
- Add KDoc for public classes/functions
- Update inline comments for complex logic

## Release Process

1. Version bump in `app/build.gradle.kts` (`versionCode`, `versionName`)
2. Update `CHANGELOG.md`
3. Create release branch from `develop`
4. Final testing and bug fixes
5. Merge to `main` and tag release
6. Build release APK/AAB
7. Deploy to distribution channels

## Reporting Issues

Use GitHub Issues with:
- Clear title and description
- Steps to reproduce
- Expected vs actual behavior
- Device/OS version
- Screenshots/logs if applicable

## Feature Requests

Open a GitHub Discussion or Issue with:
- Problem statement
- Proposed solution
- Use cases
- Mockups (for UI features)

## License

This project is for personal/educational use. All rights reserved.