## Summary

<!-- What does this PR change? A sentence or two, or a few bullets. -->

## Release checklist

<!--
  Only applies to a PR that releases an integration's current branch state
  to master (i.e. makes a new version installable via HPM). Delete this
  whole section for a PR that isn't a release — a WIP branch update, a
  docs-only fix, etc. Full detail on each item: root CLAUDE.md, "Releasing
  an integration to master."
-->

- [ ] Every changed `.groovy` file's `importUrl` points at `master`, not a feature branch
- [ ] `<Dir>/packageManifest.json` updated: `version`, `dateReleased`, `releaseNotes`, and every component's `location`/`version` reflect this release
- [ ] Root `repository.json` includes this integration (first release only)
- [ ] `<Dir>/README.md` reflects current behavior; `communityLink` (if set) is linked near the top and at the bottom
- [ ] Every changed file compiles and passes the Hubitat sandbox-restriction sweep

## Test plan

<!-- How was this verified — manual testing on real hardware, compile checks, simulations? -->
