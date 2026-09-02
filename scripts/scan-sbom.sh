#!/usr/bin/env bash
#
# Scan a CycloneDX SBOM with osv-scanner and FAIL CLOSED — a run that parsed
# nothing is a failure, not a pass.
#
# This is the single scan-and-assert path the `sca` gate uses. It takes the SBOM
# to scan as its one argument so the committed vulnerable fixture goes through
# exactly the same command, the same config, and the same assertions as the real
# generated bill of materials. If the two ever diverge, the fixture stops proving
# anything about the path that actually guards pull requests.
#
# Why an assertion on top of the exit code at all. osv-scanner reports "clean"
# and "read nothing" through the same channel unless you look closely, and the
# combination that silently passes is easy to reintroduce:
#
#   * `--allow-no-lockfiles` turns "no package sources found" into exit 0 with a
#     `{"results": null}` body. Measured against a components-less CycloneDX
#     document: WITH the flag the scan exits 0; without it, 128. The flag exists
#     so a repository with nothing scannable can still run the scanner — which is
#     precisely the state this gate must never be in, so it is not passed here.
#   * Without the flag the same document exits 128 and prints NO JSON at all
#     (stdout is zero bytes); a nonexistent path exits 127, also with no JSON.
#     Both are caught below and reported by name rather than surfacing as a bare
#     numeric exit or a downstream JSON parse error.
#
# The count assertions are the layer that holds even if `--allow-no-lockfiles` is
# ever restored: the report it produces for an empty document carries zero sources
# and zero packages, so both assertions fire regardless of the scanner's exit code.
# The source count also pins the target to a single file. The generator writes
# `bom.xml` beside `bom.json` with identical content, so pointing this at a
# DIRECTORY would extract both and double every count — an inflated number that
# still looks healthy. A directory is already rejected by the file check below;
# the source count is the second layer behind it.
#
# Exit codes are distinct on purpose, because the fixture check needs to tell
# "failed because the dependency is vulnerable" from "failed because the scan
# never happened":
#
#   0 — the SBOM parsed, packages were found, no advisory matched.
#   1 — the SBOM parsed, packages were found, and at least one advisory matched.
#   2 — fail-closed: the scan did not produce a trustworthy result.
#
# Usage: scripts/scan-sbom.sh <path-to-sbom.json>
set -euo pipefail

readonly EXIT_ADVISORY_FOUND=1
readonly EXIT_NOT_TRUSTWORTHY=2

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
config="${repo_root}/osv-scanner.toml"

fail_closed() {
  echo "::error::[scan-sbom] $*" >&2
  exit "$EXIT_NOT_TRUSTWORTHY"
}

if [ $# -ne 1 ]; then
  echo "usage: $0 <path-to-sbom.json>" >&2
  exit "$EXIT_NOT_TRUSTWORTHY"
fi
sbom="$1"

command -v osv-scanner >/dev/null 2>&1 || fail_closed "osv-scanner is not on PATH"
command -v jq >/dev/null 2>&1 || fail_closed "jq is not on PATH"
[ -f "$config" ] || fail_closed "config not found: $config"

# Checked before the scanner runs so a missing artifact reports as a missing
# artifact. Handed to osv-scanner instead, it becomes a bare exit 127 with no
# JSON, which reads in a CI log like any other scanner failure. Requiring a
# regular file also rejects a directory target here rather than downstream.
[ -f "$sbom" ] || fail_closed "SBOM not found: $sbom (was the generator run?)"

report="$(mktemp)"
scanner_stderr="$(mktemp)"
trap 'rm -f "$report" "$scanner_stderr"' EXIT

# --no-ignore because the real SBOM lives under the gitignored build tree, which
# osv-scanner would otherwise refuse to walk. --all-packages puts every parsed
# component in the report, which is what makes the count assertion below possible
# at all: without it the report carries only the vulnerable ones, and a clean scan
# is indistinguishable from a scan that read nothing.
set +e
osv-scanner scan source \
  --no-ignore \
  --config="$config" \
  --format json \
  --all-packages \
  "$sbom" >"$report" 2>"$scanner_stderr"
scanner_exit=$?
set -e

cat "$scanner_stderr" >&2

if [ "$scanner_exit" -ne 0 ] && [ "$scanner_exit" -ne "$EXIT_ADVISORY_FOUND" ]; then
  # 127 = path did not resolve; 128 = the file was read but yielded no package
  # source (an unrecognised filename, or a document with no components).
  fail_closed "osv-scanner exited $scanner_exit for $sbom — no trustworthy result was produced"
fi

jq -e . "$report" >/dev/null 2>&1 ||
  fail_closed "osv-scanner produced no parseable JSON report for $sbom"

source_count="$(jq '(.results // []) | length' "$report")"
package_count="$(jq '[(.results // [])[].packages[]] | length' "$report")"

[ "$source_count" -eq 1 ] ||
  fail_closed "expected exactly 1 scanned source, got $source_count — is the target a directory?"
[ "$package_count" -gt 0 ] ||
  fail_closed "the scan parsed 0 packages from $sbom — the SBOM carries no components"

echo "[scan-sbom] $sbom: 1 source, $package_count packages"

if [ "$scanner_exit" -eq "$EXIT_ADVISORY_FOUND" ]; then
  # The `[scan-sbom] advisory ` prefix is a contract, not decoration. osv-scanner
  # announces a suppression by printing the suppressed ID in prose ("GHSA-x has
  # been filtered out because: ..."), so any check that greps this stream for a
  # bare advisory ID matches the notice saying the advisory was IGNORED just as
  # readily as a real match. Anchoring on a prefix only this script emits is what
  # lets check-sbom-fixture.sh tell those two apart.
  jq -r '
    (.results // [])[].packages[]
    | select((.vulnerabilities // []) | length > 0)
    | "[scan-sbom] advisory \(.package.name)@\(.package.version) -> \([.vulnerabilities[].id] | join(", "))"
  ' "$report" >&2
  echo "::error::[scan-sbom] advisories matched in $sbom" >&2
  exit "$EXIT_ADVISORY_FOUND"
fi

echo "[scan-sbom] no advisories matched"
