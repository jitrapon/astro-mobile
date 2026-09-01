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
parser_status=0
entries="$(
  python3 - "$config" <<'PY'
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
)" || parser_status=$?

# The parser's own exit status is checked rather than inferred from its output. It
# reports a config it could read but disliked as a PARSE record; a non-zero status
# means it did not run at all, and the overwhelmingly likely cause is a python3
# older than 3.11, which has no `tomllib`. Reading "no records" as "nothing is
# suppressed" would turn exactly that into a silent pass.
if [ "$parser_status" -ne 0 ]; then
  echo "::error::[check-ignore-severity] the TOML parser did not run (python3 exited" \
    "${parser_status}). This check needs python3 3.11 or newer, which is where \`tomllib\`" \
    "was added. Failing closed: a config whose suppressions were never read is not a config" \
    "known to be free of high-or-critical ignores." >&2
  exit 1
fi

if [ -z "$entries" ]; then
  echo "[check-ignore-severity] osv-scanner.toml suppresses nothing — threshold intact."
  exit 0
fi

# Fetch one advisory from OSV and flatten the two facts this check needs into
# lines: `RATING <severity>` (zero or one) and `ALIAS <id>` (zero or more). One
# request yields both, so rating an entry and rating its aliases does not fetch the
# same record twice. Output is piped rather than passed through a here-string:
# here-strings are backed by a temporary file, which is the redirection this script
# deliberately avoids everywhere a missing record could be read as a clean result.
osv_record() {
  local id="$1" body
  body="$(curl -fsS --max-time 20 --retry 3 --retry-delay 2 \
    "https://api.osv.dev/v1/vulns/${id}" 2>/dev/null)" || return 1
  printf '%s' "$body" | jq -r '
    (.database_specific.severity // empty | ascii_upcase | "RATING " + .),
    (.aliases // [] | .[] | "ALIAS " + .)
  ' 2>/dev/null
}

# Read the first `RATING` line out of an osv_record body, or nothing.
rating_of() {
  local line
  line="$(printf '%s\n' "$1" | sed -n 's/^RATING //p' | head -1)" || line=""
  printf '%s' "$line"
}

# osv-scanner suppresses an ignored advisory's ALIASES along with the id named in
# the entry — it says so in its own output ("<id> and N alias(es) have been
# filtered out"). Rating only the named id would therefore let the lower-rated half
# of an alias pair stand in for the higher-rated half: ignore the MODERATE id, and
# the HIGH advisory it aliases is suppressed too, with nothing here objecting.
#
# An alias is rated where OSV gives it a qualitative rating and skipped where it
# does not. That is deliberately asymmetric with the named id, whose missing rating
# fails closed, and the asymmetry is load-bearing rather than lax:
# `database_specific.severity` is a GitHub field, so a GHSA carries one and the CVE
# records GHSAs alias generally do not. Failing closed on an unrated alias would
# reject ordinary correct entries — measured against the committed entry, whose CVE
# alias is unrated — while catching nothing a rated alias would not already catch.
# The residual gap is an alias whose only rating is a CVSS vector; closing that
# needs base-score arithmetic this check deliberately does not carry.
rate_aliases() {
  local id="$1" body="$2" alias alias_severity saved
  saved=$IFS
  IFS=$'\n'
  for alias in $(printf '%s\n' "$body" | sed -n 's/^ALIAS //p'); do
    IFS=$saved
    # Advisory ids are alphanumerics, dots, dashes and underscores. Anything else
    # is not an id this check can look up, and leaving it unquoted would expose it
    # to pathname expansion.
    case "$alias" in ''|*[!A-Za-z0-9._-]*) IFS=$'\n'; continue ;; esac
    alias_severity="$(rating_of "$(osv_record "$alias" || true)")" || alias_severity=""
    if [ -n "$alias_severity" ] && [[ " $BLOCKED_SEVERITIES " == *" $alias_severity "* ]]; then
      fail "ignored advisory ${id} aliases ${alias}, which OSV rates ${alias_severity}." \
        "osv-scanner suppresses an ignored advisory's aliases too, so this entry would hide a" \
        "high-or-worse advisory behind a lower-rated id. Remediate the dependency — bump the" \
        "parent, or drop it — rather than ignoring it."
    fi
    IFS=$'\n'
  done
  IFS=$saved
}

# Consume the records with parameter expansion rather than `while read` fed by a
# here-string. That idiom is backed by a temporary file, so on a host where the
# temporary file cannot be created the loop body simply never runs — `violations`
# stays at zero and this check prints success having rated nothing. Splitting on
# newlines and slicing the fields needs no temporary file and no subshell, so the
# records cannot be dropped between the parser and the policy below.
records_seen=0
records_rated=0
saved_ifs=$IFS
IFS=$'\n'
for record in $entries; do
  IFS=$saved_ifs
  records_seen=$((records_seen + 1))
  kind=${record%%$'\t'*}
  rest=${record#*$'\t'}
  value=${rest%%$'\t'*}
  detail=${rest#*$'\t'}
  [ "$detail" = "$value" ] && detail=""
  if [ -z "$kind" ]; then IFS=$'\n'; continue; fi
  records_rated=$((records_rated + 1))
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
      osv_body="$(osv_record "$value")" || osv_body=""
      severity="$(rating_of "$osv_body")" || severity=""
      if [ -z "$severity" ]; then
        fail "could not establish a severity for ignored advisory ${value} — OSV returned no" \
          "rating for it, or was unreachable. Failing closed: an advisory whose severity cannot" \
          "be checked is not known to be below this gate's high threshold."
      elif [[ " $BLOCKED_SEVERITIES " == *" $severity "* ]]; then
        fail "ignored advisory ${value} is rated ${severity}. The SBOM gate exists to fail a" \
          "pull request on a high-or-worse advisory; ignoring one dissolves that threshold." \
          "Remediate the dependency — bump the parent, or drop it — rather than ignoring it."
      elif [[ " $ALLOWED_SEVERITIES " == *" $severity "* ]]; then
        rate_aliases "$value" "$osv_body"
        echo "[check-ignore-severity] ${value}: ${severity} — below the high threshold, allowed."
      else
        fail "ignored advisory ${value} carries an unrecognised severity rating '${severity}'." \
          "Failing closed rather than assuming it is below the high threshold; teach this check" \
          "the new rating if OSV has introduced one."
      fi
      ;;
  esac
  IFS=$'\n'
done
IFS=$saved_ifs

# The backstop for the whole class: every record the parser emitted must have
# reached the policy above. This holds whatever caused a shortfall — a skipped
# loop, a counter lost to a subshell, a record the field split could not classify —
# rather than guarding the one mechanism known to cause it today.
if [ "$records_rated" -ne "$records_seen" ] || [ "$records_seen" -eq 0 ]; then
  echo "::error::[check-ignore-severity] parsed records were not all rated" \
    "(${records_rated} of ${records_seen}). Failing closed: this check cannot claim the" \
    "ignore list is below the threshold when it did not read every suppression in it." >&2
  exit 1
fi

if [ "$violations" -gt 0 ]; then
  echo "::error::[check-ignore-severity] ${violations} severity-policy violation(s) in" \
    "osv-scanner.toml" >&2
  exit 1
fi

echo "[check-ignore-severity] every suppression in osv-scanner.toml is below the high threshold."
