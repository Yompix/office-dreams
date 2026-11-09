import sys, glob, zxingcpp
from PIL import Image
bad = 0; n = 0
for png in sorted(glob.glob(sys.argv[1] + "/qr_*.png")):
    want = open(png[:-4] + ".txt", encoding="utf-8").read()
    res = zxingcpp.read_barcodes(Image.open(png).convert("L"))
    got = res[0].text if res else None
    n += 1
    if got != want:
        bad += 1; print("FAIL", png, repr(got)[:80])
print(f"QR decoded {n-bad}/{n}")
sys.exit(1 if bad else 0)
