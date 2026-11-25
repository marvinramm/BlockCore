BlockCore - block programming for Arduino, offline on Linux
===========================================================

BlockCore runs completely on this computer. It needs no internet, no server and
no connector program. Root rights are not needed (except once for the serial
port permission, see below).

Boards: Arduino Uno, Arduino Nano, Arduino Mega 2560

How to use it
-------------
1. Unpack the archive anywhere, e.g. in your home folder:
       tar -xzf BlockCore-linux-x64.tar.gz
2. Start it:
       ./BlockCore/BlockCore
   To add BlockCore to the application menu:
       ./BlockCore/BlockCore --install
3. Plug in the Arduino with a USB cable.
4. In BlockCore: "robot -> connect" and choose the port (/dev/ttyACM0 or
   /dev/ttyUSB0). With a single Arduino attached it is chosen automatically.
5. Build your program and click "run" (the play button).
6. Close the BlockCore window to quit.

The window is Google Chrome, Chromium, Microsoft Edge, Brave or Vivaldi in app
mode. Without one of them BlockCore opens in your default browser.

Serial port permission (once)
-----------------------------
Uploading needs access to the serial port. On most systems your user must be
in the group "dialout" (Arch/Manjaro: "uucp"):
    sudo usermod -aG dialout $USER
Then log out and in again. BlockCore reminds you if this is missing.

Problems?
---------
- No port on Ubuntu with a CH340 Arduino clone: the package "brltty" grabs the
  device. Remove it: sudo apt remove brltty
- Upload fails: is the right port selected? Is another program (e.g. the
  Arduino IDE) using the port? For the Nano the old bootloader is tried, too.
- Programs are stored in ~/Documents/BlockCore (one .xml file per program),
  the server log in ~/.local/share/BlockCore/logs/server.log.
- Stop a BlockCore running in the background: ./BlockCore/BlockCore --stop

BlockCore is only reachable from this computer (http://127.0.0.1:1999).

BlockCore by Marvin Ramm - https://github.com/marvinramm/BlockCore
Licences: LICENSE.txt, NOTICE.txt, THIRD-PARTY-NOTICES.md
