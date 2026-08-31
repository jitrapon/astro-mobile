#!/usr/bin/env bash
#
# Regression guard for the SBOM scan path: run `scan-sbom.sh` against a committed
# SBOM that is KNOWN to carry a critical advisory and require it to fail for that
# reason.
#
# Why this exists. The real dependency graph is clean today, so every scan of it
# passes — and it would keep passing if a later edit stopped invoking the scanner,
# pointed it at the wrong artifact, dropped the config, or generated an empty bill
# of materials. A gate whose only observed outcome is "green" is not known to gate
# anything. This is the same posture as the repo's `semgrep --test` fixture: prove
# the detector still detects, on an input whose answer is fixed.
#
# The fixture goes through `scan-sbom.sh` — the identical command, config, and
# assertions as the real generated SBOM — rather than calling osv-scanner directly.
# A second, parallel invocation could drift from the one that actually guards pull
# requests, and would then prove nothing about it.
#
# Two assertions, and both are load-bearing:
#
#   1. The exit code is exactly 1 (an advisory matched), never 2 (the scan did not
#      produce a trustworthy result). Without the distinction, a fixture check that
#      accepted "any non-zero exit" would go green when the scanner is missing, the
#      fixture file has been deleted, or its name stopped being recognised — every
#      way the real gate silently stops working also makes the fixture "fail", so
#      the guard would confirm exactly the breakage it exists to catch.
#
#   2. The pinned advisory appears in the MATCHED set. That is what makes the ignore
#      list unable to suppress the fixture: adding the pinned ID to `[[IgnoredVulns]]`
#      in osv-scanner.toml drops it from the matched set, and this assertion fails
#      even though the sibling advisories on the same component keep the exit code
#      at 1. The assertion anchors on the `[scan-sbom] advisory ` prefix rather than
#      searching the output for the bare ID, because osv-scanner announces a
#      suppression by printing the suppressed ID in prose — grepping the raw stream
#      for the ID therefore matches the notice saying it was IGNORED, and the guard
#      passes in exactly the case it exists to catch.
#
# The fixture pins Log4Shell on log4j-core 2.14.1: a Maven coordinate this repo does
# not depend on, at a version whose advisory is permanent and will not be withdrawn
# or re-rated out from under the assertion.
#
# Usage: scripts/check-sbom-fixture.sh
set -euo pipefail

readonly EXPECTED_EXIT=1
readonly EXPECTED_ADVISORY=GHSA-jfh8-c2jp-5v3q

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="${repo_root}/scripts/fixtures/vulnerable-sbom.cdx.json"

fail() {
  echo "::error::[check-sbom-fixture] $*" >&2
  exit 1
}

[ -f "$fixture" ] || fail "fixture not found: $fixture"

output="$(mktemp)"
trap 'rm -f "$output"' EXIT

set +e
"${repo_root}/scripts/scan-sbom.sh" "$fixture" >"$output" 2>&1
actual_exit=$?
set -e

cat "$output"

if [ "$actual_exit" -ne "$EXPECTED_EXIT" ]; then
  fail "expected exit $EXPECTED_EXIT (advisory matched) from the fixture scan, got $actual_exit —" \
    "the scan path is no longer reporting a known-vulnerable SBOM as vulnerable"
fi

grep -q "^\[scan-sbom\] advisory .*${EXPECTED_ADVISORY}" "$output" ||
  fail "the fixture scan failed, but $EXPECTED_ADVISORY was not among the matched advisories" \
    "— is it suppressed by an [[IgnoredVulns]] entry in osv-scanner.toml?"

echo "[check-sbom-fixture] fixture failed as expected on $EXPECTED_ADVISORY"
