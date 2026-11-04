#!/usr/bin/env bash
# Checks new-server.sh without building: refusals, where the output lands, the trial flag, and that the template
# stays untouched. tests/new-server-test.sh [--build] (--build also runs ./mvnw verify in every generated profile).
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
    -a ! -e "$work/relative-jira/tests/new-server-test.sh"

for ci_file in ci/run.sh Makefile .gitlab-ci.yml Jenkinsfile .github/workflows/ci.yml; do
    check "generated server has $ci_file" test -s "$work/relative-jira/$ci_file"
done
check "generated server has no registry settings" bash -c "test ! -e '$work/relative-jira/.ci' && ! grep -rIl 'MCP_MAVEN_REPOSITORY\\|maven.pkg.github.com\\|packages: read' '$work/relative-jira' --exclude-dir=target"
check "generated Dockerfile keeps the source and licenses labels" bash -c "grep -q 'image.source=\"https://github.com/aiwcorpo/mcp-server-jira\"' '$work/relative-jira/Dockerfile' && grep -q 'image.licenses=\"Apache-2.0\"' '$work/relative-jira/Dockerfile'"
check "pull request job: contents read only, no env" bash -c "awk '/verify-pull-request:/{f=1} /verify-main:/{f=0} f' '$work/relative-jira/.github/workflows/ci.yml' | grep -q 'contents: read' && ! awk '/verify-pull-request:/{f=1} /verify-main:/{f=0} f' '$work/relative-jira/.github/workflows/ci.yml' | grep -q 'env:\\|secrets\\.'"
check "ci/run.sh is executable and names the generated image" bash -c "test -x '$work/relative-jira/ci/run.sh' && grep -q 'mcp-server-jira:' '$work/relative-jira/ci/run.sh' && grep -q 'mcp-jira.jar' '$work/relative-jira/ci/run.sh'"
check "ci/run.sh refuses an unknown step" bash -c "cd '$work/relative-jira' && ci/run.sh bogus >/dev/null 2>&1; test \$? -eq 2"
for release_file in LICENSE NOTICE SECURITY.md CONTRIBUTING.md CHANGELOG.md .github/workflows/release.yml; do
    check "generated server has $release_file" test -s "$work/relative-jira/$release_file"
done
check "generated README is its own, not the template's" bash -c "! cmp -s '$template/README.md' '$work/relative-jira/README.md' && grep -q '^# mcp-jira\$' '$work/relative-jira/README.md' && ! grep -qi 'new-server.sh\\|Make a new server\\|mcp-example' '$work/relative-jira/README.md'"
check "generated README names the jar and the manifest" bash -c "grep -q 'mcp-app/target/mcp-jira.jar' '$work/relative-jira/README.md' && grep -q 'mcp-manifest.json' '$work/relative-jira/README.md'"
check "generated README links the developer guide of mcp-docs" bash -c "grep -q 'mcp-docs/blob/main/docs/developer/new-server.md' '$work/relative-jira/README.md' && grep -q 'mcp-docs/blob/main/docs/developer/tools.md' '$work/relative-jira/README.md'"
check "generated server has no docs directory of the template" test ! -e "$work/relative-jira/docs"
check "generated CHANGELOG is its own and in English" bash -c "grep -q '\`jira\`' '$work/relative-jira/CHANGELOG.md' && grep -q '^## Unreleased\$' '$work/relative-jira/CHANGELOG.md' && grep -q 'Versions follow SemVer' '$work/relative-jira/CHANGELOG.md' && ! grep -qi 'sonda wersji\|Niewydane\|wersje SemVer' '$work/relative-jira/CHANGELOG.md' && ! LC_ALL=C grep -q '[^ -~]' '$work/relative-jira/CHANGELOG.md'"
for pom in pom.xml mcp-core/pom.xml mcp-application/pom.xml mcp-integration/pom.xml mcp-tools/pom.xml mcp-app/pom.xml; do
    check "generated $pom starts at 0.1.0-SNAPSHOT" bash -c "test \$(grep -c '<version>0.1.0-SNAPSHOT</version>' '$work/relative-jira/$pom') -eq 1 && ! grep -q '<version>1.0.0</version>' '$work/relative-jira/$pom'"
