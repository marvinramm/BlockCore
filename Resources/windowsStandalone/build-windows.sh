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
# Prerequisites (Debian/Ubuntu): maven, java, curl, unzip, zip, gcc-mingw-w64-x86-64, nsis
#   mvn clean install -DskipTests     (in the repository root, before calling this script)
#
# Usage: Resources/windowsStandalone/build-windows.sh [output-dir]     (default: target/windowsStandalone)

set -euo pipefail

# Java runtime: a small runtime is linked with jlink from the Windows JDK; jlink must be of exactly the same version (Linux JDK)
JDK_LINUX_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz'
JDK_WINDOWS_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_windows_hotspot_21.0.5_11.zip'
# modules needed by the server (Jetty, Jersey, Guice, Hibernate, HSQLDB, JAXB, jSerialComm)
JAVA_MODULES='java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.net,jdk.unsupported,jdk.xml.dom,jdk.zipfs'
AVR_GCC_URL='https://github.com/ZakKemble/avr-gcc-build/releases/download/v7.3.0-1/avr-gcc-7.3.0-x64-mingw.zip'
AVRDUDE_URL='https://github.com/avrdudes/avrdude/releases/download/v7.3/avrdude-v7.3-windows-x64.zip'
ARDUINO_RESOURCES_URL='https://github.com/OpenRoberta/arduino-resources/releases/download/v2023.04.05/arduino-resources.zip'
ROBOTS='uno nano mega'

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
OUT_DIR=$(mkdir -p "${1:-$REPO_DIR/target/windowsStandalone}" && cd "${1:-$REPO_DIR/target/windowsStandalone}" && pwd)
DOWNLOAD_DIR="$OUT_DIR/downloads"
STAGE="$OUT_DIR/BlockCore"
RSC="$STAGE/ora-cc-rsc/RobotArdu/arduino-resources"

for tool in curl tar unzip zip x86_64-w64-mingw32-gcc x86_64-w64-mingw32-windres makensis; do
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

echo '== Java runtime (jlink: only the modules the server needs, German and English locales)'
mkdir -p "$OUT_DIR/tmp/jdk-linux" "$OUT_DIR/tmp/jdk-windows"
tar -xzf "$(download "$JDK_LINUX_URL")" -C "$OUT_DIR/tmp/jdk-linux" --strip-components=1
unzip -q "$(download "$JDK_WINDOWS_URL")" -d "$OUT_DIR/tmp/jdk-windows"
"$OUT_DIR/tmp/jdk-linux/bin/jlink" --module-path "$(echo "$OUT_DIR"/tmp/jdk-windows/*/jmods)" --add-modules "$JAVA_MODULES" \
    --include-locales=en,de --strip-debug --no-header-files --no-man-pages --compress=zip-0 --output "$STAGE/jre"
[ -f "$STAGE/jre/bin/java.exe" ] || { echo "jlink did not create a Windows runtime"; exit 12; }
rm -rf "$OUT_DIR/tmp/jdk-linux" "$OUT_DIR/tmp/jdk-windows"

