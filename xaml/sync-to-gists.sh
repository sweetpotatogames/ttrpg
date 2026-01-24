#!/bin/bash
# Sync XAML files to GitHub Gists
# Requires: gh CLI authenticated with gist scope
#
# First run: Creates new gists and outputs their IDs
# Subsequent runs: Updates existing gists using stored IDs
#
# Usage: ./sync-to-gists.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GIST_IDS_FILE="$SCRIPT_DIR/.gist-ids"

# Load existing gist IDs if file exists
declare -A GIST_IDS
if [[ -f "$GIST_IDS_FILE" ]]; then
    while IFS='=' read -r key value; do
        GIST_IDS["$key"]="$value"
    done < "$GIST_IDS_FILE"
fi

# Gist descriptions
declare -A DESCRIPTIONS=(
    ["DndThemeDemo.xaml"]="TTRPG Theme Demo - Dark Fantasy D&D Theme for NoesisGUI/XamlToy"
    ["CharacterSheet.xaml"]="TTRPG Character Sheet - D&D 5e Style for NoesisGUI/XamlToy"
    ["CombatControl.xaml"]="TTRPG Combat Control Panel for NoesisGUI/XamlToy"
    ["GMControl.xaml"]="TTRPG GM Control Panel for NoesisGUI/XamlToy"
    ["CombatHud.xaml"]="TTRPG Combat HUD Overlay for NoesisGUI/XamlToy"
    ["DndTheme.xaml"]="TTRPG Shared Theme ResourceDictionary for NoesisGUI/XamlToy"
)

echo "=== XAML to Gist Sync ==="
echo ""

cd "$SCRIPT_DIR"

for file in *.xaml; do
    [[ -f "$file" ]] || continue

    echo "Processing: $file"

    gist_id="${GIST_IDS[$file]}"
    desc="${DESCRIPTIONS[$file]:-TTRPG UI for XamlToy}"

    # Create temp file named Main.xaml (required by XamlToy)
    cp "$file" Main.xaml

    if [[ -n "$gist_id" ]]; then
        # Update existing gist
        echo "  Updating gist: $gist_id"
        gh gist edit "$gist_id" Main.xaml 2>/dev/null || {
            echo "  Warning: Could not update gist. Creating new one."
            gist_id=""
        }
    fi

    if [[ -z "$gist_id" ]]; then
        # Create new gist
        echo "  Creating new gist..."
        gist_url=$(gh gist create Main.xaml --public --desc "$desc")
        gist_id=$(echo "$gist_url" | grep -oE '[a-f0-9]{32}$' || echo "$gist_url" | awk -F'/' '{print $NF}')
        GIST_IDS["$file"]="$gist_id"
        echo "  Created: $gist_id"
    fi

    rm Main.xaml

    echo "  XamlToy: https://www.noesisengine.com/xamltoy/$gist_id"
    echo ""
done

# Save gist IDs for future runs
echo "# Auto-generated gist ID mapping" > "$GIST_IDS_FILE"
for key in "${!GIST_IDS[@]}"; do
    echo "$key=${GIST_IDS[$key]}" >> "$GIST_IDS_FILE"
done

echo "=== Sync Complete ==="
echo ""
echo "Gist IDs saved to: $GIST_IDS_FILE"
echo ""
echo "XamlToy Preview URLs:"
for key in "${!GIST_IDS[@]}"; do
    echo "  ${key%.xaml}: https://www.noesisengine.com/xamltoy/${GIST_IDS[$key]}"
done