done
check "generated server keeps the template's starter version" bash -c "grep -q '<mcp-platform.version>3.0.0</mcp-platform.version>' '$work/relative-jira/pom.xml'"
check "every action in the workflows is pinned to a commit SHA" bash -c "! grep -hE '^\s*-?\s*uses:' '$work'/relative-jira/.github/workflows/*.yml | grep -vE 'uses: [^@ ]+@[0-9a-f]{40}\s*(#.*)?\$'"
check "every image of the CI is pinned to a digest" bash -c "! grep -hE 'image:? .?(maven|docker):' '$work'/relative-jira/.github/workflows/*.yml '$work'/relative-jira/.gitlab-ci.yml '$work'/relative-jira/Jenkinsfile | grep -v '@sha256:'"
check "the pom carries the licence" grep -q 'Apache License, Version 2.0' "$work/relative-jira/pom.xml"
check "jira keeps the template's resource model" grep -q 'Jira::Issue' \
    "$work/relative-jira/mcp-core/src/main/java/pl/aiwcorpo/mcp/jira/core/domain/JiraResourceModel.java"
check "no profiles directory in a generated server" test ! -e "$work/relative-jira/profiles"
"$template/new-server.sh" gitlab "$work/gitlab" >/dev/null
check "gitlab gets its profile" grep -q 'GitLab::Project' \
    "$work/gitlab/mcp-core/src/main/java/pl/aiwcorpo/mcp/gitlab/core/domain/GitlabResourceModel.java"
check "gitlab sends ancestorClassifications" grep -q ancestorClassifications \
    "$work/gitlab/mcp-integration/src/main/java/pl/aiwcorpo/mcp/gitlab/integration/GitlabResourceDescription.java"
for system in confluence servicedesk opensearch; do
    "$template/new-server.sh" "$system" "$work/$system" >/dev/null
    check "$system gets its profile" test -n "$(grep -rl "$(case $system in confluence) echo Confluence::Page;; servicedesk) echo ServiceDesk::Ticket;; opensearch) echo OpenSearch::Index;; esac)" "$work/$system/mcp-core")"
done
"$template/new-server.sh" --trial demo "$work/demo" >/dev/null
check "trial system keeps the template's Jira model" grep -q 'Jira::Issue' \
    "$work/demo/mcp-core/src/main/java/pl/aiwcorpo/mcp/demo/core/domain/DemoResourceModel.java"
check "trial system: TRIAL_SYSTEM true" grep -q 'TRIAL_SYSTEM = true' \
    "$work/demo/mcp-app/src/test/java/pl/aiwcorpo/mcp/demo/app/ServerTestSupport.java"
check "trial README says dev profile only and promises no manifest" bash -c "grep -q 'dev. profile only' '$work/demo/README.md' && ! grep -q 'mcp-manifest.json\$' '$work/demo/README.md' && grep -q '^# mcp-demo\$' '$work/demo/README.md'"
check "no placeholder left" bash -c "! grep -rIil example '$work/demo'"

check "template unchanged" test "$before" == "$(git -C "$template" status --porcelain --untracked-files=all)"

if [[ "${1:-}" == "--build" ]]; then
    for generated in relative-jira gitlab confluence servicedesk opensearch demo; do
        check "generated $generated builds and passes ci/run.sh verify (tests, floors, manifest)" bash -c "cd '$work/$generated' && git init -q && ci/run.sh verify >'$work/build-$generated.log' 2>&1"
        check "generated $generated passes ci/run.sh hygiene" bash -c "cd '$work/$generated' && ci/run.sh hygiene >/dev/null 2>&1"
        if [[ "$generated" == "demo" ]]; then
            check "trial server writes no manifest" test ! -e "$work/$generated/mcp-app/target/mcp-manifest.json"
        else
            check "generated $generated writes the manifest in the build" test -s "$work/$generated/mcp-app/target/mcp-manifest.json"
        fi
    done
fi

echo "failures: $failures"
[[ $failures -eq 0 ]]
