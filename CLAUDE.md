# Repository conventions

This repo hosts multiple independent Hubitat integrations, each living in
its own top-level directory (one app/driver pair, or a single driver, plus
`packageManifest.json` and `README.md`). Root `repository.json` is the
Hubitat Package Manager (HPM) index listing every released package.

## Releasing an integration to `master`

Development happens on a feature/fix branch. A package only becomes
installable via HPM once its `packageManifest.json` resolves on `master`
and (for a first release) it's listed in the root `repository.json`. When
opening a PR that releases an integration's current branch state to
`master`, do all three of the following in that same PR:

### 1. Point every `.groovy` file's `importUrl` at `master`

During development, `importUrl` in each driver/app's
`metadata { definition { ... } }` block points at the feature branch (e.g.
`.../refs/heads/<branch-name>/<Dir>/<File>.groovy`) so it can be imported
for testing before merge. Before merging, change it to point at `master`
instead (`.../master/<Dir>/<File>.groovy`, or the `refs/heads/master/...`
form — match whichever style the file already used). Remove any
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
