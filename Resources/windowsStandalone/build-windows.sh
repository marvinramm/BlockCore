#!/bin/bash
# Builds the standalone Open Roberta Lab for Windows on Linux:
#   OpenRobertaLab-Setup.exe   installer (no admin rights needed)
#   OpenRobertaLab-portable.zip  the same files, unzip and start OpenRobertaLab.exe
#
# The lab runs completely on the Windows computer: server, Arduino compiler (avr-gcc) and upload (avrdude). The robot is selected by its COM port
# in the lab ("Roboter -> Verbinden"). Supported: Arduino Uno, Nano and Mega (incl. the TCS34725 colour sensor and the L293D motor shield).
#
# Prerequisites (Debian/Ubuntu): maven, java, curl, unzip, zip, gcc-mingw-w64-x86-64, nsis
#   mvn clean install -DskipTests     (in the repository root, before calling this script)
#
# Usage: Resources/windowsStandalone/build-windows.sh [output-dir]     (default: target/windowsStandalone)

set -euo pipefail

JRE_URL='https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.25%2B9/OpenJDK11U-jre_x64_windows_hotspot_11.0.25_9.zip'
AVR_GCC_URL='https://github.com/ZakKemble/avr-gcc-build/releases/download/v7.3.0-1/avr-gcc-7.3.0-x64-mingw.zip'
AVRDUDE_URL='https://github.com/avrdudes/avrdude/releases/download/v7.3/avrdude-v7.3-windows-x64.zip'
ARDUINO_RESOURCES_URL='https://github.com/OpenRoberta/arduino-resources/releases/download/v2023.04.05/arduino-resources.zip'
ROBOTS='uno nano mega'

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
OUT_DIR=$(mkdir -p "${1:-$REPO_DIR/target/windowsStandalone}" && cd "${1:-$REPO_DIR/target/windowsStandalone}" && pwd)
DOWNLOAD_DIR="$OUT_DIR/downloads"
STAGE="$OUT_DIR/OpenRobertaLab"
RSC="$STAGE/ora-cc-rsc/RobotArdu/arduino-resources"

for tool in curl unzip zip x86_64-w64-mingw32-gcc x86_64-w64-mingw32-windres makensis; do
    command -v "$tool" > /dev/null || { echo "missing tool: $tool"; exit 12; }
done
SERVER_JARS="$REPO_DIR/OpenRobertaServer/target/resources"
[ -d "$SERVER_JARS" ] || { echo "$SERVER_JARS not found. Run 'mvn clean install -DskipTests' first."; exit 12; }

download() { # url -> path of the cached file
    local file="$DOWNLOAD_DIR/$(basename "${1//%2B/+}")"
    if [ ! -s "$file" ]; then
        echo "downloading $1" >&2
        curl -fsSL --retry 4 -o "$file.part" "$1"
        mv "$file.part" "$file"
    fi
    echo "$file"
}

mkdir -p "$DOWNLOAD_DIR"
rm -rf "$STAGE" "$OUT_DIR/tmp"
mkdir -p "$STAGE" "$OUT_DIR/tmp" "$RSC"

echo '== Java runtime'
unzip -q "$(download "$JRE_URL")" -d "$OUT_DIR/tmp/jre"
mv "$OUT_DIR"/tmp/jre/* "$STAGE/jre"

echo '== server and frontend'
mkdir -p "$STAGE/lib"
cp "$SERVER_JARS"/*.jar "$STAGE/lib/"
cp -r "$REPO_DIR/OpenRobertaServer/staticResources" "$STAGE/staticResources"

echo '== Arduino resources (only the AVR boards)'
unzip -q "$(download "$ARDUINO_RESOURCES_URL")" -d "$OUT_DIR/tmp/ardu"
ARDU="$OUT_DIR/tmp/ardu/arduino-resources"
cp -r "$ARDU/includes" "$RSC/includes"
mkdir -p "$RSC/lib" "$RSC/core" "$RSC/hardware/arduino"
for robot in $ROBOTS; do
    cp -r "$ARDU/lib/$robot" "$RSC/lib/$robot"
    cp -r "$ARDU/core/$robot" "$RSC/core/$robot"
done
cp -r "$ARDU/hardware/arduino/avr" "$RSC/hardware/arduino/avr"

echo '== AVR toolchain'
unzip -q "$(download "$AVR_GCC_URL")" -d "$OUT_DIR/tmp/gcc"
mv "$OUT_DIR"/tmp/gcc/avr-gcc-* "$RSC/avr-gcc"
rm -rf "$RSC/avr-gcc/doc" "$RSC/avr-gcc/share/doc" "$RSC/avr-gcc/share/man" "$RSC/avr-gcc/share/info" "$RSC/avr-gcc/bin/avrdude"* "$RSC/avr-gcc/etc/avrdude.conf" "$RSC/avr-gcc/bin/make.exe"

echo '== avrdude'
mkdir -p "$RSC/avrdude/bin" "$RSC/avrdude/etc"
unzip -q "$(download "$AVRDUDE_URL")" -d "$OUT_DIR/tmp/avrdude"
cp "$OUT_DIR/tmp/avrdude/avrdude.exe" "$RSC/avrdude/bin/"
cp "$OUT_DIR/tmp/avrdude/avrdude.conf" "$RSC/avrdude/etc/"

echo '== launcher'
cp "$REPO_DIR/OpenRobertaServer/staticResources/css/img/NEPO.ico" "$OUT_DIR/tmp/NEPO.ico"
printf '1 ICON "NEPO.ico"\n' > "$OUT_DIR/tmp/launcher.rc"
(cd "$OUT_DIR/tmp" && x86_64-w64-mingw32-windres launcher.rc -O coff -o launcher.res)
x86_64-w64-mingw32-gcc -O2 -Wall -municode -static -o "$STAGE/OpenRobertaLab.exe" "$SCRIPT_DIR/launcher.c" "$OUT_DIR/tmp/launcher.res" -lws2_32 -lshell32
cp "$SCRIPT_DIR/LIESMICH.txt" "$STAGE/LIESMICH.txt"

echo '== installer'
cp "$OUT_DIR/tmp/NEPO.ico" "$STAGE/NEPO.ico"
makensis -V2 -DSTAGE="$STAGE" -DOUTFILE="$OUT_DIR/OpenRobertaLab-Setup.exe" "$SCRIPT_DIR/installer.nsi"

echo '== portable zip'
rm -f "$OUT_DIR/OpenRobertaLab-portable.zip"
(cd "$OUT_DIR" && zip -qr -9 OpenRobertaLab-portable.zip OpenRobertaLab)

rm -rf "$OUT_DIR/tmp"
ls -la "$OUT_DIR"/OpenRobertaLab-Setup.exe "$OUT_DIR"/OpenRobertaLab-portable.zip
