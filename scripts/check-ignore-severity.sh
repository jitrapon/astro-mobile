#!/usr/bin/env bash
#
# Severity policy guard for osv-scanner.toml.
#
# The SBOM gate's threshold is `high`: a high-or-critical advisory against the
# resolved Gradle graph must fail the pull request. osv-scanner itself has no
# severity threshold — it fails on ANY advisory it matches — so the threshold is
# expressed the only way the scanner understands it, by ignoring the sub-`high`
# advisories that have no patched parent. That makes the ignore list the exact
# place the threshold can be dissolved: one more `[[IgnoredVulns]]` block and a
# critical advisory goes green, with no signal anywhere that the gate's promise
# just stopped holding. This check is what makes that a build failure instead of
# something a reviewer has to notice.
#
# Two suppression mechanisms are checked, because osv-scanner offers two and a
# guard over only the documented one is a guard with a one-line bypass:
#
#   * `[[IgnoredVulns]]` — suppresses one advisory by id. Each id is resolved
#     against the OSV API and rejected if it is rated HIGH or CRITICAL.
#   * `[[PackageOverrides]]` with `ignore = true` — suppresses EVERY advisory on
#     a package, present and future, and osv-scanner reports it as a filtered
#     package on a clean exit 0. There is no advisory id to rate, so there is
#     nothing to hold to the threshold: it is rejected outright. Measured against
#     the committed fixture, one such block drops a CRITICAL match and takes the
#     scan from exit 1 to exit 0. The nested `[PackageOverrides.vulnerability]`
#     spelling of that same switch suppresses just as completely and is rejected
#     identically; only `license.ignore` is exempt, since it governs license
#     findings and cannot hide an advisory.
#
# The config is parsed with a real TOML parser rather than scanned line by line,
# and that is a correctness requirement rather than a tidiness preference. A line
# scanner recognises one spelling of each construct; osv-scanner accepts every
# spelling TOML does. `id = 'GHSA-...'`, `"id" = "GHSA-..."`, and a nested
# `[PackageOverrides.vulnerability]` table are all read normally by the scanner
# but are invisible to patterns written for `id = "..."` and a bare
# `[[PackageOverrides]]` header — each was measured suppressing an advisory while
# an earlier line-scanning version of this check reported the file clean. Parsing
# the document means a new spelling cannot reopen that hole.
#
# FAIL CLOSED. Every way this check can fail to establish a severity — the API is
# unreachable, the id is unknown to OSV, the entry carries no qualitative rating —
# is an error, not a pass. A severity check that treats "could not tell" as "below
# the threshold" enforces nothing on precisely the entries that most need it.
#
# Why a script in the `sca` job rather than a Gradle task inside `check`, unlike
# checkNoDetektBaseline / verifyKtfmtAlignment / verifyCheckPartition: this reads
# the network, and `./gradlew check` is the local gate that must not depend on
# reaching an external API to pass. It guards a file no Gradle task consumes —
# osv-scanner.toml is read by scan-sbom.sh — and so belongs beside it, which is
# the same shape as check-action-pins.sh guarding the workflows' action pins.
#
# Usage: scripts/check-ignore-severity.sh
set -euo pipefail

# Qualitative ratings this gate exists to block. OSV carries GitHub's rating for
# GHSA entries, which every Maven-ecosystem advisory this repository can hit is.
readonly BLOCKED_SEVERITIES="HIGH CRITICAL"
readonly ALLOWED_SEVERITIES="LOW MODERATE MEDIUM NONE"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
config="${repo_root}/osv-scanner.toml"

violations=0

fail() {
  echo "::error::[check-ignore-severity] $*" >&2
  violations=$((violations + 1))
}

[ -f "$config" ] || {
  echo "::error::[check-ignore-severity] config not found: $config" >&2
  exit 1
}
command -v curl >/dev/null 2>&1 || {
  echo "::error::[check-ignore-severity] curl is not on PATH" >&2
  exit 1
}
command -v jq >/dev/null 2>&1 || {
  echo "::error::[check-ignore-severity] jq is not on PATH" >&2
  exit 1
}
command -v python3 >/dev/null 2>&1 || {
  echo "::error::[check-ignore-severity] python3 is not on PATH (needed to parse TOML)" >&2
  exit 1
}

# Extract one tagged record per suppression the config declares. Reading the
# parsed document rather than the file's lines is what makes the set of records
# complete: every TOML spelling of a construct collapses to the same structure
# here, so there is no syntax left for a suppression to hide in.
#
# Anything that prevents a confident reading — a document that will not parse, an
# entry that is not a table, an `id` that is not a string — is emitted as its own
# record and handled as a violation below, never skipped. A guard that quietly
# ignores what it could not understand is exactly the guard this one must not be.
entries="$(
  python3 - "$config" <<'PY' || echo "PARSE	the TOML parser itself failed"
import sys
import tomllib

def emit(kind, *fields):
    print("\t".join((kind,) + fields))

