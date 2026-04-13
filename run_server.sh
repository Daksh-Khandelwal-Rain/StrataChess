#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run_server.sh — Launch StrataChess as the Host (White / Player 0)
#
# The lobby screen will start and let you enter your name.
# Your local IP address is printed in the terminal — share it with your opponent.
#
# USAGE:
#   ./run_server.sh
#
# CONCEPT: JavaFX Runtime Flags
# The 'java' command runs compiled .class files.
# The flags mirror what we used to compile:
#   --module-path → where JavaFX module jars live
#   --add-modules → which JavaFX APIs we need at runtime
# Additionally, 'javafx.media' must be listed for MusicPlayer to work.
# ─────────────────────────────────────────────────────────────────────────────

set -e

OUT_DIR="out"
JAVAFX_LIB="lib/javafx/lib"

# Check that the project has been compiled
if [ ! -d "$OUT_DIR" ]; then
    echo "❌ Error: Output directory not found. Run ./compile.sh first."
    exit 1
fi

echo "♟ Starting StrataChess — Host Mode (You are White)"
echo "   The game window will open. Check the terminal for your IP address."
echo ""

java \
    --module-path "$JAVAFX_LIB" \
    --add-modules javafx.controls,javafx.media \
    -cp "$OUT_DIR" \
    view.Main