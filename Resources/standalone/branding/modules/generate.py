#!/usr/bin/env python3
"""
Flat illustrations of the modules for the configuration blocks of BlockCore (one SVG per block type, viewBox 120 x 64).
Run: python3 generate.py <output dir>   (build-windows.sh copies the generated files to staticResources/blockcore/modules)
"""
import math
import os
import sys

W, H = 120, 64
BLUE = '#1f5fa8'
BLUE_EDGE = '#174a85'
GOLD = '#d8b25a'
SILK = '#e8f0ff'


def svg(body, title):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">'
            f'<title>{title}</title>{body}</svg>\n')


def pcb(x, y, w, h, color=BLUE, edge=BLUE_EDGE, holes=True, r=3):
    out = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{color}" stroke="{edge}" stroke-width="1"/>'
    if holes:
        for cx, cy in ((x + 4, y + 4), (x + w - 4, y + 4), (x + 4, y + h - 4), (x + w - 4, y + h - 4)):
            out += f'<circle cx="{cx}" cy="{cy}" r="1.8" fill="#0d2d52" stroke="{GOLD}" stroke-width="0.6"/>'
    return out


def pins(x, y, n, step=4.2, vertical=False, length=5):
    """a pin header: black base with gold pins pointing down (or left)"""
    out = ''
    if vertical:
        out += f'<rect x="{x}" y="{y}" width="3" height="{n * step}" fill="#1b1b1b" rx="0.6"/>'
        for i in range(n):
            out += f'<rect x="{x - length}" y="{y + i * step + step / 2 - 0.6}" width="{length}" height="1.2" fill="{GOLD}"/>'
    else:
        out += f'<rect x="{x}" y="{y}" width="{n * step}" height="3" fill="#1b1b1b" rx="0.6"/>'
        for i in range(n):
            out += f'<rect x="{x + i * step + step / 2 - 0.6}" y="{y + 3}" width="1.2" height="{length}" fill="{GOLD}"/>'
    return out


def chip(x, y, w, h, label='', pins_per_side=0, color='#202326'):
    out = ''
    if pins_per_side:
        step = w / (pins_per_side + 1)
        for i in range(1, pins_per_side + 1):
            out += f'<rect x="{x + i * step - 0.6}" y="{y - 2}" width="1.2" height="{h + 4}" fill="#b9bec4"/>'
    out += f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="1" fill="{color}"/>'
    if label:
        out += text(x + w / 2, y + h / 2 + 1.6, label, 4.2, '#c9ced6')
    return out


def text(x, y, value, size=4.5, color=SILK, weight='600', anchor='middle'):
    return (f'<text x="{x}" y="{y}" font-family="Arial, sans-serif" font-size="{size}" font-weight="{weight}" fill="{color}" '
            f'text-anchor="{anchor}">{value}</text>')


def wire(points, color, width=2.2):
    d = 'M' + ' L'.join(f'{px},{py}' for px, py in points)
    return f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>'


def can(cx, cy, r):
    """a silver ultrasonic transducer seen from the front"""
    out = f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#cfd5dc" stroke="#8e969f" stroke-width="1"/>'
    out += f'<circle cx="{cx}" cy="{cy}" r="{r * 0.78}" fill="#3a4048"/>'
    for i in range(-3, 4):
        for j in range(-3, 4):
            if i * i + j * j <= 9:
                out += f'<circle cx="{cx + i * r * 0.2}" cy="{cy + j * r * 0.2}" r="{r * 0.055}" fill="#6b737c"/>'
    return out


def led(cx, cy, color, glow=None, legs=2):
    out = ''
    for i in range(legs):
        lx = cx - 3 + i * (6 / max(1, legs - 1)) if legs > 1 else cx
        out += f'<rect x="{lx - 0.6}" y="{cy + 7}" width="1.2" height="{20 if i == 0 else 16}" fill="#b9bec4"/>'
    out += f'<rect x="{cx - 8}" y="{cy + 4}" width="16" height="3.5" rx="1" fill="{color}" opacity="0.9"/>'
    out += f'<path d="M{cx - 7},{cy + 5} V{cy - 4} a7,7 0 0 1 14,0 V{cy + 5} Z" fill="{color}" opacity="0.88"/>'
    out += f'<path d="M{cx - 4},{cy - 3} a4,4 0 0 1 4,-4" fill="none" stroke="#fff" stroke-width="1.4" opacity="0.7"/>'
    if glow:
        out = f'<circle cx="{cx}" cy="{cy - 2}" r="13" fill="{glow}" opacity="0.18"/>' + out
    return out


