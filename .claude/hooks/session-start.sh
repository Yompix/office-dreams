#!/bin/bash
# Cloud sessions only: install what the image lacks. Node 22, ffmpeg, Python with PIL/numpy and Playwright + Chromium are already there.
set -euo pipefail
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then exit 0; fi
# edge-tts: voice-overs for the promo videos (run it as python3 tools/tts.py, which trusts the session's proxy CA)
if ! python3 -c "import edge_tts" 2>/dev/null; then
  pip install -q --disable-pip-version-check --root-user-action=ignore edge-tts || echo "session-start: edge-tts install failed (promo voice-overs only)" >&2
fi
