"""예시 매물이 같이 쓰는 그림 셋을 만든다(규칙 v2 6.3).

0번 썸네일, 1번 평면도 칸, 2번 사진 칸. 사이트 화면이 그림 0·1·2번을 바로 쓴다.
글꼴은 Noto Sans CJK KR(SIL Open Font License)이다. 같은 글꼴 파일과 Pillow 판이면 같은 바이트가 나온다.
사용: python3 make_example_images.py <출력 폴더>
"""
import hashlib
import os
import sys

from PIL import Image, ImageDraw, ImageFont, __version__ as PIL_VERSION

BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
REGULAR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
KR = 1  # ttc 안에서 KR 글꼴의 차례
W, H = 1200, 900
BG = (243, 244, 246)
INK = (55, 65, 81)
ACCENT = (180, 83, 9)
LINE = (209, 213, 219)

IMAGES = [
    ("zipinfo_example_v2_0_thumb.png", "예시 매물", ACCENT, "실제 매물이 아닙니다",
     "국토부 실거래가 공개자료로 만든 예시 · 집인포"),
    ("zipinfo_example_v2_1_floorplan.png", "평면도 없음", INK, "예시 매물 · 실제 매물이 아닙니다", ""),
    ("zipinfo_example_v2_2_photo.png", "사진 없음", INK, "예시 매물 · 실제 매물이 아닙니다", ""),
]


def centered(draw, y, text, font, fill):
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    draw.text(((W - (right - left)) / 2 - left, y), text, font=font, fill=fill)


def make(path, title, title_fill, sub, foot):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.rectangle([24, 24, W - 25, H - 25], outline=LINE, width=6)
    centered(d, 250, title, ImageFont.truetype(BOLD, 170, index=KR), title_fill)
    centered(d, 520, sub, ImageFont.truetype(BOLD, 64, index=KR), INK)
    if foot:
        centered(d, 760, foot, ImageFont.truetype(REGULAR, 36, index=KR), INK)
    img.save(path, format="PNG", optimize=True)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    print(f"Pillow {PIL_VERSION}")
    for f in (BOLD, REGULAR):
        print(f"글꼴 {os.path.basename(f)} SHA-256 {hashlib.sha256(open(f, 'rb').read()).hexdigest().upper()}")
    for name, title, fill, sub, foot in IMAGES:
        p = os.path.join(out, name)
        make(p, title, fill, sub, foot)
        b = open(p, "rb").read()
        print(f"{name} {len(b)}바이트 SHA-256 {hashlib.sha256(b).hexdigest().upper()}")


if __name__ == "__main__":
    main()
