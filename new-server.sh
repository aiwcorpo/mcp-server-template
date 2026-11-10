#!/usr/bin/env bash
# Generates a new MCP server from this template: new-server.sh <system> [target-dir]
#
# <system> is the SystemId of the new server (lower case letters and digits, 2 to 31 characters, not one of the ids the
# contracts reserve; the pattern is read from the mcp-contracts jar the template builds against). There is no list of
# systems: a server made here can be registered at a running platform under any such id.
#
# The template is a working server for the placeholder system "example". Generation copies the files git tracks and
# renames the placeholder everywhere, in contents and paths:
#   pl.aiwcorpo.mcp.example -> pl.aiwcorpo.mcp.<system>   (groupId, Java packages, directories)
#   example / Example / EXAMPLE -> <system> / <System> / <SYSTEM>
# The Cedar namespace is the Example form (Example::Record becomes <System>::Record) and the groups of the policies are
# mcp-<system>-read and so on; everything else the policies need comes from the files in mcp/policies/, which are copied
# and renamed like any other. Template-only files (this script, README.md, CHANGELOG.md) are not
# copied; the generated server gets a CHANGELOG.md and a short README.md of its own, both in English; the project version
# is reset to 0.1.0-SNAPSHOT. LICENSE is copied unchanged, NOTICE, SECURITY.md, CONTRIBUTING.md and the release workflow
# like the rest. Afterwards no "example" may remain; on any failure the target directory is removed.
set -euo pipefail

usage() {
    echo "usage: $0 <system> [target-dir]" >&2
    echo "  <system>: the SystemId of the server, lower case letters and digits (for example notes)" >&2
    echo "  [target-dir]: defaults to <template>/../mcp-server-<system>; must not exist" >&2
    exit 2
}

[[ $# -ge 1 && $# -le 2 ]] || usage
system="$1"
template="$(cd "$(dirname "$0")" && pwd)"

# Resolved before anything else, so the check and the writes below refer to the same directory.
target="${2:-$template/../mcp-server-$system}"
case "$target" in
    /*) ;;
    *) target="$PWD/$target" ;;
esac
target_parent="$(cd "$(dirname "$target")" 2>/dev/null && pwd)" || { echo "parent of $target does not exist" >&2; exit 1; }
target="$target_parent/$(basename "$target")"
[[ ! -e "$target" ]] || { echo "$target already exists" >&2; exit 1; }
case "$target/" in
    "$template"/*) echo "the target must be outside the template ($template)" >&2; exit 1 ;;
esac

# The pattern of SystemId in the contracts the template builds against: the one place that says what a system id may be.
system_id_pattern() {
    local repo="${MAVEN_REPO:-}" platform contracts jar
    # Maven reads user.home from the passwd entry, which is not $HOME in CI containers, and settings.xml may move the repo.
    if [[ -z "$repo" ]]; then
        repo="$("$template/mvnw" -q -B -N -f "$template/pom.xml" \
            org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
            -Dexpression=settings.localRepository -DforceStdout 2>/dev/null)" || repo=""
        repo="${repo:-$HOME/.m2/repository}"
    fi
    platform="$(sed -n 's|.*<mcp-platform.version>\(.*\)</mcp-platform.version>.*|\1|p' "$template/pom.xml")"
    contracts="$(sed -n 's|.*<mcp-contracts.version>\(.*\)</mcp-contracts.version>.*|\1|p' \
        "$repo/pl/aiwcorpo/mcp/mcp-platform-bom/$platform/mcp-platform-bom-$platform.pom" 2>/dev/null)"
    jar="$repo/pl/aiwcorpo/mcp/mcp-contracts/$contracts/mcp-contracts-$contracts.jar"
    if [[ -z "$contracts" || ! -f "$jar" ]]; then
        echo "mcp-contracts jar not found in $repo; run ./mvnw verify in the template first" >&2
        return 1
    fi
    unzip -p "$jar" mcp-contracts/schemas/common.schema.json \
        | python3 -I -c 'import json, sys; print(json.load(sys.stdin)["$defs"]["SystemId"]["pattern"])'
}

[[ "$system" =~ ^[a-z][a-z0-9]{1,30}$ ]] || usage
pattern="$(system_id_pattern)"
if ! python3 -I -c 'import re, sys; sys.exit(0 if re.fullmatch(sys.argv[1], sys.argv[2]) else 1)' "$pattern" "$system"; then
    echo "'$system' is not a SystemId of the contracts: $pattern" >&2
    exit 2
fi
# Package segments that would collide with the starter (platform, audit logger) or not compile.
case "$system" in
    example | platform | audit | abstract | assert | boolean | break | byte | case | catch | char | class | const \
    | continue | default | do | double | else | enum | extends | final | finally | float | for | goto | if \
    | implements | import | instanceof | int | interface | long | native | new | package | private | protected \
    | public | return | short | static | strictfp | super | switch | synchronized | this | throw | throws \
    | transient | try | void | volatile | while | true | false | null | var | record | yield)
        echo "'$system' cannot be a package name here" >&2
        exit 2 ;;
esac

System="$(printf '%s' "${system:0:1}" | tr '[:lower:]' '[:upper:]')${system:1}"
SYSTEM="$(printf '%s' "$system" | tr '[:lower:]' '[:upper:]')"

rename() {
    perl -pe "s/example/$system/g; s/Example/$System/g; s/EXAMPLE/$SYSTEM/g"
}

# A generated server is a new product: it starts as a snapshot of 0.1.0, whatever version the template itself has reached.
initial_version="0.1.0-SNAPSHOT"
template_version="$(sed -n 's|^    <version>\(.*\)</version>$|\1|p' "$template/pom.xml" | head -n 1)"
[[ -n "$template_version" ]] || { echo "no project version in $template/pom.xml" >&2; exit 1; }
set_project_version() {
    # Root pom (4 spaces) and the parent element of each module (8 spaces); the template has no dependency with its own version.
    perl -pe "s#^(    |        )<version>\\Q$template_version\\E</version>\$#\$1<version>$initial_version</version>#"
}

# The compose file pins the image of the release: a generated server has none yet, so it starts at the first version.
set_image_version() {
    perl -pe "s#(_VERSION:-)\Q$template_version\E\}#\${1}${initial_version%-SNAPSHOT}}#"
}

cd "$template"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    # Tracked files only: notes, .env files or build output lying around in the template stay out.
    files="$(git ls-files --cached)"
else
    files="$(find . -type f -not -path './.git/*' -not -path '*/target/*' | sed 's|^\./||')"
fi

trap 'rm -rf "$target"' ERR
mkdir "$target"
while IFS= read -r file; do
    [[ -f "$file" ]] || continue
    case "$file" in
        new-server.sh | README.md | CHANGELOG.md) continue ;;
    esac
    destination="$target/$(printf '%s' "$file" | rename)"
    mkdir -p "$(dirname "$destination")"
    case "$file" in
        LICENSE) cp "$file" "$destination" ;;
        pom.xml | */pom.xml) rename < "$file" | set_project_version > "$destination" ;;
        deploy/compose.yaml) rename < "$file" | set_image_version > "$destination" ;;
        *) rename < "$file" > "$destination" ;;
    esac
    if [[ -x "$file" ]]; then
        chmod +x "$destination"
    fi