def lcd(display, text_color, rows, board='#1f6b3a', backpack=False):
    out = pcb(6, 8, 108, 48, board, '#164f2b')
    out += pins(26, 8.5, 16, 3.6) if not backpack else ''
    out += f'<rect x="12" y="17" width="96" height="32" rx="2" fill="#1b1b1b"/>'
    out += f'<rect x="15" y="20" width="90" height="26" rx="1" fill="{display}"/>'
    for r, line in enumerate(rows):
        out += text(19, 30 + r * 11, line, 8.5, text_color, '700', 'start').replace('Arial, sans-serif', 'Courier New, monospace')
    if backpack:
        out += f'<rect x="96" y="9" width="16" height="7" rx="1" fill="#1b1b1b"/>' + text(104, 14.3, 'I²C', 4.2, '#ddd')
    return out


def module_board(label, w=70, h=30, color=BLUE):
    x = (W - w) / 2
    return pcb(x, 8, w, h, color) + text(W / 2, 8 + h - 3, label, 4.6)


# ---------------------------------------------------------------- the modules

def ultrasonic():
    out = pcb(4, 10, 112, 44)
    out += can(30, 31, 15) + can(90, 31, 15)
    out += f'<rect x="54" y="14" width="12" height="6" rx="1" fill="#c8ccd2"/>'
    out += text(60, 50, 'HC-SR04', 5)
    out += pins(51.6, 54, 4)
    return svg(out, 'ultrasonic sensor HC-SR04')


def tcs34725():
    out = pcb(30, 6, 60, 46)
    out += f'<rect x="51" y="15" width="18" height="18" rx="2" fill="#f4f1e6" stroke="#c9c2a8"/>'
    out += f'<circle cx="60" cy="24" r="5" fill="#fff8c7" stroke="#e5d98a"/>'
    out += chip(42, 36, 8, 6) + text(60, 47, 'TCS34725', 4.6)
    out += pins(45.3, 52, 7)
    return svg(out, 'colour sensor TCS34725')


def mpu6050():
    out = pcb(28, 8, 64, 42)
    out += chip(50, 18, 20, 16, 'MPU', 0, '#18191c')
    out += f'<path d="M36,44 h10 M36,44 v-10" stroke="{SILK}" stroke-width="1" fill="none"/>' + text(48.5, 45.5, 'X', 4.4) + text(36, 31.5, 'Y', 4.4)
    out += text(78, 45, 'GY-521', 4.4)
    out += pins(43.2, 50, 8, 4.2)
    return svg(out, 'gyro sensor MPU6050')


def analogjoystick():
    out = pcb(30, 4, 60, 56, '#1d1f22', '#111', True)
    out += f'<circle cx="60" cy="31" r="20" fill="#3b3f45" stroke="#55595f"/>'
    out += f'<circle cx="60" cy="31" r="13" fill="#25282c"/>'
    out += f'<circle cx="60" cy="31" r="10" fill="#33373c" stroke="#1a1c1f" stroke-width="2"/>'
    for a in range(0, 360, 30):
        out += f'<circle cx="{60 + 7 * math.cos(math.radians(a))}" cy="{31 + 7 * math.sin(math.radians(a))}" r="0.9" fill="#4c5157"/>'
    out += pins(30, 13, 5, 4.2, True)
    return svg(out, 'joystick KY-023')


def ds18b20():
    out = wire([(46, 32), (70, 32), (84, 30), (96, 28)], '#1b1b1b', 5)
    out += wire([(96, 26), (112, 18)], '#d23b2f') + wire([(96, 28), (112, 28)], '#e6c32f') + wire([(96, 30), (112, 38)], '#2a2a2a')
    out += f'<rect x="8" y="24" width="40" height="16" rx="7" fill="#c9cfd6" stroke="#8e969f"/>'
    out += f'<rect x="10" y="26" width="36" height="4" rx="2" fill="#eef1f4" opacity="0.8"/>'
    out += text(28, 52, 'DS18B20', 5.2, '#7d8792')
    return svg(out, 'temperature probe DS18B20')


def led_module():
    return svg(led(60, 22, '#e0312b', '#ff5a4f'), 'LED')


def rgbled():
    out = led(60, 22, '#eef0f3', None, 4)
    out += f'<circle cx="56" cy="16" r="3" fill="#e0312b" opacity="0.7"/><circle cx="62" cy="14" r="3" fill="#2fa84f" opacity="0.7"/><circle cx="60" cy="19" r="3" fill="#2f6fe0" opacity="0.7"/>'
    return svg(out, 'RGB LED')


