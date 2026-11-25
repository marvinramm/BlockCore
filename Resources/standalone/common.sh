#!/bin/bash
# Shared part of build-windows.sh and build-linux.sh (sourced, not called). Expects:
#   SCRIPT_DIR  this directory
#   REPO_DIR    the repository root
#   OUT_DIR     the output directory
# and sets DOWNLOAD_DIR, VERSION, JDK_LINUX_URL, JAVA_MODULES, ARDUINO_RESOURCES_URL, ROBOTS.

# The Java runtime is linked with jlink from the Temurin JDK. To link a runtime for Windows on Linux, jlink must be of exactly the same version.
JDK_LINUX_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz'
# modules needed by the server (Jetty, Jersey, Guice, Hibernate, HSQLDB, JAXB, jSerialComm)
JAVA_MODULES='java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.net,jdk.unsupported,jdk.xml.dom,jdk.zipfs'
ARDUINO_RESOURCES_URL='https://github.com/OpenRoberta/arduino-resources/releases/download/v2023.04.05/arduino-resources.zip'
ROBOTS='uno nano mega'
# the Arduino libraries (headers) of the AVR boards: the code generator includes these (Wire, SPI, ... are in avr/)
ARDUINO_LIBRARIES='avr RobertaFunctions ArduinoSTL Adafruit_BusIO Adafruit_GFX_Library Adafruit_SSD1306 Adafruit_Unified_Sensor DHT_sensor_library IRremote LiquidCrystal LiquidCrystal_I2C MFRC522 Servo Stepper'

DOWNLOAD_DIR="$OUT_DIR/downloads"
mkdir -p "$DOWNLOAD_DIR"

SERVER_JARS="$REPO_DIR/OpenRobertaServer/target/resources"
[ -d "$SERVER_JARS" ] || { echo "$SERVER_JARS not found. Run 'mvn clean install -DskipTests' first."; exit 12; }

# version: set by the release workflow (e.g. 1.0.15), otherwise "<VERSION>-dev"
VERSION="${BLOCKCORE_VERSION:-$(tr -d '[:space:]' < "$REPO_DIR/VERSION")-dev}"

