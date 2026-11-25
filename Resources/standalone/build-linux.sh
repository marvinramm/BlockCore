#!/bin/bash
# Builds BlockCore for Linux (x86_64):
#   BlockCore-linux-x64.tar.gz   unpack anywhere and start ./BlockCore (no root needed); ./BlockCore --install adds it to the application menu
#
# Everything is included: Java runtime, server, frontend, AVR toolchain (avr-gcc 7.3, the same as on Windows) and avrdude. avrdude is taken from
# the build system (Debian/Ubuntu package "avrdude") together with the libraries it needs that are not part of every Linux system. The release
# workflow builds on Ubuntu 22.04 (glibc 2.35): BlockCore runs on Ubuntu 22.04, Debian 12, Fedora 36, Linux Mint 21 and newer. If the included
# avrdude does not run on a system, the launcher uses an installed avrdude.
#
# Prerequisites (Debian/Ubuntu): maven, java, curl, unzip, python3, avrdude
#   mvn clean install -DskipTests     (in the repository root, before calling this script)
#
# Usage: Resources/standalone/build-linux.sh [output-dir]     (default: target/standalone)

set -euo pipefail

AVR_GCC_URL='https://github.com/ZakKemble/avr-gcc-build/releases/download/v7.3.0-1/avr-gcc-7.3.0-x64-linux.tar.bz2'
AVRDUDE_BIN="${AVRDUDE_BIN:-/usr/bin/avrdude}"
AVRDUDE_CONF="${AVRDUDE_CONF:-/etc/avrdude.conf}"

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
OUT_DIR=$(mkdir -p "${1:-$REPO_DIR/target/standalone}" && cd "${1:-$REPO_DIR/target/standalone}" && pwd)

for tool in curl tar unzip python3 ldd; do
    command -v "$tool" > /dev/null || { echo "missing tool: $tool"; exit 12; }
done
[ -x "$AVRDUDE_BIN" ] && [ -f "$AVRDUDE_CONF" ] || { echo "avrdude not found ($AVRDUDE_BIN, $AVRDUDE_CONF): apt-get install avrdude"; exit 12; }
source "$SCRIPT_DIR/common.sh"

STAGE_PARENT="$OUT_DIR/linux"
STAGE="$STAGE_PARENT/BlockCore"
TMP="$OUT_DIR/tmp-linux"
RSC="$STAGE/ora-cc-rsc/RobotArdu/arduino-resources"
rm -rf "$STAGE_PARENT" "$TMP"
mkdir -p "$STAGE" "$TMP" "$RSC"
echo "== BlockCore $VERSION for Linux"

echo '== Java runtime (jlink: only the modules the server needs, German and English locales, with class data archive)'
unpack_linux_jdk "$TMP/jdk-linux"
"$TMP/jdk-linux/bin/jlink" --add-modules "$JAVA_MODULES" --include-locales=en,de --strip-debug --no-header-files --no-man-pages \
    --compress=zip-0 --generate-cds-archive --output "$STAGE/jre"
[ -x "$STAGE/jre/bin/java" ] || { echo "jlink did not create a runtime"; exit 12; }
[ -f "$STAGE/jre/lib/server/classes.jsa" ] || echo "warning: the runtime has no class data archive (slower start)"
rm -rf "$TMP/jdk-linux"

echo '== server and frontend'
stage_server_and_frontend "$STAGE"
stage_arduino_resources "$RSC" "$TMP"

echo '== AVR toolchain'
mkdir -p "$TMP/gcc"
tar -xjf "$(download "$AVR_GCC_URL")" -C "$TMP/gcc"
mv "$TMP"/gcc/avr-gcc-* "$RSC/avr-gcc"
rm -f "$RSC/avr-gcc/permissions.sh"
trim_avr_gcc "$RSC/avr-gcc"
chmod -R go-w "$RSC/avr-gcc"
"$RSC/avr-gcc/bin/avr-gcc" --version | head -1

echo "== avrdude ($("$AVRDUDE_BIN" -? 2>&1 | grep -io 'version [0-9.]*' | head -1))"
mkdir -p "$RSC/avrdude/bin" "$RSC/avrdude/etc" "$RSC/avrdude/lib"
cp "$AVRDUDE_BIN" "$RSC/avrdude/bin/avrdude.bin"
cp "$AVRDUDE_CONF" "$RSC/avrdude/etc/avrdude.conf"
# the libraries of avrdude, except those every Linux system has (glibc, udev, ...)
ldd "$AVRDUDE_BIN" | awk '/=> \// { print $1 " " $3 }' | while read -r name path; do
    case "$name" in
        libc.so* | libm.so* | libdl.so* | libpthread.so* | librt.so* | ld-linux* | libudev.so* | libcap.so* | libsystemd.so*) ;;
        *) cp -L "$path" "$RSC/avrdude/lib/$name" ;;
    esac
done
cat > "$RSC/avrdude/bin/avrdude" << 'EOF'
#!/bin/sh
# avrdude with the libraries next to it (see build-linux.sh)
HERE=$(dirname "$(readlink -f "$0")")
LD_LIBRARY_PATH="$HERE/../lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" exec "$HERE/avrdude.bin" "$@"
EOF
chmod +x "$RSC/avrdude/bin/avrdude"
"$RSC/avrdude/bin/avrdude" "-?" > /dev/null 2>&1 || { echo "the packaged avrdude does not run"; exit 12; }

echo '== launcher'
cp "$SCRIPT_DIR/linux/BlockCore" "$STAGE/BlockCore"
chmod +x "$STAGE/BlockCore"
cp "$SCRIPT_DIR/branding/web/icon-512.png" "$STAGE/blockcore.png"
cp "$SCRIPT_DIR/linux/README.txt" "$STAGE/README.txt"
stage_licences "$STAGE"

echo '== archive'
rm -f "$OUT_DIR/BlockCore-linux-x64.tar.gz"
tar -C "$STAGE_PARENT" -czf "$OUT_DIR/BlockCore-linux-x64.tar.gz" --owner=0 --group=0 BlockCore

rm -rf "$TMP"
ls -la "$OUT_DIR/BlockCore-linux-x64.tar.gz"
