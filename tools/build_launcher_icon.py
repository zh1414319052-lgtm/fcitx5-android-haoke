"""Package the original transparent artwork into Android launcher resources.

The artwork is image_gen output, not an upstream/Tencent/Tux logo. This script
only performs deterministic density export, compositing and Android mask export.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "design/icon/penguin-keyboard-foreground-v1.png"
RES = ROOT / "app/src/main/res"
BACKGROUND = "#E7EFFF"
ART = Image.open(SOURCE).convert("RGBA")
assert ART.getchannel("A").getextrema() == (0, 255)

for density, pixels in {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}.items():
    folder = RES / f"mipmap-{density}"
    folder.mkdir(exist_ok=True)
    # Legacy icons do not have the adaptive icon's outer overscan area.
    canvas = Image.new("RGBA", (pixels,pixels), BACKGROUND)
    mark = ART.resize((round(pixels*1.22),round(pixels*1.22)),Image.Resampling.LANCZOS)
    canvas.alpha_composite(mark,((pixels-mark.width)//2,(pixels-mark.height)//2))
    for rounded in [False, True]:
        output = canvas.copy()
        if rounded:
            mask = Image.new("L",output.size)
            ImageDraw.Draw(mask).ellipse((0,0,pixels-1,pixels-1),fill=255)
            output.putalpha(mask)
        name = "ic_launcher_round" if rounded else "ic_launcher"
        output.save(folder/f"{name}.png",optimize=True)

raw = RES / "drawable-nodpi"
raw.mkdir(exist_ok=True)
ART.resize((864,864),Image.Resampling.LANCZOS).save(raw/"haoke_launcher_mark.png",optimize=True)
(RES/"drawable/ic_launcher_foreground.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/haoke_launcher_mark"
    android:gravity="fill" android:filter="true" />
''',encoding="utf-8")
(RES/"drawable/ic_launcher_background.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#E7EFFF" />
</shape>
''',encoding="utf-8")
(RES/"drawable/ic_launcher_foreground_monochrome.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<!-- A native themed-icon interpretation of the selected penguin/key silhouette. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#000000" android:fillType="evenOdd"
        android:pathData="M54,26 C42,26 32,36 31,49 C29,53 26,58 26,62 C26,65 30,65 32,63 L32,70 Q32,79 43,79 L65,79 Q76,79 76,70 L76,63 Q82,66 82,62 C82,58 79,53 77,49 C76,36 66,26 54,26 Z M54,42 C47,32 36,37 36,49 Q36,56 41,58 C37,63 35,68 36,71 Q37,75 44,75 L64,75 Q71,75 72,71 C73,68 71,63 67,58 Q72,56 72,49 C72,37 61,32 54,42 Z M46,44 A2.5,3.5 0,1 0,46,51 A2.5,3.5 0,1 0,46,44 Z M62,44 A2.5,3.5 0,1 0,62,51 A2.5,3.5 0,1 0,62,44 Z M50,52 Q54,49 58,52 Q59,54 54,57 Q49,54 50,52 Z M40,64 Q40,62 42,62 L47,62 Q49,62 49,64 L49,69 Q49,71 47,71 L42,71 Q40,71 40,69 Z M51,64 Q51,62 53,62 L57,62 Q59,62 59,64 L59,69 Q59,71 57,71 L53,71 Q51,71 51,69 Z M61,64 Q61,62 63,62 L67,62 Q69,62 69,64 L69,69 Q69,71 67,71 L63,71 Q61,71 61,69 Z" />
</vector>
''',encoding="utf-8")

preview = Image.new("RGB",(768,256),"#F5F7FC")
for index,kind in enumerate(["square","round","squircle"]):
    tile = Image.new("RGBA",(216,216),BACKGROUND)
    tile.alpha_composite(ART.resize((216,216),Image.Resampling.LANCZOS))
    mask = Image.new("L",tile.size)
    draw = ImageDraw.Draw(mask)
    if kind=="round":draw.ellipse((0,0,215,215),fill=255)
    else:draw.rounded_rectangle((0,0,215,215),radius=42 if kind=="squircle" else 8,fill=255)
    preview.paste(tile,(index*256+20,20),mask)
preview.save(ROOT/"design/icon/launcher-mask-preview.png")
(ROOT/"design/icon/README.md").write_text('''# 新软件图标

采用一个原创企鹅与三枚按键结合的图形，无文字。蓝白主体配橙色鸟喙和浅蓝背景，适合小尺寸识别。

原件：penguin-keyboard-foreground-v1.png，内置 image_gen 生成的透明 PNG；没有伪造 SVG 原件。tools/build_launcher_icon.py 负责密度导出、背景与 Android 遮罩包装，原件保留不动。主题图标使用与原件轮廓对应的原生矢量解释。普通与圆形旧版图标为五种密度，自适应与单色图标保留 Android 系统遮罩。

生成规格：original friendly geometric penguin, navy body, white face/chest, tiny orange beak and three blue keyboard keys, no text/scarf/accessories, transparent safe margins, recognizable at 48 px. 使用内置生图工具；未使用第三方图标或字体。
''',encoding="utf-8")
print(json.dumps({"source":str(SOURCE),"legacy_densities":5,"adaptive":True,"monochrome":True}))
