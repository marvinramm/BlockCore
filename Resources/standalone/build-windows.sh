#!/bin/bash
# Builds BlockCore, the standalone block programming lab for Arduino on Windows, on Linux:
#   BlockCore-Setup.exe     installer (no admin rights needed)
#   BlockCore-portable.zip  the same files, unzip and start BlockCore.exe
#
# The lab runs completely on the Windows computer: server, Arduino compiler (avr-gcc) and upload (avrdude). The robot is selected by its COM port
# in the lab ("robot -> connect"). Supported: Arduino Uno, Nano and Mega (incl. the TCS34725 colour sensor and the L293D motor shield).
#
# BlockCore is based on the Open Roberta Lab of Fraunhofer IAIS. "Open Roberta" and its logos are trademarks of the Fraunhofer Society and must not
# be used for modified versions (see NOTICE). Therefore the package is branded as BlockCore (branding/).
#
# Prerequisites (Debian/Ubuntu): maven, java, curl, unzip, zip, python3, gcc-mingw-w64-x86-64, nsis
#   mvn clean install -DskipTests     (in the repository root, before calling this script)
#
# Usage: Resources/standalone/build-windows.sh [output-dir]     (default: target/standalone)

set -euo pipefail

JDK_WINDOWS_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_windows_hotspot_21.0.5_11.zip'
AVR_GCC_URL='https://github.com/ZakKemble/avr-gcc-build/releases/download/v7.3.0-1/avr-gcc-7.3.0-x64-mingw.zip'
AVRDUDE_URL='https://github.com/avrdudes/avrdude/releases/download/v7.3/avrdude-v7.3-windows-x64.zip'

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
OUT_DIR=$(mkdir -p "${1:-$REPO_DIR/target/standalone}" && cd "${1:-$REPO_DIR/target/standalone}" && pwd)

for tool in curl tar unzip zip python3 x86_64-w64-mingw32-gcc x86_64-w64-mingw32-windres makensis; do
    command -v "$tool" > /dev/null || { echo "missing tool: $tool"; exit 12; }
done
source "$SCRIPT_DIR/common.sh"

STAGE="$OUT_DIR/BlockCore"
TMP="$OUT_DIR/tmp-windows"
RSC="$STAGE/ora-cc-rsc/RobotArdu/arduino-resources"
rm -rf "$STAGE" "$TMP"
mkdir -p "$STAGE" "$TMP" "$RSC"
echo "== BlockCore $VERSION for Windows"

echo '== Java runtime (jlink: only the modules the server needs, German and English locales)'
unpack_linux_jdk "$TMP/jdk-linux"
mkdir -p "$TMP/jdk-windows"
unzip -q "$(download "$JDK_WINDOWS_URL")" -d "$TMP/jdk-windows"
# the class data archive of the runtime cannot be generated here (it needs the Windows JVM): the installer and BlockCore.exe create it
"$TMP/jdk-linux/bin/jlink" --module-path "$(echo "$TMP"/jdk-windows/*/jmods)" --add-modules "$JAVA_MODULES" \
    --include-locales=en,de --strip-debug --no-header-files --no-man-pages --compress=zip-0 --output "$STAGE/jre"
[ -f "$STAGE/jre/bin/java.exe" ] || { echo "jlink did not create a Windows runtime"; exit 12; }
rm -rf "$TMP/jdk-linux" "$TMP/jdk-windows"

echo '== server and frontend'
stage_server_and_frontend "$STAGE"
stage_arduino_resources "$RSC" "$TMP"

echo '== AVR toolchain'
unzip -q "$(download "$AVR_GCC_URL")" -d "$TMP/gcc"
mv "$TMP"/gcc/avr-gcc-* "$RSC/avr-gcc"
trim_avr_gcc "$RSC/avr-gcc"

echo '== avrdude'
mkdir -p "$RSC/avrdude/bin" "$RSC/avrdude/etc"
unzip -q "$(download "$AVRDUDE_URL")" -d "$TMP/avrdude"
cp "$TMP/avrdude/avrdude.exe" "$RSC/avrdude/bin/"
cp "$TMP/avrdude/avrdude.conf" "$RSC/avrdude/etc/"

echo '== launcher'
cp "$SCRIPT_DIR/branding/blockcore.ico" "$TMP/BlockCore.ico"
printf '1 ICON "BlockCore.ico"\n' > "$TMP/launcher.rc"
(cd "$TMP" && x86_64-w64-mingw32-windres launcher.rc -O coff -o launcher.res)
x86_64-w64-mingw32-gcc -O2 -Wall -Wextra -municode -mwindows -static -o "$STAGE/BlockCore.exe" "$SCRIPT_DIR/launcher.c" "$TMP/launcher.res" -lws2_32 -lshell32 -luser32 -lgdi32
cp "$SCRIPT_DIR/README.txt" "$STAGE/README.txt"
stage_licences "$STAGE"

echo '== installer'
cp "$TMP/BlockCore.ico" "$STAGE/BlockCore.ico"
makensis -V2 -DVERSION="$VERSION" -DSTAGE="$STAGE" -DOUTFILE="$OUT_DIR/BlockCore-Setup.exe" "$SCRIPT_DIR/installer.nsi"

echo '== portable zip'
rm -f "$OUT_DIR/BlockCore-portable.zip"
(cd "$OUT_DIR" && zip -qr -9 BlockCore-portable.zip BlockCore)

rm -rf "$TMP"
ls -la "$OUT_DIR"/BlockCore-Setup.exe "$OUT_DIR"/BlockCore-portable.zip
