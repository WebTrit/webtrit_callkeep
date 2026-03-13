# Contributing to Webtrit CallKeep

## Branch Naming

Branches must follow the pattern `<type>/<description>`, where `<description>` uses kebab-case.

**Accepted prefixes:**

| Prefix                | When to use                               |
|-----------------------|-------------------------------------------|
| `feature/` or `feat/` | New feature or enhancement                |
| `fix/`                | Bug fix                                   |
| `refactor/`           | Code refactoring without behavior change  |
| `chore/`              | Maintenance, dependency updates, tooling  |
| `docs/`               | Documentation only                        |
| `style/`              | Code style / formatting changes           |
| `build/`              | Build system or CI changes                |
| `release/`            | Release preparation                       |
| `test/`               | Test additions or corrections             |
| `ci/`                 | CI/CD pipeline changes                    |

> Both `feature/` and `feat/` are accepted as equivalent aliases for feature branches.

Examples:

```
feat/android-background-signaling
fix/null-pointer-on-incoming-call
chore/upgrade-pigeon
release/1.2.0
```

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[(scope)]: <lowercase description>
```

Accepted types: `feat`, `fix`, `chore`, `refactor`, `test`, `docs`, `style`, `ci`, `perf`, `build`, `revert`.

- Description must start with a **lowercase** letter.
- No Cyrillic characters anywhere in commit messages.
- Do not mention AI tools (Claude, Copilot, ChatGPT, etc.).

Examples:

```
feat(android): add SMS-triggered incoming call support
fix(ios): handle nil push token in PushRegistryDelegate
chore: upgrade pigeon to 26.0.3
```

## Git Hooks

Hooks are managed with [Lefthook](https://github.com/evilmartians/lefthook).

```bash
brew install lefthook
lefthook install
```

Hooks run automatically:

- **pre-commit** — `dart format` on staged Dart files (generated files excluded)
- **commit-msg** — validates commit message format and content
- **pre-push** — validates branch name, runs `flutter analyze` on all key packages

## Pull Requests

- Branch off from the correct base branch (not directly from `main`).
- Always `git pull` the base branch before creating your fix branch.
- After opening a PR, switch back to your base branch.
- PR title must follow the same Conventional Commits format as commit messages.
