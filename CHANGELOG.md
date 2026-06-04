# Changelog

All notable changes to this project are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

---

## [3.13.1] - 2026-06-04

### Fixed

- fix(ci): use paths inclusion for CodeQL to fix kdiab-analyze compilation (#1484)
- fix(ci): correct Detekt SARIF filename to detekt.sarif (#1480)
- fix(build): eliminate Kotlin deprecation warnings in nightscout and profiles (#1477)
- fix(skills): remove orphaned log headings and clean up create-pr post-merge step (#1489)

### Changed

- ci: add CodeQL backend and frontend analysis workflows (#1477, #1480, #1482, #1484)
- ci: upload Detekt SARIF to GitHub Security tab for all backend services (#1480)
- chore(skills): improve SDLC skill automation — docs story dispatch, auto-fix in pr-reviewer, post-merge cleanup in create-pr (#1489)
- chore(skills): remove audit skill and all audit/agent-log.jsonl logging from skills (#1489)
- build(deps): bump ui-npm-minor group in kdiab-ui (#1474)

### Removed

- Audit skill (`/audit`) and `audit/` directory removed — agent actions are logged to `~/.claude/kdiab-sessions/` instead (#1488)

---

<!-- Versions below are appended automatically by ReleaseAgent -->
