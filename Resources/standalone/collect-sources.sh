#!/bin/bash
# Collects the source code of the GPL / LGPL / CDDL / EPL / MPL software that the BlockCore packages contain, into
#   BlockCore-third-party-sources.tar
# which is attached to every release next to the packages. This makes the "complete corresponding source code" available from the same place
# as the binaries (GPL 2.0 section 3, GPL 3.0 section 6, LGPL 2.1 section 6). See THIRD-PARTY-NOTICES.md.
#
# Contents:
#   toolchain/   GCC 7.3.0, Binutils 2.30, AVR Libc 2.0.0, GMP, MPFR, MPC, isl (as used by the avr-gcc build of Zak Kemble) and that build script
#   avrdude/     AVRDUDE 7.3 (Windows package)
#   ubuntu/      Ubuntu source packages of the avrdude and the libraries in the Linux package (needs deb-src entries, see the release workflow)
#   java/        OpenJDK 21.0.5+11 (Eclipse Temurin) and the -sources.jar of the Java libraries
#   SOURCES.txt  where every file comes from, with its SHA-256
#
# Run after build-linux.sh (the libraries of the Linux avrdude are read from its stage) and after "mvn install".
# Usage: Resources/standalone/collect-sources.sh [output-dir]     (default: target/standalone)

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
OUT_DIR=$(mkdir -p "${1:-$REPO_DIR/target/standalone}" && cd "${1:-$REPO_DIR/target/standalone}" && pwd)
source "$SCRIPT_DIR/common.sh"

TOOLCHAIN_SOURCES=(
    'https://ftp.gnu.org/gnu/gcc/gcc-7.3.0/gcc-7.3.0.tar.xz'
    'https://ftp.gnu.org/gnu/binutils/binutils-2.30.tar.xz'
    'https://download.savannah.gnu.org/releases/avr-libc/avr-libc-2.0.0.tar.bz2'
    'https://gcc.gnu.org/pub/gcc/infrastructure/gmp-6.1.0.tar.bz2'
    'https://gcc.gnu.org/pub/gcc/infrastructure/mpfr-3.1.4.tar.bz2'
    'https://gcc.gnu.org/pub/gcc/infrastructure/mpc-1.0.3.tar.gz'
    'https://gcc.gnu.org/pub/gcc/infrastructure/isl-0.16.1.tar.bz2'
    'https://github.com/ZakKemble/avr-gcc-build/archive/refs/tags/v7.3.0-1.tar.gz'
)
AVRDUDE_SOURCES=('https://github.com/avrdudes/avrdude/archive/refs/tags/v7.3.tar.gz')
JAVA_SOURCES=('https://github.com/adoptium/jdk21u/archive/refs/tags/jdk-21.0.5%2B11.tar.gz')
LINUX_AVRDUDE_LIB="$OUT_DIR/linux/BlockCore/ora-cc-rsc/RobotArdu/arduino-resources/avrdude/lib"
AVRDUDE_BIN="${AVRDUDE_BIN:-/usr/bin/avrdude}"

SRC="$OUT_DIR/sources/BlockCore-third-party-sources"
rm -rf "$OUT_DIR/sources"
mkdir -p "$SRC/toolchain" "$SRC/avrdude" "$SRC/ubuntu" "$SRC/java"
INDEX="$SRC/SOURCES.txt"
{
    echo "Source code of the third-party software in BlockCore $VERSION (see THIRD-PARTY-NOTICES.md)."
    echo
} > "$INDEX"

add() { # directory url [file name]
    local file
    file=$(download "$2")
    local name="${3:-$(basename "$file")}"
    ln -f "$file" "$SRC/$1/$name" 2> /dev/null || cp "$file" "$SRC/$1/$name"
    printf '%s/%s\n    from %s\n    sha256 %s\n' "$1" "$name" "$2" "$(sha256sum "$file" | cut -d' ' -f1)" >> "$INDEX"
}

echo '== toolchain'
for url in "${TOOLCHAIN_SOURCES[@]}"; do
    case "$url" in
        *avr-gcc-build*) add toolchain "$url" avr-gcc-build-v7.3.0-1.tar.gz ;;
        *) add toolchain "$url" ;;
    esac
done
echo '== avrdude'
add avrdude "${AVRDUDE_SOURCES[0]}" avrdude-7.3.tar.gz
echo '== OpenJDK'
add java "${JAVA_SOURCES[0]}" jdk21u-jdk-21.0.5+11.tar.gz

echo '== Ubuntu source packages of the Linux avrdude and its libraries'
[ -d "$LINUX_AVRDUDE_LIB" ] || { echo "$LINUX_AVRDUDE_LIB not found: run build-linux.sh first"; exit 12; }
source_package() { # file -> "source=version" of the Debian package that contains it
    local path package
    for path in "$1" "$(readlink -f "$1")" "/usr$1" "${1#/usr}"; do
        package=$(dpkg -S "$path" 2> /dev/null | grep -v "^diversion" | head -1 | cut -d: -f1) && [ -n "$package" ] && break
    done
    [ -n "$package" ] || return 1
    dpkg-query -W -f='${source:Package}=${source:Version}\n' "$package" | head -1
}
packages=("$(source_package "$AVRDUDE_BIN")")
while read -r name path; do
    [ -f "$LINUX_AVRDUDE_LIB/$name" ] && packages+=("$(source_package "$path")")
done < <(ldd "$AVRDUDE_BIN" | awk '/=> \// { print $1 " " $3 }')
mapfile -t packages < <(printf '%s\n' "${packages[@]}" | sort -u)
echo "${packages[*]}"
(cd "$SRC/ubuntu" && apt-get source --download-only "${packages[@]}" > /dev/null) || { echo "apt-get source failed: are deb-src entries configured?"; exit 12; }
for file in "$SRC"/ubuntu/*; do
    printf 'ubuntu/%s\n    from the Ubuntu source package archive (apt-get source)\n    sha256 %s\n' "$(basename "$file")" "$(sha256sum "$file" | cut -d' ' -f1)" >> "$INDEX"
done

echo '== Java libraries (-sources.jar from Maven Central)'
(cd "$REPO_DIR" && mvn --batch-mode -q dependency:copy-dependencies -pl OpenRobertaServer -DincludeScope=runtime -Dclassifier=sources \
    -Dmdep.failOnMissingClassifierArtifact=false -DexcludeGroupIds=de.fhg.iais.openroberta -DoutputDirectory="$SRC/java/libraries")
echo "java/libraries/*-sources.jar" >> "$INDEX"
echo "    from Maven Central (the source jars of the libraries in lib/, where the project publishes one)" >> "$INDEX"

echo '== archive'
rm -f "$OUT_DIR/BlockCore-third-party-sources.tar"
tar -C "$OUT_DIR/sources" -cf "$OUT_DIR/BlockCore-third-party-sources.tar" --owner=0 --group=0 BlockCore-third-party-sources
rm -rf "$OUT_DIR/sources"
ls -la "$OUT_DIR/BlockCore-third-party-sources.tar"