done <<< "$files"

cat > "$target/CHANGELOG.md" <<EOF
# Changelog

Versions follow SemVer, newest first. Until the first release the working version is \`$initial_version\`.

## Unreleased

- Generated from mcp-server-template for the system \`$system\`.
EOF

developer_guide="https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer"
platform_version="$(sed -n 's|.*<mcp-platform.version>\(.*\)</mcp-platform.version>.*|\1|p' "$template/pom.xml")"
cat > "$target/README.md" <<EOF
# mcp-$system

MCP server for $system in the aiwcorpo MCP platform, generated from \`mcp-server-template\`. It runs only behind the platform
gateway: the gateway connects to its address, and it accepts only calls with the gateway's internal JWT (401 without it) and calls
$system with the credential the gateway attaches to each request. Its Cedar namespace is \`$System\`, its policies are in
\`mcp-app/src/main/resources/mcp/policies/\`.

## Build

JDK 25 is required; no container runtime and no running $system are needed, the tests use an in-process stub backend.

\`\`\`bash
export JAVA_HOME="\$(/usr/libexec/java_home -v 25)"   # macOS; on Linux the JDK 25 directory
./mvnw -B verify     # jar: mcp-app/target/mcp-$system.jar and the manifest mcp-app/target/mcp-manifest.json
\`\`\`

## Developing

The record domain, the adapter and the policies are placeholders: replace them with the real ones.

- Resource types and policies: \`mcp-app/src/main/resources/mcp/policies/\`, tested in \`${System}PolicyTest\`.
- [A new server]($developer_guide/new-server.md): modules, what to complete, the patterns the server carries.
- [Writing tools]($developer_guide/tools.md): annotations, risk, resources, search, results and errors.

## Run locally

In IntelliJ start the run configurations \`LocalGateway\` and \`Server (dev)\` (in \`.run/\`). The server looks for $system at
127.0.0.1:18081 (\`MCP_${SYSTEM}_URL\` changes that); for a first try without a real $system, the stub backend of the mcp-e2e repository
(\`stub-backend/\`) listens there. From a shell, after \`./mvnw verify\`:

\`\`\`bash
java -jar ~/.m2/repository/pl/aiwcorpo/mcp/mcp-platform-test/$platform_version/mcp-platform-test-$platform_version-local-gateway.jar \\
  --server mcp-$system --jwks-port 18445 --policies-dir mcp-app/src/main/resources/mcp/policies \\
  --scope-mapping .run/scope-mapping.json --env-file local-gateway.env &   # MCP endpoint 127.0.0.1:18500
SPRING_PROFILES_ACTIVE=dev java -jar mcp-app/target/mcp-$system.jar            # 127.0.0.1:8080
\`\`\`

An MCP client connects to \`http://127.0.0.1:18500/mcp\` with \`Authorization: Bearer <any token>\`, which the backend then receives as the
credential.

## Deployment

\`deploy/\`: \`compose.yaml\` with \`server.env\` (and \`certs/\` only for your own certificate), or the systemd unit. You start the server;
the platform operator enters its address in the portal and approves the connection after comparing the certificate fingerprint from the
server log. \`deploy/README.md\` has the steps.

## License

Apache-2.0, see \`LICENSE\`.
EOF

if leftovers="$(grep -rIil example "$target" --exclude=LICENSE)" && [[ -n "$leftovers" ]]; then
    echo "placeholder left in:" >&2
    echo "$leftovers" >&2
    false
fi
trap - ERR

cat <<EOF
Generated mcp-$system in $target
Next:
  cd $target && git init
  ./mvnw -B verify       # JDK 25; builds and runs the tests against TestGateway and an in-process stub backend
Then replace the sample record domain and the policies with the real ones (README of the template, "Write the server").
EOF