def buzzer():
    out = pcb(34, 8, 52, 46)
    out += f'<circle cx="60" cy="29" r="17" fill="#1d1f22" stroke="#000"/>' + f'<circle cx="60" cy="29" r="3" fill="#000"/>'
    out += text(70, 20, '+', 7, '#bbb')
    out += pins(53.7, 54, 3)
    return svg(out, 'buzzer')


def relay():
    out = pcb(8, 6, 104, 50)
    out += f'<rect x="16" y="12" width="46" height="34" rx="2" fill="#2a6fd0" stroke="#1d4f99"/>' + text(39, 32, 'SRD-05VDC', 4.6, '#e9f0ff')
    for i in range(3):
        out += f'<rect x="{78 + i * 10}" y="16" width="9" height="16" rx="1" fill="#2c8a4a"/><circle cx="{82.5 + i * 10}" cy="22" r="2.6" fill="#c4c8cc"/>'
    out += f'<circle cx="80" cy="44" r="2.5" fill="#ff4b3f"/>' + pins(14, 56, 3)
    return svg(out, 'relay')


def servo():
    out = f'<rect x="24" y="18" width="62" height="32" rx="3" fill="#2f6fd0" stroke="#1d4f99"/>'
    out += f'<rect x="16" y="28" width="78" height="5" rx="1.5" fill="#2a64bd"/>'
    out += f'<circle cx="40" cy="18" r="9" fill="#2f6fd0"/>' + f'<circle cx="40" cy="14" r="3.4" fill="#eee"/>'
    out += f'<path d="M24,12 L56,12 a3,3 0 0 1 0,6 L24,18 a3,3 0 0 1 0,-6 Z" fill="#f4f4f4" stroke="#ccc"/>'
    out += text(62, 42, 'SG90', 6, '#e9f0ff')
    out += wire([(86, 40), (100, 40), (112, 52)], '#8a5a2b', 1.8) + wire([(86, 42), (100, 42), (112, 54)], '#d23b2f', 1.8) + wire([(86, 44), (100, 44), (112, 56)], '#f39a1e', 1.8)
    return svg(out, 'servo SG90')


def stepmotor():
    out = f'<circle cx="50" cy="32" r="24" fill="#c9cfd6" stroke="#8e969f"/>' + f'<circle cx="50" cy="32" r="17" fill="#b5bcc4"/>'
    out += f'<rect x="44" y="4" width="12" height="10" rx="2" fill="#2f6fd0"/>' + f'<rect x="48" y="0" width="4" height="7" fill="#e8c06a"/>'
    out += f'<circle cx="24" cy="32" r="3" fill="#9aa1a9"/><circle cx="76" cy="32" r="3" fill="#9aa1a9"/>'
    out += text(50, 36, '28BYJ-48', 5.4, '#4b525a')
    for i, c in enumerate(['#2f6fd0', '#e64ab0', '#f2c84b', '#f39a1e', '#d23b2f']):
        out += wire([(72, 44 + i * 2), (100, 46 + i * 2), (114, 46 + i * 2)], c, 1.6)
    return svg(out, 'stepper motor 28BYJ-48')


def lcd_parallel():
    return svg(lcd('#a7c957', '#24331a', ['Hallo', 'BlockCore']), 'LCD 1602')


def lcd_i2c():
    return svg(lcd('#2f6fe0', '#e9f3ff', ['Hallo', 'BlockCore'], BLUE, True), 'LCD 1602 I2C')


def oled():
    out = pcb(26, 6, 68, 52, '#1d1f22', '#111')
    out += pins(51.6, 6.5, 4)
    out += f'<rect x="32" y="17" width="56" height="34" rx="1.5" fill="#05070a"/>'
    out += f'<rect x="34" y="19" width="52" height="8" fill="#f5d33b" opacity="0.9"/>' + text(60, 25.5, 'SSD1306', 5.4, '#05070a', '700')
    out += text(36, 36, 'Temp 21.5', 5.5, '#61d6ff', '600', 'start') + text(36, 46, 'Hallo', 5.5, '#61d6ff', '600', 'start')
    return svg(out, 'OLED display SSD1306')