try:
    with open(sys.argv[1], "rb") as handle:
        document = tomllib.load(handle)
except (OSError, tomllib.TOMLDecodeError) as exc:
    emit("PARSE", str(exc).replace("\t", " "))
    raise SystemExit(0)

def blocks(key):
    """The array-of-tables under `key`, tolerating a lone table written directly."""
    value = document.get(key, [])
    return value if isinstance(value, list) else [value]

for block in blocks("IgnoredVulns"):
    if not isinstance(block, dict):
        emit("MALFORMED", "an IgnoredVulns entry is not a table")
        continue
    advisory = block.get("id")
    if isinstance(advisory, str) and advisory.strip():
        emit("IGNORE", advisory.strip())
    else:
        emit("MALFORMED", "an IgnoredVulns entry carries no string `id` to rate")

def suppressing_switches(table, trail=()):
    """Every `ignore = true` at any depth, except the license-only one.

    osv-scanner spells this switch both at the top of a PackageOverrides block and
    inside its `vulnerability` sub-table, and nothing stops a future release from
    adding a third place. Walking for the switch instead of looking in the two
    known spots keeps that from becoming a silent bypass. `license.ignore` is the
    one deliberate exemption: it filters license findings, not advisories.
    """
    for key, value in table.items():
        path = trail + (key,)
        if key == "ignore" and value is True:
            if trail[:1] != ("license",):
                yield ".".join(path)
        elif isinstance(value, dict):
            yield from suppressing_switches(value, path)

for block in blocks("PackageOverrides"):
    if not isinstance(block, dict):
        emit("MALFORMED", "a PackageOverrides entry is not a table")
        continue
    name = block.get("name")
    label = name if isinstance(name, str) and name else "(unnamed block)"
    for switch in suppressing_switches(block):
        emit("OVERRIDE", label, switch)
PY
)"

if [ -z "$entries" ]; then
  echo "[check-ignore-severity] osv-scanner.toml suppresses nothing — threshold intact."
  exit 0
fi

# Ask OSV for one advisory's qualitative rating. Prints the rating on stdout, or
# nothing when it cannot be established — every such case is handled as a failure
# by the caller rather than skipped.
osv_severity() {
  local id="$1" body
  body="$(curl -fsS --max-time 20 --retry 3 --retry-delay 2 \
    "https://api.osv.dev/v1/vulns/${id}" 2>/dev/null)" || return 1
  jq -re '.database_specific.severity // empty' <<<"$body" 2>/dev/null | tr '[:lower:]' '[:upper:]'
}

while IFS=$'\t' read -r kind value detail; do
  [ -n "$kind" ] || continue
  case "$kind" in
    PARSE)
      fail "osv-scanner.toml could not be parsed as TOML (${value}). Failing closed: a config" \
        "this check cannot read is a config whose suppressions it cannot rate — and osv-scanner" \
        "would reject it too, so the gate is broken either way."
      ;;
    MALFORMED)
      fail "osv-scanner.toml has a suppression this check cannot rate — ${value}. Failing closed:" \
        "an entry whose advisory id cannot be read is not known to be below the high threshold."
      ;;
    OVERRIDE)
      fail "[[PackageOverrides]] on '${value}' sets ${detail} = true, which suppresses every" \
        "advisory on that package, at any severity, now and in future. There is no advisory id" \
        "to hold to the gate's high threshold, so this form of suppression is not permitted" \
        "here — ignore a specific sub-high advisory by id under [[IgnoredVulns]] instead."
      ;;
    IGNORE)
      severity="$(osv_severity "$value")" || severity=""
      if [ -z "$severity" ]; then
        fail "could not establish a severity for ignored advisory ${value} — OSV returned no" \
          "rating for it, or was unreachable. Failing closed: an advisory whose severity cannot" \
          "be checked is not known to be below this gate's high threshold."
      elif [[ " $BLOCKED_SEVERITIES " == *" $severity "* ]]; then
        fail "ignored advisory ${value} is rated ${severity}. The SBOM gate exists to fail a" \
          "pull request on a high-or-worse advisory; ignoring one dissolves that threshold." \
          "Remediate the dependency — bump the parent, or drop it — rather than ignoring it."
      elif [[ " $ALLOWED_SEVERITIES " == *" $severity "* ]]; then
        echo "[check-ignore-severity] ${value}: ${severity} — below the high threshold, allowed."
      else
        fail "ignored advisory ${value} carries an unrecognised severity rating '${severity}'." \
          "Failing closed rather than assuming it is below the high threshold; teach this check" \
          "the new rating if OSV has introduced one."
      fi
      ;;
  esac
done <<<"$entries"

if [ "$violations" -gt 0 ]; then
  echo "::error::[check-ignore-severity] ${violations} severity-policy violation(s) in" \
    "osv-scanner.toml" >&2
  exit 1
fi

echo "[check-ignore-severity] every suppression in osv-scanner.toml is below the high threshold."
