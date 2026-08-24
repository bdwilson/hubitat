# Repository conventions

This repo hosts multiple independent Hubitat integrations, each living in
its own top-level directory (one app/driver pair, or a single driver, plus
`packageManifest.json` and `README.md`). Root `repository.json` is the
Hubitat Package Manager (HPM) index listing every released package.

## `importUrl` must always match the branch a file actually lives on

This is a standing rule, active on every commit — not just something to fix
right before a release. Every driver/app `.groovy` file's `importUrl` (in
its `metadata { definition { ... } }` block) must point at wherever *that
exact file* currently lives:

- **Committing a brand-new `.groovy` file** to a branch (a new driver, a new
  app, or a copy of one under a new name): give it an `importUrl` pointing
  at **that same branch** from the start —
  `.../refs/heads/<branch-name>/<Dir>/<File>.groovy` — never `master`, even
  if the plan is to release it soon. Do this in the same commit that adds
  the file, without being asked.
- **Committing further changes to an existing file already on a branch**:
  leave `importUrl` as-is if it already points at that branch (it should,
  assuming this rule was followed when the file first landed there).
- **A file's `importUrl` doesn't match the branch it's actually on** (stale
  from an earlier branch, or still pointing at `master` while real
  development is happening elsewhere): fix it as part of whatever commit
  you're already making to that file — don't leave it stale, don't treat it
  as a separate cleanup task for later.
- On `master` itself, `importUrl` points at `master`. That's the one place
  the "matches the branch it lives on" rule and "points at master" happen to
  be the same statement — it isn't a special case, it falls out of the rule
  above naturally.

The practical effect: at any point in time, reading any `.groovy` file's
`importUrl` tells you exactly which branch it currently lives on — never
early, never stale, never a leftover from a previous branch.

## Releasing an integration to `master`

Development happens on a feature/fix branch. A package only becomes
installable via HPM once its `packageManifest.json` resolves on `master`
and (for a first release) it's listed in the root `repository.json`. When
opening a PR that releases an integration's current branch state to
`master`, do all three of the following in that same PR:

### 1. Flip every `.groovy` file's `importUrl` to `master`

This is the one moment `importUrl` changes to something other than the
current branch's own location — because the file is *becoming* part of
`master`. Per the rule above, this happens exactly once, as one of the
changes *inside* the PR that actually merges this branch to `master` (never
early, never as its own separate PR): change each file's `importUrl` to
`.../master/<Dir>/<File>.groovy` (or the `refs/heads/master/...` form —
match whichever style the file already used), and remove any
`// TODO: point back at master once merged`-style comment left as a
reminder during development.

### 2. Update (or create) `<Dir>/packageManifest.json`, and `repository.json` if needed

In `<Dir>/packageManifest.json`:

- Bump `version` to the version being released, set `dateReleased` to
  today, and write a concise `releaseNotes` entry summarizing what actually
  changed since the last released version (not a full changelog dump).
- Point every driver's/app's `location` at `master`.
- Bump each driver's/app's own `version` field to match its current
  in-file header comment version.
- If this is the integration's **first** release, also add/verify:
  - `packageName`, `author`, `minimumHEVersion`
  - `documentationLink` → `https://github.com/bdwilson/hubitat/tree/master/<Dir>`
  - `communityLink` → the Hubitat Community forum release thread, if one
    exists yet (leave `""` if not published yet — fill in on a later release
    once it is)
  - a stable random UUID `id` for each driver/app entry — generate it once,
    never change it on later releases (HPM uses it to track the package)

Then, **only if this integration isn't already listed** in the root
`repository.json`, add an entry under `packages`: `name`, `category`,
`tags`, `location` (pointing at `<Dir>/packageManifest.json` on `master`),
and `description`. Don't touch other integrations' existing entries.

### 3. Update (or create) `<Dir>/README.md`

Bring it up to date with the integration's **current** state — install
steps, current feature set, known limitations, troubleshooting entries for
real bugs that were actually fixed. It's a living reference, not a
changelog: don't just append to it, make sure what it says matches what the
code does today.

If `<Dir>/packageManifest.json` has a non-empty `communityLink`, link it in
the README in **two places** so users can find support either way they
land on the page:
- near the top, right under the title (e.g. `Support / discussion: <link>`)
- at the bottom, under a "Support" or "Credits"-type heading

### Before opening the PR

Compile-check every changed `.groovy` file and sweep it for Hubitat sandbox
restrictions — `System.`, `Arrays.`, `Runtime.`, `Class.forName`,
`ProcessBuilder`, `reflect.`, and a safe-nav-then-subscript (`?[` with no
dot, which Groovy misparses) are all rejected by Hubitat's sandbox even
though they compile fine locally. A minimal local compile check:

```bash
groovy -e "new GroovyShell().parse(new File('<Dir>/<File>.groovy')); println 'OK'"
```

(Apps that reference `groovyx.net.http.HttpResponseException` need a tiny
local stub class on the classpath to parse standalone — see any recently
released integration's development history for the pattern if needed.)
