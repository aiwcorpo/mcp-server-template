#!/usr/bin/env bash
# Generates a new MCP server from this template: new-server.sh [--trial] <system> [target-dir]
#
# <system> must be a SystemId of the contracts (read from the mcp-contracts jar the template builds against):
# a server of any other system does not start outside the dev profile. --trial allows another name for a
# throwaway server (dev profile only, tests expect internal_error instead of credential_required).
#
# The template is a working server for the placeholder system "example". Generation copies the files git tracks
# and renames the placeholder everywhere, in contents and paths:
#   pl.aiwcorpo.mcp.example -> pl.aiwcorpo.mcp.<system>   (groupId, Java packages, directories)
#   example / Example / EXAMPLE -> <system> / <System> / <SYSTEM>
# The template's resource model is the Jira one. For another system the files under profiles/<system>/ (resource
# types, how a record is described to the policy, test ids) replace their counterparts (files without a counterpart are
# added), and the credential type of
# the system goes into the configuration. Template-only files (this
# script, its test, STATUS.md, docs/nowy-serwer.md, profiles/) are not copied. Afterwards no
# "example" may remain; on any failure the target directory is removed.
set -euo pipefail

usage() {
    echo "usage: $0 [--trial] <system> [target-dir]" >&2
    echo "  <system>: a SystemId of the contracts (jira, confluence, gitlab, servicedesk, opensearch)" >&2
    echo "  --trial: allow another name, lower case letters and digits (dev profile only)" >&2
    echo "  [target-dir]: defaults to <template>/../mcp-server-<system>; must not exist" >&2
    exit 2
}

trial=false
if [[ "${1:-}" == "--trial" ]]; then
    trial=true
    shift
fi
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

contract_systems() {
    local repo="${MAVEN_REPO:-$HOME/.m2/repository}" platform contracts jar
    platform="$(sed -n 's|.*<mcp-platform.version>\(.*\)</mcp-platform.version>.*|\1|p' "$template/pom.xml")"
    contracts="$(sed -n 's|.*<mcp-contracts.version>\(.*\)</mcp-contracts.version>.*|\1|p' \
        "$repo/pl/aiwcorpo/mcp/mcp-platform-bom/$platform/mcp-platform-bom-$platform.pom" 2>/dev/null)"
    jar="$repo/pl/aiwcorpo/mcp/mcp-contracts/$contracts/mcp-contracts-$contracts.jar"
    if [[ -z "$contracts" || ! -f "$jar" ]]; then
        echo "mcp-contracts jar not found in $repo; run ./mvnw verify in the template first" >&2
        return 1
    fi
    unzip -p "$jar" mcp-contracts/schemas/common.schema.json \
        | python3 -I -c 'import json, sys; print("\n".join(json.load(sys.stdin)["$defs"]["SystemId"]["enum"]))'
}

[[ "$system" =~ ^[a-z][a-z0-9]{1,29}$ ]] || usage
known="$(contract_systems)"
if grep -qx "$system" <<< "$known"; then
    trial=false
elif [[ "$trial" == false ]]; then
    echo "'$system' is not a SystemId of the contracts: $(tr '\n' ' ' <<< "$known")" >&2
    echo "use --trial for a throwaway server" >&2
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
        new-server.sh | tests/new-server-test.sh | STATUS.md | docs/nowy-serwer.md | profiles/*) continue ;;
    esac
    source="$file"
    if [[ "$trial" == false && -f "profiles/$system/$file" ]]; then
        source="profiles/$system/$file"
    fi
    destination="$target/$(printf '%s' "$file" | rename)"
    mkdir -p "$(dirname "$destination")"
    rename < "$source" > "$destination"
    if [[ -x "$source" ]]; then
        chmod +x "$destination"
    fi
done <<< "$files"

# Files only the system's profile has (extra tests of its own behavior).
if [[ "$trial" == false && -d "profiles/$system" ]]; then
    while IFS= read -r profile_file; do
        file="${profile_file#profiles/$system/}"
        [[ -e "$file" ]] && continue
        destination="$target/$(printf '%s' "$file" | rename)"
        mkdir -p "$(dirname "$destination")"
        rename < "$profile_file" > "$destination"
    done < <(git ls-files --cached "profiles/$system")
fi

if [[ "$trial" == false ]]; then
    perl -pi -e 's/static final boolean TRIAL_SYSTEM = true;/static final boolean TRIAL_SYSTEM = false;/' \
        "$target/mcp-app/src/test/java/pl/aiwcorpo/mcp/$system/app/ServerTestSupport.java"
fi

# The credential type is the system's configuration in the gateway and the broker (Cedar sees it as
# context.credentialType): GitLab resolves OAuth tokens (a PAT is accepted too), Service Desk uses the account of the
# service, OpenSearch exchanges the user's token, Jira and Confluence use the user's own secret.
if [[ "$trial" == false ]]; then
    case "$system" in
        gitlab) credential_type=user-oauth; accepted_types=user-oauth,user-secret ;;
        servicedesk) credential_type=service-account; accepted_types=service-account ;;
        opensearch) credential_type=token-exchange; accepted_types=token-exchange ;;
        *) credential_type=user-secret; accepted_types=user-secret ;;
    esac
    resources="$target/mcp-app/src/main/resources"
    perl -pi -e "s/^(\\s*credential-type: ).*/\${1}$credential_type/; s/(accepted-credential-types: .*:)user-secret\\}/\${1}$accepted_types}/" \
        "$resources/application.yml"
    perl -pi -e "s/(accepted-credential-types: )user-secret\$/\${1}$accepted_types/" "$resources/application-dev.yml"
fi

if leftovers="$(grep -rIil example "$target")" && [[ -n "$leftovers" ]]; then
    echo "placeholder left in:" >&2
    echo "$leftovers" >&2
    false
fi
trap - ERR

cat <<EOF
Generated mcp-$system in $target$([[ "$trial" == true ]] && echo " (trial: dev profile only)")
Next:
  cd $target && git init
  ./mvnw verify          # JDK 25; builds and runs the tests against TestGateway and a stub backend
Then replace the sample record domain with the real one (docs/jak-napisac-narzedzie.md).
EOF
