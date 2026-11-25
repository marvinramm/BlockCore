BlockCore - block programming for Arduino, offline on Windows
=============================================================

BlockCore runs completely on this computer. It needs no internet, no server and
no connector program.

Boards: Arduino Uno, Arduino Nano, Arduino Mega 2560
(with the TCS34725 colour sensor and the L293D motor shield).

How to use it
-------------
1. Start "BlockCore" (desktop or start menu).
   After a short start window the BlockCore window opens.
2. Plug in the Arduino with a USB cable.
3. In BlockCore: "robot -> connect" and choose the COM port.
   With a single Arduino attached it is chosen automatically.
4. Build your program and click "run" (the play button).
   The program is compiled and uploaded to the Arduino.
5. Text the Arduino sends ("show on Serial Monitor") is shown under
   "robot -> Serial Monitor". During an upload the monitor pauses and
   connects again afterwards.
6. Close the BlockCore window to quit.

Choose the board (Uno, Nano, Mega) under "robot -> Board" and the language
(English / German) under "languages". Programs are saved on this PC.

Problems?
---------
- No COM port: many Arduino clones need the "CH340" USB driver.
- Upload fails: is the right COM port selected? Is another program (e.g. the
  Arduino IDE) using the port? For the Nano the old bootloader is tried, too.
- Colour sensor always says "black": it gets too little light. The LED on the
  sensor board must be on, hold the sensor about 1 cm above the surface and
  shade it from room light. Lower "black below %" in the robot configuration.
- Programs are stored in %LOCALAPPDATA%\BlockCore,
  the server log in %LOCALAPPDATA%\BlockCore\logs\server.log.

BlockCore is only reachable from this computer (http://127.0.0.1:1999).

BlockCore by Marvin Ramm - https://github.com/marvinramm/BlockCore
Based on the Open Roberta Lab of Fraunhofer IAIS (Apache License 2.0).
"Open Roberta" is a trademark of the Fraunhofer Society; BlockCore is not an
official product of Fraunhofer IAIS.
