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
#     scan from exit 1 to exit 0.
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

# Minimal TOML walk: track the current table header and emit one tagged record per
# suppression found. A `grep` for `^id = ` instead would also match an id declared
# under some other table, and would miss a PackageOverrides block entirely.
# PackageOverrides fields are buffered and flushed at the next header so `ignore`
# and `name` are reported together whichever order they appear in.
entries="$(
  awk '
    function flush() {
      if (table == "[[PackageOverrides]]" && po_ignore)
        printf "OVERRIDE\t%s\n", (po_name == "" ? "(unnamed block)" : po_name)
      po_ignore = 0; po_name = ""
    }
    {
      line = $0
      gsub(/^[ \t]+|[ \t]+$/, "", line)
      if (line == "" || line ~ /^#/) next
      if (line ~ /^\[/) {
        flush()
        match(line, /^\[\[?[A-Za-z_][A-Za-z_0-9]*\]\]?/)
        table = substr(line, RSTART, RLENGTH)
        next
      }
      if (table == "[[IgnoredVulns]]" && line ~ /^id[ \t]*=/) {
        if (match(line, /"[^"]*"/)) printf "IGNORE\t%s\n", substr(line, RSTART + 1, RLENGTH - 2)
      }
      if (table == "[[PackageOverrides]]") {
        if (line ~ /^name[ \t]*=/ && match(line, /"[^"]*"/))
          po_name = substr(line, RSTART + 1, RLENGTH - 2)
        if (line ~ /^ignore[ \t]*=[ \t]*true([ \t]|#|$)/) po_ignore = 1
      }
    }
    END { flush() }
  ' "$config"
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

while IFS=$'\t' read -r kind value; do
  [ -n "$kind" ] || continue
  case "$kind" in
    OVERRIDE)
      fail "[[PackageOverrides]] with ignore = true on '${value}' suppresses every advisory" \
        "on that package, at any severity, now and in future. There is no advisory id to hold" \
        "to the gate's high threshold, so this form of suppression is not permitted here —" \
        "ignore a specific sub-high advisory by id under [[IgnoredVulns]] instead."
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