echo '== server and frontend'
mkdir -p "$STAGE/lib"
cp "$SERVER_JARS"/*.jar "$STAGE/lib/"
cp -r "$REPO_DIR/OpenRobertaServer/staticResources" "$STAGE/staticResources"

# version: set by the release workflow (e.g. 1.0.15), otherwise "<VERSION>-dev"
VERSION="${BLOCKCORE_VERSION:-$(tr -d '[:space:]' < "$REPO_DIR/VERSION")-dev}"
echo "== version $VERSION"

echo '== branding (BlockCore instead of the Open Roberta name and logo) and the simplified BlockCore user interface'
WEB="$STAGE/staticResources"
cp "$SCRIPT_DIR/branding/logo-word.svg" "$WEB/css/svg/logo-word.svg"
cp "$SCRIPT_DIR/branding/icon-144.png" "$WEB/css/img/Nepo_N.png"
cp "$SCRIPT_DIR/branding/blockcore.ico" "$WEB/css/img/NEPO.ico"
mkdir -p "$WEB/blockcore"
cp "$SCRIPT_DIR"/branding/web/blockcore.js "$SCRIPT_DIR"/branding/web/blockcore.css "$SCRIPT_DIR"/branding/web/icon-*.png "$SCRIPT_DIR"/branding/web/*.svg "$WEB/blockcore/"
cp "$SCRIPT_DIR/branding/web/manifest.webapp.json" "$WEB/manifest.webapp.json"
printf '{"version":"%s"}\n' "$VERSION" > "$WEB/blockcore/version.json"
sed -i -e 's|<title>Open Roberta Lab</title>|<title>BlockCore</title>|' \
       -e "s|alt='Open Roberta Lab logo'|alt='BlockCore logo'|" \
       -e "s|</head>|<link href='/blockcore/blockcore.css' rel='stylesheet' type='text/css'>\\n<script defer src='/blockcore/blockcore.js'></script>\\n</head>|" \
       "$WEB/index.html"
grep -q '<title>BlockCore</title>' "$WEB/index.html" || { echo "branding of index.html failed"; exit 12; }
grep -q '/blockcore/blockcore.js' "$WEB/index.html" || { echo "injecting blockcore.js failed"; exit 12; }

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
# only the libraries of the architectures used: avr5 (ATmega328P: Uno, Nano) and avr6 (ATmega2560: Mega)
for arch in avr25 avr3 avr31 avr35 avr4 avr51 avrtiny avrxmega2 avrxmega3 avrxmega4 avrxmega5 avrxmega6 avrxmega7 tiny-stack; do
    rm -rf "$RSC/avr-gcc/lib/gcc/avr/7.3.0/$arch" "$RSC/avr-gcc/avr/lib/$arch"
done
[ -d "$RSC/avr-gcc/lib/gcc/avr/7.3.0/avr5" ] && [ -d "$RSC/avr-gcc/avr/lib/avr6" ] || { echo "avr5/avr6 libraries missing"; exit 12; }

echo '== avrdude'
mkdir -p "$RSC/avrdude/bin" "$RSC/avrdude/etc"
unzip -q "$(download "$AVRDUDE_URL")" -d "$OUT_DIR/tmp/avrdude"
cp "$OUT_DIR/tmp/avrdude/avrdude.exe" "$RSC/avrdude/bin/"
cp "$OUT_DIR/tmp/avrdude/avrdude.conf" "$RSC/avrdude/etc/"

echo '== launcher'
cp "$SCRIPT_DIR/branding/blockcore.ico" "$OUT_DIR/tmp/BlockCore.ico"
printf '1 ICON "BlockCore.ico"\n' > "$OUT_DIR/tmp/launcher.rc"
(cd "$OUT_DIR/tmp" && x86_64-w64-mingw32-windres launcher.rc -O coff -o launcher.res)
x86_64-w64-mingw32-gcc -O2 -Wall -Wextra -municode -mwindows -static -o "$STAGE/BlockCore.exe" "$SCRIPT_DIR/launcher.c" "$OUT_DIR/tmp/launcher.res" -lws2_32 -lshell32 -luser32 -lgdi32
cp "$SCRIPT_DIR/README.txt" "$STAGE/README.txt"

echo '== installer'
cp "$OUT_DIR/tmp/BlockCore.ico" "$STAGE/BlockCore.ico"
makensis -V2 -DVERSION="$VERSION" -DSTAGE="$STAGE" -DOUTFILE="$OUT_DIR/BlockCore-Setup.exe" "$SCRIPT_DIR/installer.nsi"

echo '== portable zip'
rm -f "$OUT_DIR/BlockCore-portable.zip"
(cd "$OUT_DIR" && zip -qr -9 BlockCore-portable.zip BlockCore)

rm -rf "$OUT_DIR/tmp"
ls -la "$OUT_DIR"/BlockCore-Setup.exe "$OUT_DIR"/BlockCore-portable.zip
