#!/usr/bin/env bash
# Generates a new MCP server from this template: new-server.sh <system> [target-dir]
#
# The template is a working server for the system "example". Generation copies the files this repository
# tracks (plus new, not ignored ones) and renames the placeholder everywhere, in contents and paths:
#   pl.aiwcorpo.mcp.example -> pl.aiwcorpo.mcp.<system>   (groupId, Java packages, directories)
#   example / Example / EXAMPLE -> <system> / <System> / <SYSTEM>
#     (server-id mcp-<system>, artifact mcp-<system>.jar, CredentialContext.require("<system>"),
#      MCP_<SYSTEM>_URL, <System>Tool, ...)
# Template-only files (this script, STATUS.md, docs/nowy-serwer.md) are not copied.
# Afterwards no "example" may remain in the new project; the script fails if one does.
set -euo pipefail

usage() {
    echo "usage: $0 <system> [target-dir]" >&2
    echo "  <system>: lower case letters and digits, starting with a letter (jira, confluence, gitlab, ...)" >&2
    echo "  [target-dir]: defaults to ../mcp-server-<system>; must not exist" >&2
    exit 2
}

[[ $# -ge 1 && $# -le 2 ]] || usage
system="$1"
# Becomes a Java package segment and part of mcp-<system>, the C2 audience; dashes would break the package.
[[ "$system" =~ ^[a-z][a-z0-9]{1,29}$ ]] || usage
[[ "$system" != "example" ]] || { echo "pick the real system name, not the placeholder" >&2; exit 2; }

template="$(cd "$(dirname "$0")" && pwd)"
target="${2:-$template/../mcp-server-$system}"
[[ ! -e "$target" ]] || { echo "$target already exists" >&2; exit 1; }

System="$(printf '%s' "${system:0:1}" | tr '[:lower:]' '[:upper:]')${system:1}"
SYSTEM="$(printf '%s' "$system" | tr '[:lower:]' '[:upper:]')"

rename() {
    perl -pe "s/example/$system/g; s/Example/$System/g; s/EXAMPLE/$SYSTEM/g"
}

cd "$template"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    files="$(git ls-files --cached --others --exclude-standard)"
else
    files="$(find . -type f -not -path './.git/*' -not -path '*/target/*' | sed 's|^\./||')"
fi

mkdir -p "$target"
target="$(cd "$target" && pwd)"
while IFS= read -r file; do
    [[ -f "$file" ]] || continue
    case "$file" in
        new-server.sh | STATUS.md | docs/nowy-serwer.md) continue ;;
    esac
    destination="$target/$(printf '%s' "$file" | rename)"
    mkdir -p "$(dirname "$destination")"
    rename < "$file" > "$destination"
    [[ -x "$file" ]] && chmod +x "$destination"
done <<< "$files"

if leftovers="$(grep -rIil example "$target")" && [[ -n "$leftovers" ]]; then
    echo "placeholder left in:" >&2
    echo "$leftovers" >&2
    exit 1
fi

cat <<EOF
Generated mcp-$system in $target
Next:
  cd $target && git init
  ./mvnw verify          # JDK 25; builds and runs the tests against TestGateway and a stub backend
Then replace the sample record domain with the real one (docs/jak-napisac-narzedzie.md).
EOF
