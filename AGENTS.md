
# GitHub Copilot Instructions - JobPilot

This repository contains a local-first job search assistant
built with Java 17 and Spring Boot 3.3.5.

## Before answering a code question

1. Inspect the relevant source files.
2. Trace controller -> service -> repository interactions.
3. Check related entities and models.
4. Check application.properties and pom.xml when relevant.
5. Explain findings using actual class and method names.

## Java and Spring Boot

- Follow the existing Spring Boot architecture.
- Keep controllers thin.
- Keep business logic in services.
- Use Spring Data JPA repositories for persistence.
- Avoid introducing unnecessary dependencies.
- Preserve Java 17 compatibility.
- Check Lombok compatibility before changing Java versions.

## Frontend

- Use the existing HTML, CSS and vanilla JavaScript.
- Preserve existing API contracts.
- Keep frontend and backend behavior consistent.
- Avoid adding a frontend framework without approval.

## Browser Extension

- Preserve Manifest V3 architecture.
- Respect the extension pairing mechanism.
- Never automatically press the final Submit button.
- Never store passwords or sensitive personal answers.

## Security

- Never expose API keys or tokens.
- Use environment variables for external credentials.
- Preserve X-JobPilot validation on state-changing requests.
- Preserve localhost-only application access and CORS rules.
- Do not weaken security for convenience.

## Testing

- Add tests for new business logic.
- Test existing behavior before refactoring.
- Run Maven tests after meaningful changes.
- Report build failures honestly.

## Documentation

Update relevant documentation whenever behavior changes.

Never invent APIs, classes, methods or features.
The actual source code is the final authority.
