#!/usr/bin/env bash
# Sync this fork with GeyserMC upstream:
#   1. fetch upstream (adds the remote if it's missing)
#   2. fast-forward master to upstream/master
#   3. rebase (default) or merge sunny onto the new master
#
# Usage: scripts/sync-upstream.sh [--merge] [--push] [--abort-on-conflict]
#   --merge              merge master into sunny instead of rebasing
#   --push               push master, then sunny (--force-with-lease after a rebase)
#   --abort-on-conflict  abort the rebase/merge on conflict instead of leaving it to resolve

set -euo pipefail

UPSTREAM_URL="https://github.com/GeyserMC/Geyser.git"
UPSTREAM_REMOTE="upstream"
ORIGIN_REMOTE="origin"
BASE_BRANCH="master"
FORK_BRANCH="sunny"

mode="rebase"
push=false
abort_on_conflict=false

for arg in "$@"; do
    case "$arg" in
        --merge) mode="merge" ;;
        --rebase) mode="rebase" ;;
        --push) push=true ;;
        --abort-on-conflict) abort_on_conflict=true ;;
        -h|--help) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m==>\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m==>\033[0m %s\n' "$*" >&2; exit 1; }

cd "$(git rev-parse --show-toplevel)"

git diff --quiet && git diff --cached --quiet \
    || die "Working tree has uncommitted changes; commit or stash them first."
[ -d "$(git rev-parse --git-path rebase-merge)" ] || [ -d "$(git rev-parse --git-path rebase-apply)" ] \
    && die "A rebase is already in progress."
git rev-parse -q --verify MERGE_HEAD >/dev/null && die "A merge is already in progress."

if ! git remote get-url "$UPSTREAM_REMOTE" >/dev/null 2>&1; then
    info "Adding remote '$UPSTREAM_REMOTE' -> $UPSTREAM_URL"
    git remote add "$UPSTREAM_REMOTE" "$UPSTREAM_URL"
fi

info "Fetching $UPSTREAM_REMOTE and $ORIGIN_REMOTE"
git fetch --prune "$UPSTREAM_REMOTE"
git fetch --prune "$ORIGIN_REMOTE"

# 1. Fast-forward master. It should never carry fork-only commits, so refuse anything but a ff.
info "Updating $BASE_BRANCH to $UPSTREAM_REMOTE/$BASE_BRANCH"
git switch --quiet "$BASE_BRANCH"
old_base=$(git rev-parse HEAD)
git merge --ff-only --quiet "$UPSTREAM_REMOTE/$BASE_BRANCH" \
    || die "$BASE_BRANCH has diverged from $UPSTREAM_REMOTE/$BASE_BRANCH; fix it by hand."
new_base=$(git rev-parse HEAD)
if [ "$old_base" = "$new_base" ]; then
    info "$BASE_BRANCH already up to date"
else
    info "$BASE_BRANCH: $(git rev-list --count "$old_base..$new_base") new upstream commit(s)"
fi

if $push; then
    info "Pushing $BASE_BRANCH to $ORIGIN_REMOTE"
    git push "$ORIGIN_REMOTE" "$BASE_BRANCH"
fi

# 2. Bring sunny up to date with master.
git switch --quiet "$FORK_BRANCH"
if git merge-base --is-ancestor "$BASE_BRANCH" "$FORK_BRANCH"; then
    info "$FORK_BRANCH already contains $BASE_BRANCH; nothing to $mode"
    exit 0
fi

backup="backup/$FORK_BRANCH-$(date +%Y%m%d-%H%M%S)"
git branch "$backup" "$FORK_BRANCH"
info "Saved current $FORK_BRANCH as $backup"

on_conflict() {
    if $abort_on_conflict; then
        git "$mode" --abort
        git branch -D "$backup" >/dev/null
        die "Conflicts during $mode; aborted. $FORK_BRANCH is unchanged."
    fi
    warn "Conflicts during $mode. Resolve them, then run:"
    if [ "$mode" = "rebase" ]; then
        warn "  git add <files> && git rebase --continue    (or: git rebase --abort)"
    else
        warn "  git add <files> && git commit              (or: git merge --abort)"
    fi
    warn "The pre-sync branch is kept as $backup."
    exit 1
}

info "Running $mode of $FORK_BRANCH onto $BASE_BRANCH"
if [ "$mode" = "rebase" ]; then
    git rebase "$BASE_BRANCH" || on_conflict
else
    git merge --no-edit --no-stat "$BASE_BRANCH" || on_conflict
fi

info "$FORK_BRANCH is now $(git rev-list --count "$BASE_BRANCH..$FORK_BRANCH") commit(s) ahead of $BASE_BRANCH"

if $push; then
    info "Pushing $FORK_BRANCH to $ORIGIN_REMOTE"
    if [ "$mode" = "rebase" ]; then
        git push --force-with-lease="$FORK_BRANCH:$ORIGIN_REMOTE/$FORK_BRANCH" "$ORIGIN_REMOTE" "$FORK_BRANCH"
    else
        git push "$ORIGIN_REMOTE" "$FORK_BRANCH"
    fi
else
    info "Not pushed. Push with:"
    if [ "$mode" = "rebase" ]; then
        info "  git push $ORIGIN_REMOTE $BASE_BRANCH && git push --force-with-lease $ORIGIN_REMOTE $FORK_BRANCH"
    else
        info "  git push $ORIGIN_REMOTE $BASE_BRANCH $FORK_BRANCH"
    fi
fi

git branch -D "$backup" >/dev/null
info "Done"
