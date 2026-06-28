#!/usr/bin/env bash
#
# Fail if any non-GitHub-owned third-party GitHub Action is referenced by a
# mutable ref instead of a full 40-character commit SHA. A mutable tag (@main,
# @v4, …) lets a compromised or retagged upstream run in CI with whatever
# permissions the workflow grants — for an action that holds `pull-requests:
# write` or a secret, that is the supply-chain exposure this gate closes.
#
# Policy: GitHub-owned `actions/*` may use a major-version tag (vN[.N[.N]]) or a
# SHA, but NOT a mutable branch ref (@main, @master, …); every other owner
# (gradle/*, anthropics/*, …) must be pinned to a 40-hex commit SHA. `docker://`
# image refs must be pinned to an immutable `@sha256:<digest>`. Only local
# (`./…`) refs are out of scope.
#
# Scans BOTH workflow extensions GitHub executes (.yml and .yaml) AND local
# composite-action metadata under .github/actions/ (action.yml/action.yaml) —
# GitHub runs `uses:` steps inside a composite action too, so an unpinned
# third-party action there would otherwise execute with the job token unscanned.
# Runs in CI as the `actions-pin` required gate and is runnable by hand locally.
set -euo pipefail
shopt -s nullglob

scan_files=(.github/workflows/*.yml .github/workflows/*.yaml)

# Local composite-action metadata (action.yml/action.yaml at any depth under
# .github/actions/). Collected with `find` rather than a `**` glob so the gate
# still runs on bash 3.2 (macOS default), where `globstar` is unavailable.
if [ -d .github/actions ]; then
  while IFS= read -r -d '' meta; do
    scan_files+=("$meta")
  done < <(find .github/actions -type f \( -name action.yml -o -name action.yaml \) -print0)
fi

if [ ${#scan_files[@]} -eq 0 ]; then
  echo "No workflow or composite-action files found under .github/ — nothing to check."
  exit 0
fi

violations=0

for f in "${scan_files[@]}"; do
  # Pull the ref out of every `uses:` value, in any form GitHub Actions accepts for
  # a string scalar. A naive `grep '^...uses:...'` misses two YAML-valid shapes that
  # GitHub's own YAML parser still honours, both of which would let an unpinned
  # third-party action run while this gate saw nothing:
  #   1. whitespace before the key colon — `uses : owner/action@main`
  #   2. a block scalar — `uses: |` / `uses: >` with the ref on the next line(s)
  # So extract with a small YAML-aware awk instead of a line regex: it tolerates the
  # spaced colon, quoted keys, the list-item dash, and block-scalar values (taking
  # the first non-blank following line as the ref).
  #
  # RESIDUAL (deliberate, low risk): this is a best-effort lint, NOT a full YAML
  # parser. A few exotic-but-valid shapes are out of scope and would slip an
  # unpinned ref past this gate — flow-mapping steps (`- { uses: owner/action@x }`),
  # anchors/aliases, and merge keys. They are effectively never hand-written in real
  # workflows and none exist in this repo, and chasing each new shape with more awk
  # is a regex-vs-YAML treadmill. The durable fix, if this surface ever matters, is
  # to replace the awk with a real YAML parse (python3 is present in CI and locally)
  # and read every `uses:` value structurally. Tracked here rather than patched.
  while IFS= read -r ref; do
    [ -z "$ref" ] && continue
    case "$ref" in
      ./* | .\\*) continue ;; # local action
      docker://*)
        # A `uses: docker://…` step runs an arbitrary container in CI with the
        # job's token/permissions, so a mutable tag (docker://alpine:latest, a
        # moving GHCR tag) is the same supply-chain exposure as an unpinned
        # action. Require an immutable @sha256: digest instead of exempting it.
        if [[ ! "$ref" =~ @sha256:[0-9a-f]{64}$ ]]; then
          echo "::error file=$f::Unpinned Docker action '$ref' — pin the image to an immutable @sha256:<digest>."
          violations=$((violations + 1))
        fi
        continue
        ;;
      actions/*)
        # GitHub-owned: a major-version tag (v4, v4.1.2) or a 40-hex SHA is
        # allowed, but a mutable branch ref (@main, @master, an unversioned
        # `actions/foo`, …) is not — it would run unreviewed code under the job
        # token exactly like an unpinned third-party action. Restrict the
        # exemption to the shapes the policy actually intends to trust.
        gh_ref="${ref##*@}"
        if [[ ! "$gh_ref" =~ ^v[0-9]+(\.[0-9]+){0,2}$ && ! "$gh_ref" =~ ^[0-9a-f]{40}$ ]]; then
          echo "::error file=$f::Mutable GitHub-owned action ref '$ref' — pin to a version tag (vN[.N[.N]]) or a 40-char commit SHA."
          violations=$((violations + 1))
        fi
        continue
        ;;
    esac
    sha="${ref##*@}"
    if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
      echo "::error file=$f::Unpinned third-party action '$ref' — pin to a full 40-char commit SHA."
      violations=$((violations + 1))
    fi
  done < <(awk '
    # q holds a literal single quote so the program itself can stay single-quoted.
    BEGIN { q = sprintf("%c", 39); pending = 0 }
    function emit(v,   s) {
      s = v
      sub(/[[:space:]]*#.*$/, "", s)              # strip trailing comment
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)  # trim
      gsub("[\"" q "]", "", s)                    # strip surrounding quotes
      if (s != "") print s
    }
    # A `uses:` key, list-item or block form, with optional space before the
    # colon and optional surrounding quotes on the key (bare, double- or
    # single-quoted). Built as a dynamic regex so the quote class can include a
    # literal single quote via q (the awk program itself is single-quoted).
    match($0, "^[[:space:]]*-?[[:space:]]*[\"" q "]?uses[\"" q "]?[[:space:]]*:[[:space:]]*") {
      rest = substr($0, RLENGTH + 1)
      probe = rest
      sub(/[[:space:]]*#.*$/, "", probe)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", probe)
      # Empty value or a block-scalar header (|, >, with optional chomp/indent
      # indicators) means the ref is on a following line.
      if (probe == "" || probe ~ /^[|>][0-9+-]*$/) { pending = 1; next }
      emit(rest); next
    }
    pending {
      if ($0 ~ /^[[:space:]]*$/) next   # skip blank lines inside the block scalar
      emit($0); pending = 0
    }
  ' "$f")
done

if [ "$violations" -gt 0 ]; then
  echo "FAIL: ${violations} unpinned third-party action(s) found."
  exit 1
fi

echo "OK: all third-party actions are pinned to a commit SHA."
