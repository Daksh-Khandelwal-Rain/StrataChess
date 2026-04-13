#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# compile.sh — Build all StrataChess source files
#
# CONCEPT: Shell Scripts as Build Tools
# Before you learn Gradle or Maven, a shell script is the simplest possible
# build tool. It runs exactly the commands you would type manually — but saves
# you from retyping them every time. Understanding what this script does is
# understanding how Java compilation works under the hood.
#
# HOW JAVA COMPILATION WORKS:
# 1. javac (the Java compiler) takes .java source files as input.
# 2. It produces .class bytecode files as output — one per class.
# 3. The JVM (java command) executes those .class files.
# 4. JavaFX classes live in a separate SDK — we tell the compiler where
#    to find them using --module-path and --add-modules.
#
# USAGE:
#   chmod +x compile.sh   (only needed once — makes the script executable)
#   ./compile.sh
#
# PREREQUISITES:
#   - Java 17+ installed and on your PATH
#   - JavaFX 17 SDK extracted to ./lib/javafx/
#     Download from: https://gluonhq.com/products/javafx/
#     Unzip it so that ./lib/javafx/lib/ contains the .jar files.
# ─────────────────────────────────────────────────────────────────────────────

set -e  # Exit immediately if any command fails
         # CONCEPT: 'set -e' is defensive programming for shell scripts.
         # Without it, the script continues even after errors, potentially
         # running later steps on broken output. 'set -e' makes failures loud.

# ── Configuration ─────────────────────────────────────────────────────────────
SRC_DIR="src"
OUT_DIR="out"
JAVAFX_LIB="lib/javafx/lib"

# ── Preflight Checks ──────────────────────────────────────────────────────────
# CONCEPT: Fail Fast — check prerequisites before doing work.
# Giving a clear error message here is much more helpful than letting javac
# fail with a cryptic "package not found" error 10 lines later.

if ! command -v javac &> /dev/null; then
    echo "❌ Error: javac not found. Please install Java 17 or higher."
    echo "   Download from: https://adoptium.net/"
    exit 1
fi

if [ ! -d "$JAVAFX_LIB" ]; then
    echo "❌ Error: JavaFX library not found at $JAVAFX_LIB"
    echo "   Download JavaFX 17 SDK from: https://gluonhq.com/products/javafx/"
    echo "   Unzip it so that lib/javafx/lib/ contains the .jar files."
    exit 1
fi

# ── Create Output Directory ────────────────────────────────────────────────────
# -p: create intermediate directories and don't fail if already exists
mkdir -p "$OUT_DIR"

echo "🔨 Compiling StrataChess..."
echo "   Source:  $SRC_DIR"
echo "   Output:  $OUT_DIR"
echo "   JavaFX:  $JAVAFX_LIB"
echo ""

# ── Find All Source Files ──────────────────────────────────────────────────────
# 'find' recursively locates every .java file under src/.
# We write the list to a temporary file because the argument list can be long.
# Passing thousands of filenames directly to javac can exceed shell limits.
# CONCEPT: find + xargs is a classic Unix pattern for batch processing files.
find "$SRC_DIR" -name "*.java" > /tmp/stratachess_sources.txt

SOURCE_COUNT=$(wc -l < /tmp/stratachess_sources.txt | tr -d ' ')
echo "   Found $SOURCE_COUNT source files."

# ── Compile ────────────────────────────────────────────────────────────────────
# CONCEPT: javac flags explained
#   -d out/                → put compiled .class files in the out/ directory
#   --module-path ...      → where to find JavaFX modules (.jar files)
#   --add-modules ...      → which JavaFX modules to include (controls=UI, media=audio)
#   @/tmp/...              → read source file list from this file (@ syntax)
#
# Why --module-path instead of -classpath for JavaFX?
# JavaFX 11+ uses the Java Module System (JPMS). Modules are loaded via
# --module-path, not the old -classpath. Using -classpath would fail with
# "Error: JavaFX runtime components are missing."

javac \
    -d "$OUT_DIR" \
    --module-path "$JAVAFX_LIB" \
    --add-modules javafx.controls,javafx.media \
    @/tmp/stratachess_sources.txt

echo ""
echo "✅ Compilation successful!"
echo "   Class files written to: $OUT_DIR/"
echo ""
echo "   To start the server (host):   ./run_server.sh"
echo "   To start the client (guest):  ./run_client.sh <host-ip>"
echo "   For local testing (2 windows): ./run_server.sh  then  ./run_client.sh localhost"