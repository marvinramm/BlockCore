# Third-party software in BlockCore

BlockCore ((c) 2026 Marvin Ramm, [Apache License 2.0](LICENSE)) contains and ships software of other authors. This file lists it with its
licence. The licence texts are linked; the packages (installer, portable zip, Linux archive) contain this file, [`LICENSE`](LICENSE) and
[`NOTICE`](NOTICE).

Thank you to all these projects.

## Based on

| Component | Licence | Source |
|---|---|---|
| Open Roberta Lab (server, robot plugins, frontend), (c) Fraunhofer IAIS | Apache 2.0 | https://github.com/OpenRoberta/openroberta-lab |
| Blockly (fork of Open Roberta), (c) Google LLC, Fraunhofer IAIS | Apache 2.0 | https://github.com/OpenRoberta/blockly |
| Arduino resources of Open Roberta (headers, precompiled cores and libraries) | see the Arduino libraries below | https://github.com/OpenRoberta/arduino-resources |

"Open Roberta" and its logos are trademarks of the Fraunhofer Society and are not used by BlockCore (see [`NOTICE`](NOTICE)). "Arduino" is a
trademark of Arduino SA; BlockCore is not affiliated with or endorsed by Arduino SA.

The drivers for the TCS34725 colour sensor, the L293D motor shield, the MPU-6050 gyroscope and the DS18B20 thermometer
(`RobotArdu/src/main/resources/arduino/`) were written for BlockCore and are licensed under Apache 2.0.

## Programs shipped in the packages

| Component | Version | Licence | Source |
|---|---|---|---|
| Eclipse Temurin (OpenJDK) Java runtime, reduced with jlink | 21.0.5+11 | GPL 2.0 with Classpath Exception | https://github.com/adoptium/temurin21-binaries, sources: https://github.com/adoptium/jdk21u |
| avr-gcc (GCC), build of Zak Kemble | 7.3.0 | GPL 3.0 with GCC Runtime Library Exception | https://github.com/ZakKemble/avr-gcc-build/releases/tag/v7.3.0-1 (build scripts), https://gcc.gnu.org |
| GNU Binutils for AVR (in the avr-gcc build) | see the build | GPL 3.0 | https://www.gnu.org/software/binutils/ |
| AVR Libc (in the avr-gcc build) | see the build | Modified BSD | https://github.com/avrdudes/avr-libc |
| AVRDUDE (Windows) | 7.3 | GPL 2.0 or later | https://github.com/avrdudes/avrdude/releases/tag/v7.3 |
| AVRDUDE (Linux, from the Ubuntu 22.04 package) | 6.3 | GPL 2.0 or later | https://launchpad.net/ubuntu/+source/avrdude |
| Libraries of AVRDUDE on Linux: libftdi1, libusb-0.1, libusb-1.0, libhidapi, libelf (elfutils), libreadline, libtinfo (ncurses), zlib, zstd | Ubuntu 22.04 | LGPL 2.1 (libftdi, libusb), BSD-style (hidapi), LGPL 3.0 / GPL 2.0 (elfutils), GPL 3.0 (readline), MIT-style (ncurses), zlib, BSD (zstd) | https://launchpad.net/ubuntu/+source/ |
| NSIS (installer stub of BlockCore-Setup.exe) | 3.x | zlib/libpng licence | https://nsis.sourceforge.io |

**Source code.** The GPL and LGPL require that the source code of these programs and libraries is available to everybody who gets the
binaries. Every BlockCore release therefore contains, next to the packages, **`BlockCore-third-party-sources.tar`**: the source code of
GCC, Binutils, AVR Libc, GMP, MPFR, MPC and isl (as used by the avr-gcc build), the build script of that build, AVRDUDE, the Ubuntu
source packages of the Linux AVRDUDE and its libraries, OpenJDK and the Java libraries (`-sources.jar`). Its `SOURCES.txt` lists where
every file comes from. The source code of BlockCore itself is the repository (and the source code archives of every release).

## Arduino core and libraries (compiled into the programs uploaded to the board)

| Component | Licence | Source |
|---|---|---|
| Arduino AVR core (`hardware/arduino/avr`, `core.a`) | LGPL 2.1 or later | https://github.com/arduino/ArduinoCore-avr |
| Wire, SPI, EEPROM, SoftwareSerial, HID | LGPL 2.1 or later | https://github.com/arduino/ArduinoCore-avr/tree/master/libraries |
| ArduinoSTL (based on uClibc++) | LGPL 2.1 | https://github.com/mike-matera/ArduinoSTL |
| Servo, Stepper, LiquidCrystal | LGPL 2.1 or later | https://github.com/arduino-libraries |
| LiquidCrystal_I2C | no licence stated by its authors (derived from LiquidCrystal, LGPL) | https://github.com/johnrickman/LiquidCrystal_I2C |
| MFRC522 | Unlicense (public domain) | https://github.com/miguelbalboa/rfid |
| IRremote | MIT | https://github.com/Arduino-IRremote/Arduino-IRremote |
| DHT sensor library, Adafruit Unified Sensor | MIT, Apache 2.0 | https://github.com/adafruit/DHT-sensor-library, https://github.com/adafruit/Adafruit_Sensor |
| Adafruit GFX, Adafruit SSD1306, Adafruit BusIO | BSD, BSD, MIT | https://github.com/adafruit |
| RobertaFunctions (NEPODefs.h), `libora.a` | Apache 2.0 (Open Roberta), contains the libraries above | https://github.com/OpenRoberta/arduino-resources |

