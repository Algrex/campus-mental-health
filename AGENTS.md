# Repository development conventions

This directory is a self-contained repository. Development must not depend on a parent folder named Class7, course documents, a particular drive, or another developer's SSH identity.

- Read `README.md` and `docs/development-environment.md` before configuring or building.
- Use Java 21 and JavaScript. Format Java with the configured Google Java Format and JavaScript with the project ESLint/Prettier configuration. Any C++ additions must follow Google C++ Style Guide.
- Keep only extension recommendations in `.vscode/extensions.json`. Machine paths, JDK selection, terminal environment, ports, launch configurations, and workspaces belong outside Git and should be generated for the current clone.
- Use `scripts/configure-workspace.ps1` to generate the external VS Code workspace. Local JDK choices and caches belong in ignored `.tools` files.
- Maintain API contracts and database migrations alongside implementation. Keep business endpoints denied until appropriate role and record-scope authorization is implemented and tested.
- Never commit credentials, SSH keys, real student-sensitive data, runtime databases, dependency caches, or build output.
- Finish related code edits before running one necessary final check or startup. Do not repeatedly build, launch, or validate after each small edit or documentation change. Rerun only to resolve an actual failure or validate subsequent code changes. Prioritize a runnable environment and clear documentation.
- Keep existing user changes intact and report validation limits honestly.
- Course allocations and personal reports are maintained separately; they are not prerequisites for cloning or running this repository.