def l293d():
    out = pcb(4, 6, 112, 52, '#1f5fa8')
    out += chip(30, 14, 22, 10, 'L293D', 8) + chip(30, 36, 22, 10, 'L293D', 8) + chip(62, 26, 16, 8, '595', 4)
    for y in (12, 40):
        for i in range(2):
            out += f'<rect x="8" y="{y + i * 8}" width="12" height="7" rx="1" fill="#2c8a4a"/><circle cx="14" cy="{y + 3.5 + i * 8}" r="2.2" fill="#c4c8cc"/>'
            out += f'<rect x="100" y="{y + i * 8}" width="12" height="7" rx="1" fill="#2c8a4a"/><circle cx="106" cy="{y + 3.5 + i * 8}" r="2.2" fill="#c4c8cc"/>'
    out += text(80, 52, 'M1 M2 M3 M4', 4.4)
    return svg(out, 'L293D motor shield')


def key():
    out = pcb(36, 10, 48, 44)
    out += f'<rect x="46" y="18" width="28" height="28" rx="2" fill="#1d1f22"/>' + f'<circle cx="60" cy="32" r="9" fill="#3b3f45" stroke="#55595f"/>'
    out += pins(53.7, 54, 3)
    return svg(out, 'button')


def light():
    out = pcb(30, 8, 60, 46)
    out += f'<circle cx="60" cy="26" r="11" fill="#d8743a" stroke="#a8552a"/>'
    out += f'<path d="M53,26 q2,-6 4,0 t4,0 t4,0" fill="none" stroke="#3b2414" stroke-width="1.4"/>'
    out += f'<path d="M53,30 q2,-6 4,0 t4,0 t4,0" fill="none" stroke="#3b2414" stroke-width="1.4" transform="translate(0,-8)"/>'
    out += text(60, 48, 'LDR', 4.8) + pins(53.7, 54, 3)
    return svg(out, 'light sensor')


def potentiometer():
    out = pcb(34, 16, 52, 38)
    out += f'<rect x="44" y="24" width="32" height="24" rx="2" fill="#2f6fd0"/>'
    out += f'<circle cx="60" cy="18" r="11" fill="#1d1f22"/><rect x="58.8" y="8" width="2.4" height="8" fill="#9aa1a9"/>'
    out += pins(53.7, 54, 3)
    return svg(out, 'potentiometer')


def temperature():
    out = f'<path d="M48,30 V16 a12,12 0 0 1 24,0 V30 Z" fill="#1d1f22"/>' + text(60, 24, 'TMP36', 4.4, '#c9ced6')
    for i in range(3):
        out += f'<rect x="{51.5 + i * 7.5}" y="30" width="1.6" height="26" fill="#b9bec4"/>'
    return svg(out, 'temperature sensor TMP36')


def humidity():
    out = pcb(38, 40, 44, 16) + f'<rect x="42" y="4" width="36" height="38" rx="2" fill="#4a8fe0" stroke="#2f6fd0"/>'
    for i in range(5):
        for j in range(4):
            out += f'<rect x="{46 + j * 7.5}" y="{9 + i * 6}" width="4" height="3" rx="0.8" fill="#24508c"/>'
    out += pins(53.7, 56, 3)
    return svg(out, 'humidity sensor DHT11')


def infrared():
    out = pcb(36, 14, 48, 40)
    out += f'<rect x="50" y="20" width="20" height="18" rx="2" fill="#1d1f22"/>' + f'<path d="M54,22 a6,6 0 0 1 12,0 v6 h-12 Z" fill="#2c2f33" stroke="#000"/>'
    out += text(60, 48, 'IR', 5) + pins(53.7, 54, 3)
    return svg(out, 'infrared receiver')


def motion():
    out = pcb(20, 6, 80, 52, '#2c8a4a', '#1f6b35')
    out += f'<circle cx="60" cy="32" r="22" fill="#f4f6f8" stroke="#d5d9de"/>'
    for r in (16, 10, 4):
        out += f'<circle cx="60" cy="32" r="{r}" fill="none" stroke="#dde1e6" stroke-width="1.2"/>'
    for a in range(0, 180, 30):
        dx, dy = 22 * math.cos(math.radians(a)), 22 * math.sin(math.radians(a))
        out += f'<line x1="{60 - dx}" y1="{32 - dy}" x2="{60 + dx}" y2="{32 + dy}" stroke="#e3e6ea" stroke-width="0.9"/>'
    return svg(out, 'motion sensor HC-SR501')


