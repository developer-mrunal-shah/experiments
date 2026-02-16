#!/bin/bash
# ─── Add Collection to Learning Lab ──────────────────────────
#
# Usage:
#   ./add-collection.sh                    # Auto-discover all collections
#   ./add-collection.sh my-new-collection  # Add a specific collection
#
# This script scans the /collections folder for any subfolder
# containing a meta.json and rebuilds the manifest.

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COLLECTIONS_DIR="$SCRIPT_DIR/collections"
MANIFEST="$COLLECTIONS_DIR/manifest.json"

echo "🔍 Scanning for collections in $COLLECTIONS_DIR..."
echo ""

# Find all folders with meta.json
FOUND=()
for dir in "$COLLECTIONS_DIR"/*/; do
    folder=$(basename "$dir")
    if [ -f "$dir/meta.json" ]; then
        title=$(python3 -c "import json; print(json.load(open('$dir/meta.json'))['title'])" 2>/dev/null || echo "$folder")
        icon=$(python3 -c "import json; print(json.load(open('$dir/meta.json')).get('icon','📚'))" 2>/dev/null || echo "📚")
        echo "  ✅ $icon $title ($folder/)"
        FOUND+=("\"$folder\"")
    else
        echo "  ⚠️  $folder/ — missing meta.json, skipping"
    fi
done

if [ ${#FOUND[@]} -eq 0 ]; then
    echo ""
    echo "❌ No collections found!"
    echo ""
    echo "To create a new collection:"
    echo "  1. Create a folder in collections/ (e.g., collections/python-basics/)"
    echo "  2. Add a meta.json with title, icon, description, etc."
    echo "  3. Add an index.html (interactive) and optionally print.html (printable)"
    echo "  4. Run this script again"
    exit 1
fi

# Build manifest
COLLECTIONS_JSON=$(IFS=,; echo "${FOUND[*]}")
cat > "$MANIFEST" << EOF
{
  "collections": [
    $(echo "$COLLECTIONS_JSON" | sed 's/,/,\n    /g')
  ]
}
EOF

echo ""
echo "📋 Manifest updated: ${#FOUND[@]} collection(s)"
echo "🌐 Refresh the Learning Lab homepage to see changes!"
echo ""

# ─── TEMPLATE ─────────────────────────────────────────────────
# Want to create a new collection from scratch? Here's a starter:

if [ "$1" = "--template" ] && [ -n "$2" ]; then
    TEMPLATE_DIR="$COLLECTIONS_DIR/$2"
    if [ -d "$TEMPLATE_DIR" ]; then
        echo "❌ Folder '$2' already exists!"
        exit 1
    fi

    mkdir -p "$TEMPLATE_DIR"
    cat > "$TEMPLATE_DIR/meta.json" << 'METAEOF'
{
  "title": "My New Collection",
  "icon": "📚",
  "color": "#3b82f6",
  "description": "A brief description of this collection.",
  "age": "9-12",
  "projectCount": 0,
  "difficulty": "Beginner",
  "tags": ["topic1", "topic2"],
  "pages": {
    "interactive": "index.html",
    "printable": "print.html"
  },
  "created": "$(date +%Y-%m-%d)",
  "author": "Palani's Learning Lab"
}
METAEOF

    cat > "$TEMPLATE_DIR/index.html" << 'HTMLEOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>My New Collection</title>
  <style>
    body { font-family: sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; }
    h1 { color: #1a1a2e; }
  </style>
</head>
<body>
  <h1>📚 My New Collection</h1>
  <p>Start adding your projects here!</p>
</body>
</html>
HTMLEOF

    echo "✨ Template created at collections/$2/"
    echo "   Edit meta.json and index.html to customize, then run:"
    echo "   ./add-collection.sh"
fi
