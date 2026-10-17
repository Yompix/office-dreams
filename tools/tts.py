#!/usr/bin/env python3
"""edge-tts for promo voice-overs (dev only). Same arguments as the edge-tts command:
    python3 tools/tts.py --voice ru-RU-DmitryNeural --text "..." --write-media out.mp3
edge-tts trusts only certifi's CA list; in a cloud session HTTPS goes through a proxy whose CA is in
SSL_CERT_FILE, so trust that bundle instead (TLS verification stays on) and use HTTPS_PROXY when no --proxy is given."""
import os
import ssl
import sys

import edge_tts.communicate
import edge_tts.voices
from edge_tts.util import main

ca = os.environ.get('SSL_CERT_FILE')
if ca and os.path.exists(ca):
    edge_tts.communicate._SSL_CTX = edge_tts.voices._SSL_CTX = ssl.create_default_context(cafile=ca)
px = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
if px and '--proxy' not in sys.argv:
    sys.argv[1:1] = ['--proxy', px]
sys.exit(main())