def rfid():
    out = pcb(14, 4, 92, 56)
    for i in range(4):
        out += f'<rect x="{24 + i * 3}" y="{10 + i * 3}" width="{56 - i * 6}" height="{42 - i * 6}" rx="4" fill="none" stroke="{GOLD}" stroke-width="1"/>'
    out += chip(86, 26, 12, 12, '', 0) + text(52, 33, 'RC522', 5.6)
    out += pins(18, 56.5, 8, 4.2, False, 3)
    return svg(out, 'RFID reader RC522')


def moisture():
    out = f'<path d="M40,6 h40 v14 h-6 v38 l-4,4 l-4,-4 v-38 h-12 v38 l-4,4 l-4,-4 v-38 h-6 Z" fill="{BLUE}" stroke="{BLUE_EDGE}"/>'
    out += f'<rect x="50" y="26" width="3" height="30" fill="{GOLD}"/><rect x="67" y="26" width="3" height="30" fill="{GOLD}"/>'
    out += pins(53.7, 3, 3, 4.2, False, 0)
    return svg(out, 'soil moisture sensor')


def pulse():
    out = f'<circle cx="60" cy="32" r="24" fill="#2c8a4a" stroke="#1f6b35"/>'
    out += f'<path d="M60,44 L47,31 a7,7 0 0 1 13,-8 a7,7 0 0 1 13,8 Z" fill="#f4f6f8"/>'
    out += wire([(46, 31), (52, 31), (55, 25), (58, 37), (61, 29), (64, 31), (74, 31)], '#d23b2f', 1.4)
    return svg(out, 'pulse sensor')


def drop():
    out = pcb(18, 8, 84, 48, '#1d1f22', '#000', False)
    for i in range(6):
        y = 13 + i * 7
        out += f'<path d="M24,{y} h68" stroke="#c9a24b" stroke-width="2"/>'
    out += f'<path d="M32,24 q4,-7 8,0 a4,4 0 0 1 -8,0" fill="#5bb4ff" opacity="0.85"/>' + f'<path d="M70,36 q4,-7 8,0 a4,4 0 0 1 -8,0" fill="#5bb4ff" opacity="0.85"/>'
    return svg(out, 'rain / drop sensor')


def pin_icon(label, signal):
    out = f'<rect x="20" y="16" width="34" height="32" rx="4" fill="#1d1f22"/>' + f'<rect x="31" y="27" width="12" height="10" rx="1" fill="{GOLD}"/>'
    out += text(37, 58, label, 7, '#7d8792', '700')
    if signal == 'digital':
        out += wire([(62, 40), (70, 40), (70, 24), (82, 24), (82, 40), (94, 40), (94, 24), (104, 24)], '#2f8fbf', 2.2)
    else:
        pts = [(62 + i, 32 - 9 * math.sin(i / 42 * 4 * math.pi)) for i in range(0, 43, 2)]
        out += wire(pts, '#f29400', 2.2)
    return svg(out, label)


MODULES = {
    'robConf_ultrasonic': ultrasonic, 'robConf_tcs34725': tcs34725, 'robConf_mpu6050': mpu6050, 'robConf_analogjoystick': analogjoystick,
    'robConf_ds18b20': ds18b20, 'robConf_led': led_module, 'robConf_rgbled': rgbled, 'robConf_buzzer': buzzer, 'robConf_relay': relay,
    'robConf_servo': servo, 'robConf_stepmotor': stepmotor, 'robConf_lcd': lcd_parallel, 'robConf_lcdi2c': lcd_i2c,
    'robConf_oledssd1306i2c': oled, 'robConf_l293dmotor': l293d, 'robConf_key': key, 'robConf_light': light,
    'robConf_potentiometer': potentiometer, 'robConf_temperature': temperature, 'robConf_humidity': humidity,
    'robConf_infrared': infrared, 'robConf_motion': motion, 'robConf_rfid': rfid, 'robConf_moisture': moisture, 'robConf_pulse': pulse,
    'robConf_drop': drop,
    'robConf_digitalout': lambda: pin_icon('digital out', 'digital'), 'robConf_digitalin': lambda: pin_icon('digital in', 'digital'),
    'robConf_analogout': lambda: pin_icon('analog out', 'analog'), 'robConf_analogin': lambda: pin_icon('analog in', 'analog'),
}

if __name__ == '__main__':
    out_dir = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(out_dir, exist_ok=True)
    for block_type, draw in MODULES.items():
        with open(os.path.join(out_dir, block_type.replace('robConf_', '') + '.svg'), 'w', encoding='utf-8') as f:
            f.write(draw())
    print(len(MODULES), 'modules')