download() { # url -> path of the cached file
    local file="$DOWNLOAD_DIR/$(basename "${1//%2B/+}")"
    if [ ! -s "$file" ]; then
        echo "downloading $1" >&2
        curl -fsSL --retry 4 -o "$file.part" "$1"
        mv "$file.part" "$file"
    fi
    echo "$file"
}

# the Linux JDK (for jlink), unpacked to $1
unpack_linux_jdk() {
    mkdir -p "$1"
    tar -xzf "$(download "$JDK_LINUX_URL")" -C "$1" --strip-components=1
}

# server jars and the frontend with the BlockCore branding and user interface -> $1 (the stage directory)
stage_server_and_frontend() {
    local stage="$1"
    mkdir -p "$stage/lib"
    cp "$SERVER_JARS"/*.jar "$stage/lib/"
    cp -r "$REPO_DIR/OpenRobertaServer/staticResources" "$stage/staticResources"

    echo '== branding (BlockCore instead of the Open Roberta name and logo) and the simplified BlockCore user interface'
    local web="$stage/staticResources"
    cp "$SCRIPT_DIR/branding/logo-word.svg" "$web/css/svg/logo-word.svg"
    cp "$SCRIPT_DIR/branding/icon-144.png" "$web/css/img/Nepo_N.png"
    cp "$SCRIPT_DIR/branding/blockcore.ico" "$web/css/img/NEPO.ico"
    mkdir -p "$web/blockcore"
    cp "$SCRIPT_DIR"/branding/web/*.js "$SCRIPT_DIR"/branding/web/blockcore.css "$SCRIPT_DIR"/branding/web/icon-*.png "$SCRIPT_DIR"/branding/web/*.svg "$web/blockcore/"
    cp "$SCRIPT_DIR/branding/web/manifest.webapp.json" "$web/manifest.webapp.json"
    python3 "$SCRIPT_DIR/branding/modules/generate.py" "$web/blockcore/modules"
    printf '{"version":"%s"}\n' "$VERSION" > "$web/blockcore/version.json"
    # the licences, linked from the settings dialog (works offline; .txt: shown as text by the browser; ASCII only, no charset is sent)
    cp "$REPO_DIR/THIRD-PARTY-NOTICES.md" "$web/blockcore/THIRD-PARTY-NOTICES.txt"
    sed -i -e 's|<title>Open Roberta Lab</title>|<title>BlockCore</title>|' \
           -e "s|alt='Open Roberta Lab logo'|alt='BlockCore logo'|" \
           -e "s|</head>|<script src='/blockcore/blockcore-theme.js'></script>\\n<link href='/blockcore/blockcore.css' rel='stylesheet' type='text/css'>\\n<script defer src='/blockcore/blockcore.js'></script>\\n</head>|" \
           "$web/index.html"
    grep -q '<title>BlockCore</title>' "$web/index.html" || { echo "branding of index.html failed"; exit 12; }
    grep -q '/blockcore/blockcore.js' "$web/index.html" || { echo "injecting blockcore.js failed"; exit 12; }
}

# Arduino cores, libraries and includes of the AVR boards -> $1 (arduino-resources directory); $2 is a temporary directory
stage_arduino_resources() {
    local rsc="$1" tmp="$2"
    echo '== Arduino resources (only the AVR boards)'
    unzip -q "$(download "$ARDUINO_RESOURCES_URL")" -d "$tmp/ardu"
    local ardu="$tmp/ardu/arduino-resources"
    mkdir -p "$rsc/lib" "$rsc/core" "$rsc/hardware/arduino" "$rsc/includes"
    # only the headers the code of Uno, Nano and Mega can include (the archive has the libraries of all Open Roberta robots, some of them
    # under licences that do not fit BlockCore). See THIRD-PARTY-NOTICES.md
    for library in $ARDUINO_LIBRARIES; do
        cp -r "$ardu/includes/$library" "$rsc/includes/$library"
    done
    for robot in $ROBOTS; do
        cp -r "$ardu/lib/$robot" "$rsc/lib/$robot"
        cp -r "$ardu/core/$robot" "$rsc/core/$robot"
    done
    cp -r "$ardu/hardware/arduino/avr" "$rsc/hardware/arduino/avr"
    rm -rf "$tmp/ardu"
}

# removes what the AVR toolchain in $1 does not need for Uno, Nano and Mega
trim_avr_gcc() {
    local gcc="$1"
    rm -rf "$gcc/doc" "$gcc/share/doc" "$gcc/share/man" "$gcc/share/info" "$gcc/bin/avrdude"* "$gcc/etc/avrdude.conf" "$gcc/bin/make.exe"
    # only the libraries of the architectures used: avr5 (ATmega328P: Uno, Nano) and avr6 (ATmega2560: Mega)
    for arch in avr25 avr3 avr31 avr35 avr4 avr51 avrtiny avrxmega2 avrxmega3 avrxmega4 avrxmega5 avrxmega6 avrxmega7 tiny-stack; do
        rm -rf "$gcc/lib/gcc/avr/7.3.0/$arch" "$gcc/avr/lib/$arch"
    done
    [ -d "$gcc/lib/gcc/avr/7.3.0/avr5" ] && [ -d "$gcc/avr/lib/avr6" ] || { echo "avr5/avr6 libraries missing"; exit 12; }
    # no link time optimisation (the cores contain no LTO code), no gcc plugins, no debugging and profiling tools
    local exec="$gcc/libexec/gcc/avr/7.3.0"
    rm -rf "$exec"/lto1* "$exec"/lto-wrapper* "$exec/plugin" "$exec/install-tools" "$gcc/lib/gcc/avr/7.3.0/plugin" "$gcc/lib/gcc/avr/7.3.0/install-tools"
    rm -f "$gcc"/bin/avr-gcov* "$gcc"/bin/avr-gprof* "$gcc"/bin/avr-addr2line* "$gcc"/bin/avr-c++filt* "$gcc"/bin/avr-elfedit* "$gcc"/bin/avr-readelf* \
        "$gcc"/bin/avr-strings* "$gcc"/bin/avr-man* "$gcc"/bin/avr-gcc-7.3.0* "$gcc"/bin/avr-ld.bfd* "$gcc"/bin/libusb0.dll
    [ -n "$(ls "$exec"/cc1plus*)" ] || { echo "cc1plus missing"; exit 12; }
}

# licence files -> $1
stage_licences() {
    cp "$REPO_DIR/LICENSE" "$1/LICENSE.txt"
    cp "$REPO_DIR/NOTICE" "$1/NOTICE.txt"
    cp "$REPO_DIR/THIRD-PARTY-NOTICES.md" "$1/THIRD-PARTY-NOTICES.md"
}
