#!/usr/bin/env bash
# Checks new-server.sh without building: refusals, where the output lands, what is renamed (code, policies, deploy), and
# that the template stays untouched. tests/new-server-test.sh [--build] (--build also runs ci/run.sh verify in the
# generated servers).
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

check "upper case" refused Notes "$work/a"
check "shell metacharacters" refused 'x;rm' "$work/b"
check "path in the name" refused ../evil "$work/c"
check "one character" refused n "$work/d"
check "id reserved by the contracts" refused gateway "$work/e"
check "starter package name" refused platform "$work/f"
check "java keyword" refused new "$work/g"
check "placeholder itself" refused example "$work/h"
mkdir "$work/exists"
check "existing target" refused notes "$work/exists"
check "target inside the template" refused notes "$template/inner"
check "nothing left after refusals" test -z "$(ls "$work" | grep -v '^exists$')"

(cd "$work" && "$template/new-server.sh" notes relative-notes >/dev/null)
server="$work/relative-notes"
check "relative target lands in the caller's directory" test -f "$server/pom.xml"
check "relative target used twice is refused" bash -c "cd '$work' && ! '$template/new-server.sh' tickets relative-notes >/dev/null 2>&1"
check "no template-only files" test ! -e "$server/new-server.sh" -a ! -e "$server/STATUS.md" -a ! -e "$server/tests/new-server-test.sh"
check "no profiles directory" test ! -e "$server/profiles"
check "LICENSE is copied unchanged" cmp -s "$template/LICENSE" "$server/LICENSE"

policies="$server/mcp-app/src/main/resources/mcp/policies"
check "policy files are copied" test -s "$policies/schema.cedarschema" -a -s "$policies/resource-types.json" -a -s "$policies/read.cedar" -a -s "$policies/update.cedar"
check "Cedar namespace is renamed in the schema and the declarations" bash -c "grep -q '^namespace Notes {' '$policies/schema.cedarschema' && grep -q '\"Notes::Record\"' '$policies/resource-types.json' && ! grep -q 'Example' '$policies'/*"
check "groups and ids of the policies are renamed" bash -c "grep -q 'UserGroup::\"mcp-notes-read\"' '$policies/read.cedar' && grep -q 'Notes::Action::\"update\"' '$policies/update.cedar' && grep -q '@id(\"notes-read\")' '$policies/read.cedar'"
check "policy test is renamed" test -f "$server/mcp-app/src/test/java/pl/aiwcorpo/mcp/notes/app/NotesPolicyTest.java"
check "the settings use the policy data of the gateway" bash -c "grep -q 'MCP_GATEWAY_POLICY_DATA_URL' '$server/mcp-app/src/main/resources/application.yml' && ! grep -rq 'MCP_POLICY_' '$server' --include=*.yml --include=*.template --include=*.env --include=*.yaml"
check "environment variables are renamed" grep -q 'MCP_NOTES_URL' "$server/mcp-app/src/main/resources/application.yml"

for ci_file in ci/run.sh Makefile .gitlab-ci.yml Jenkinsfile .github/workflows/ci.yml; do
    check "generated server has $ci_file" test -s "$server/$ci_file"
done
check "generated server has no registry settings" bash -c "test ! -e '$server/.ci' && ! grep -rIl 'MCP_MAVEN_REPOSITORY\\|maven.pkg.github.com\\|packages: read' '$server' --exclude-dir=target"
check "generated Dockerfile keeps the source and licenses labels" bash -c "grep -q 'image.source=\"https://github.com/aiwcorpo/mcp-server-notes\"' '$server/Dockerfile' && grep -q 'image.licenses=\"Apache-2.0\"' '$server/Dockerfile'"
check "pull request job: contents read only, no env" bash -c "awk '/verify-pull-request:/{f=1} /verify-main:/{f=0} f' '$server/.github/workflows/ci.yml' | grep -q 'contents: read' && ! awk '/verify-pull-request:/{f=1} /verify-main:/{f=0} f' '$server/.github/workflows/ci.yml' | grep -q 'env:\\|secrets\\.'"
check "ci/run.sh is executable and names the generated image" bash -c "test -x '$server/ci/run.sh' && grep -q 'mcp-server-notes:' '$server/ci/run.sh' && grep -q 'mcp-notes.jar' '$server/ci/run.sh'"
check "ci/run.sh refuses an unknown step" bash -c "cd '$server' && ci/run.sh bogus >/dev/null 2>&1; test \$? -eq 2"
for release_file in LICENSE NOTICE SECURITY.md CONTRIBUTING.md CHANGELOG.md .github/workflows/release.yml; do
    check "generated server has $release_file" test -s "$server/$release_file"
done

for deploy_file in deploy/README.md deploy/compose.yaml deploy/server.env deploy/certs/.gitignore deploy/systemd/mcp-notes.service deploy/systemd/mcp-notes.env.template \
    deploy/scripts/install.sh deploy/scripts/update.sh deploy/scripts/healthcheck.sh; do
    check "generated server has $deploy_file" test -e "$server/$deploy_file"
