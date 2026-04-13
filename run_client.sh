#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run_client.sh — Launch StrataChess as the Guest (Black / Player 1)
#
# USAGE:
#   ./run_client.sh 192.168.1.42    ← replace with the host's IP
#   ./run_client.sh localhost        ← for testing both players on one machine
#
# The lobby screen will start. Enter the host's IP when prompted.
# You can also provide it as a command-line argument for convenience.
# ─────────────────────────────────────────────────────────────────────────────

set -e

OUT_DIR="out"
JAVAFX_LIB="lib/javafx/lib"

# Check that the project has been compiled
if [ ! -d "$OUT_DIR" ]; then
    echo "❌ Error: Output directory not found. Run ./compile.sh first."
    exit 1
fi

echo "♟ Starting StrataChess — Guest Mode (You are Black)"

# CONCEPT: Command-Line Arguments in Shell Scripts
# "$1" is the first argument passed to the script.
# If provided (./run_client.sh 192.168.1.42), we print it as a reminder.
# The actual IP is entered in the GUI lobby, not passed to the Java program
# — the lobby handles user input. This is just informational.
if [ -n "$1" ]; then
    echo "   Connecting to host at: $1"
    echo "   (Enter this IP in the lobby screen)"
fi
echo ""

java \
    --module-path "$JAVAFX_LIB" \
    --add-modules javafx.controls,javafx.media \
    -cp "$OUT_DIR" \
    view.Main