A program you build with BlockCore is linked with the Arduino core (LGPL). This concerns you only if you distribute the compiled program (e.g.
sell boards with it): then the LGPL applies to the core part. Uploading to your own board is not distribution.

## Java libraries (`lib/`)

| Component | Version | Licence |
|---|---|---|
| Eclipse Jetty (http, io, security, server, servlet, util) | 9.4.15 | Apache 2.0 / EPL 1.0 |
| Jersey (core, server, servlet, json, guice) | 1.19.4 | CDDL 1.1 / GPL 2.0 with Classpath Exception |
| Guice, Guice Servlet | 5.1.0 | Apache 2.0 |
| HyperSQL (hsqldb), only to copy the programs of BlockCore 1.2 and older | 2.4.0 | HSQLDB licence (BSD-style) |
| jSerialComm | 2.11.0 | Apache 2.0 / LGPL 3.0 |
| Logback (classic, core) | 1.3.12 | EPL 1.0 / LGPL 2.1 |
| SLF4J API | 2.0.13 | MIT |
| Guava, failureaccess, listenablefuture | 32.0.0 | Apache 2.0 |
| Apache Commons Lang 3, Text, IO, Codec | 3.7, 1.4, 2.14.0, 1.11 | Apache 2.0 |
| JSON in Java (org.json) | 20231013 | Public domain |
| ANTLR 4 runtime | 4.7.2 | BSD 3-clause |
| JAXB API, JAXB reference implementation (jaxb-impl), StAX API, JavaBeans Activation Framework | 2.2.2, 2.2.3, 1.0-2, 1.1 | CDDL 1.1 / GPL 2.0 with CE, Apache 2.0 (StAX API) |
| Servlet API, JSR 311 API, javax.inject | 3.1.0, 1.1.1, 1 | CDDL / GPL 2.0 with CE, CDDL 1.0, Apache 2.0 |
| Jackson 1 (core, mapper, jaxrs, xc), Jettison | 1.9.2, 1.1 | Apache 2.0 |
| SnakeYAML | 2.0 | Apache 2.0 |
| JOpt Simple | 5.0.4 | MIT |
| UserAgentUtils | 1.21 | BSD 3-clause |
| OWASP Java HTML Sanitizer | 20211018.1 | Apache 2.0 |
| AOP Alliance | 1.0 | Public domain |
| JSR 305, FindBugs annotations, JCIP annotations, Checker Qual, Error Prone annotations, J2ObjC annotations | | BSD 3-clause, LGPL 2.1, CC BY 2.5, MIT, Apache 2.0, Apache 2.0 |

## Frontend (`staticResources/`)

| Component | Version | Licence |
|---|---|---|
| jQuery | 3.7.1 | MIT |
| jQuery Validation, jQuery.scrollTo, jQuery Hotkeys | 1.17.0, 2.1.2, 0.2.0 | MIT |
| Bootstrap | 5.3.2 | MIT |
| Bootstrap Table, Bootstrap Tags Input, bootstrap-wysiwyg | 1.22.1, 0.8.0, 2.0.1 | MIT |
| Ace (code editor) | 1.x | BSD 3-clause |
| EnjoyHint | | MIT |
| RequireJS | 2.1.22 | MIT / BSD |
| Font Awesome | 4.7.0 | SIL OFL 1.1 (font), MIT (CSS) |
| Typicons | 2.x | SIL OFL 1.1 |
| Roboto, Caveat (fonts) | | Apache 2.0, SIL OFL 1.1 |

## Licence texts

- Apache 2.0: https://www.apache.org/licenses/LICENSE-2.0
- GPL 2.0: https://www.gnu.org/licenses/old-licenses/gpl-2.0.html, Classpath Exception: https://openjdk.org/legal/gplv2+ce.html
- GPL 3.0: https://www.gnu.org/licenses/gpl-3.0.html, GCC Runtime Library Exception: https://www.gnu.org/licenses/gcc-exception-3.1.html
- LGPL 2.1: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html, LGPL 3.0: https://www.gnu.org/licenses/lgpl-3.0.html
- EPL 1.0: https://www.eclipse.org/legal/epl-v10.html, EDL 1.0: https://www.eclipse.org/org/documents/edl-v10.php
- CDDL 1.0/1.1: https://opensource.org/licenses/CDDL-1.0, MPL 1.1: https://www.mozilla.org/MPL/1.1/
- MIT: https://opensource.org/licenses/MIT, BSD 3-clause: https://opensource.org/licenses/BSD-3-Clause
- SIL OFL 1.1: https://openfontlicense.org, zlib: https://zlib.net/zlib_license.html, CC BY 2.5: https://creativecommons.org/licenses/by/2.5/
