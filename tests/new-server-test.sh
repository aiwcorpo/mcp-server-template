#!/usr/bin/env bash
# Checks new-server.sh without building: refusals, where the output lands, the trial flag, and that the template
# stays untouched. tests/new-server-test.sh [--build] (--build also runs ./mvnw verify in a generated jira).
set -euo pipefail

template="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

check() {
    local name="$1"
    shift
    if "$@"; then
        echo "ok   $name"
    else
        echo "FAIL $name"
        failures=$((failures + 1))
    fi
}

refused() {
    ! "$template/new-server.sh" "$@" >/dev/null 2>&1
}

before="$(git -C "$template" status --porcelain --untracked-files=all)"

check "unknown system without --trial" refused gitlb "$work/a"
check "shell metacharacters" refused 'x;rm' "$work/b"
check "path in the name" refused ../evil "$work/c"
check "upper case" refused Jira "$work/d"
check "starter package name" refused --trial platform "$work/e"
check "java keyword" refused --trial new "$work/f"
check "placeholder itself" refused --trial example "$work/g"
mkdir "$work/exists"
check "existing target" refused jira "$work/exists"
check "target inside the template" refused jira "$template/inner"
check "nothing left after refusals" test -z "$(ls "$work" | grep -v '^exists$')"

(cd "$work" && "$template/new-server.sh" jira relative-jira >/dev/null)
check "relative target lands in the caller's directory" test -f "$work/relative-jira/pom.xml"
check "relative target used twice is refused" bash -c "cd '$work' && ! '$template/new-server.sh' confluence relative-jira >/dev/null 2>&1"
check "real system: TRIAL_SYSTEM false" grep -q 'TRIAL_SYSTEM = false' \
    "$work/relative-jira/mcp-app/src/test/java/pl/aiwcorpo/mcp/jira/app/ServerTestSupport.java"
check "no template-only files" test ! -e "$work/relative-jira/new-server.sh" -a ! -e "$work/relative-jira/STATUS.md" \
    -a ! -e "$work/relative-jira/tests/new-server-test.sh" -a ! -e "$work/relative-jira/docs/nowy-serwer.md"

"$template/new-server.sh" --trial demo "$work/demo" >/dev/null
check "trial system: TRIAL_SYSTEM true" grep -q 'TRIAL_SYSTEM = true' \
    "$work/demo/mcp-app/src/test/java/pl/aiwcorpo/mcp/demo/app/ServerTestSupport.java"
check "no placeholder left" bash -c "! grep -rIil example '$work/demo'"

check "template unchanged" test "$before" == "$(git -C "$template" status --porcelain --untracked-files=all)"

if [[ "${1:-}" == "--build" ]]; then
    check "generated jira builds and passes its tests" bash -c "cd '$work/relative-jira' && git init -q && ./mvnw -B -q verify >'$work/build.log' 2>&1"
fi

echo "failures: $failures"
[[ $failures -eq 0 ]]
