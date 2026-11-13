#!/usr/bin/env python3
"""1.8.1: прочитать QR со скриншота (как камера телефона) и сверить со ссылкой.
  pip install opencv-python-headless
  python3 tests/check_qr_shots.py <скриншот.png> <ожидаемая ссылка или её начало со *> [...пары]"""
import sys, cv2
bad = 0
for png, want in zip(sys.argv[1::2], sys.argv[2::2]):
    got = cv2.QRCodeDetector().detectAndDecode(cv2.imread(png))[0]
    ok = got.startswith(want[:-1]) if want.endswith('*') else got == want
    print('ok  ' if ok else 'FAIL', png, '→', repr(got))
    bad += not ok
sys.exit(1 if bad else 0)
