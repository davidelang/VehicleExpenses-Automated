#!/bin/bash
# generate_pr.sh: Create a Pull Request markdown document for the Master Agent.
# Usage: ./generate_pr.sh plan1.md [plan2.md ...]

if [ $# -lt 1 ]; then
    echo "Usage: $0 plan1.md [plan2.md ...]"
    exit 1
fi

PLANS=("$@")
BRANCH_NAME=$(git rev-parse --abbrev-ref HEAD)
BACKUP_TAG="backup-$BRANCH_NAME"
PR_FILE="dev-ai-interaction/PRs/PR-$BRANCH_NAME.md"

# 1. Validation
if [[ "$BRANCH_NAME" == "master" ]] || [[ "$BRANCH_NAME" == "orchestration" ]]; then
    echo "Error: Cannot generate PR from branch '$BRANCH_NAME'."
    exit 1
fi

# 2. Check for backup tag
if ! git rev-parse "$BACKUP_TAG" >/dev/null 2>&1; then
    echo "Warning: Backup tag '$BACKUP_TAG' not found. Ensure you have run your cleanup/squash step."
fi

# 3. Create PR document
{
    echo "# Pull Request: $BRANCH_NAME"
    echo ""
    echo "## Recovery & Audit Info"
    echo "- **Original Messy State:** \`$BACKUP_TAG\` ($(git rev-parse $BACKUP_TAG 2>/dev/null || echo "NOT FOUND"))"
    echo "- **Cleaned HEAD:** \`$(git rev-parse HEAD)\`"
    echo ""
    echo "## Logical Commit History"
    git log --pretty=format:"* %s (%h)" master..HEAD
    echo ""
    echo ""
    echo "## Documentation & Plans"
    for plan in "${PLANS[@]}"; do
        echo "### Plan: $plan"
        echo '```markdown'
        cat "$plan"
        echo '```'
        echo ""
    done
    echo "## Files Changed"
    echo '```'
    git diff --stat master..HEAD
    echo '```'
} > "$PR_FILE"

echo "PR Generated: $PR_FILE"
echo "--------------------------------------------------------------------------------"
echo "INSTRUCTION FOR USER:"
echo "Switch to the Master Agent terminal and say:"
echo "  \"Please review PR-$BRANCH_NAME\""
echo "--------------------------------------------------------------------------------"