done
check "compose file names the renamed service, image and variables" bash -c "grep -q 'mcp-notes:' '$server/deploy/compose.yaml' && grep -q 'mcp-server-notes' '$server/deploy/compose.yaml' && grep -q 'MCP_NOTES_VERSION' '$server/deploy/compose.yaml'"
check "compose file starts at the first version of a generated server" grep -q 'MCP_NOTES_VERSION:-0.1.0}' "$server/deploy/compose.yaml"
check "certificate names in the deploy files follow the server id" bash -c "grep -q 'mcp-notes.internal' '$server/deploy/README.md' && grep -q 'CN=mcp-notes' '$server/deploy/README.md'"

check "stub backend and run configurations are generated" test -s "$server/mcp-stub-backend/pom.xml" -a -s "$server/.run/LocalGateway.run.xml" -a -s "$server/local/scope-mapping.json"
check "scope mapping uses the renamed namespace" grep -q 'Notes::Project/' "$server/local/scope-mapping.json"

check "generated README is its own, not the template's" bash -c "! cmp -s '$template/README.md' '$server/README.md' && grep -q '^# mcp-notes\$' '$server/README.md' && ! grep -qi 'new-server.sh\\|Create a new MCP server\\|mcp-example' '$server/README.md'"
check "generated README names the jar and the manifest" bash -c "grep -q 'mcp-app/target/mcp-notes.jar' '$server/README.md' && grep -q 'mcp-manifest.json' '$server/README.md'"
check "generated README links the developer guide of mcp-docs" bash -c "grep -q 'mcp-docs/blob/main/docs/developer/new-server.md' '$server/README.md' && grep -q 'mcp-docs/blob/main/docs/developer/tools.md' '$server/README.md'"
check "generated server has no docs directory of the template" test ! -e "$server/docs"
check "generated CHANGELOG is its own and in English" bash -c "grep -q '\`notes\`' '$server/CHANGELOG.md' && grep -q '^## Unreleased\$' '$server/CHANGELOG.md' && grep -q 'Versions follow SemVer' '$server/CHANGELOG.md' && ! grep -qi 'sonda wersji\\|Niewydane\\|wersje SemVer' '$server/CHANGELOG.md' && ! LC_ALL=C grep -q '[^ -~]' '$server/CHANGELOG.md'"
for pom in pom.xml mcp-core/pom.xml mcp-application/pom.xml mcp-integration/pom.xml mcp-tools/pom.xml mcp-app/pom.xml mcp-stub-backend/pom.xml; do
    check "generated $pom starts at 0.1.0-SNAPSHOT" bash -c "test \$(grep -c '<version>0.1.0-SNAPSHOT</version>' '$server/$pom') -eq 1 && ! grep -q '<version>$(sed -n 's|^    <version>\(.*\)</version>$|\1|p' "$template/pom.xml" | head -n 1)</version>' '$server/$pom'"
done
check "generated server keeps the template's starter version" bash -c "grep -q '<mcp-platform.version>$(sed -n 's|.*<mcp-platform.version>\(.*\)</mcp-platform.version>.*|\1|p' "$template/pom.xml")</mcp-platform.version>' '$server/pom.xml'"
check "every action in the workflows is pinned to a commit SHA" bash -c "! grep -hE '^\s*-?\s*uses:' '$server'/.github/workflows/*.yml | grep -vE 'uses: [^@ ]+@[0-9a-f]{40}\s*(#.*)?\$'"
check "every image of the CI is pinned to a digest" bash -c "! grep -hE 'image:? .?(maven|docker):' '$server'/.github/workflows/*.yml '$server'/.gitlab-ci.yml '$server'/Jenkinsfile | grep -v '@sha256:'"
check "the pom carries the licence" grep -q 'Apache License, Version 2.0' "$server/pom.xml"
check "no placeholder left" bash -c "! grep -rIil example '$server' --exclude=LICENSE"

"$template/new-server.sh" tickets "$work/tickets" >/dev/null
check "a second system gets its own namespace" grep -q '^namespace Tickets {' "$work/tickets/mcp-app/src/main/resources/mcp/policies/schema.cedarschema"

check "template unchanged" test "$before" == "$(git -C "$template" status --porcelain --untracked-files=all)"

if [[ "${1:-}" == "--build" ]]; then
    for generated in relative-notes tickets; do
        check "generated $generated builds and passes ci/run.sh verify (tests, floors, manifest)" bash -c "cd '$work/$generated' && git init -q && ci/run.sh verify >'$work/build-$generated.log' 2>&1"
        check "generated $generated passes ci/run.sh hygiene" bash -c "cd '$work/$generated' && ci/run.sh hygiene >/dev/null 2>&1"
        check "generated $generated writes the manifest in the build" test -s "$work/$generated/mcp-app/target/mcp-manifest.json"
    done
fi

echo "failures: $failures"
[[ $failures -eq 0 ]]
