"""Keep system-primary UI/host fallback resources complete; no user data is processed."""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

res = Path(__file__).resolve().parents[1] / 'app/src/main/res'
# ISO-639-1 host labels must resolve before a later Chinese system language.
languages = 'aa ab ae af ak am an ar as av ay az ba be bg bh bi bm bn bo br bs ca ce ch co cr cs cu cv cy da de dv dz ee el en eo es et eu fa ff fi fj fo fr fy ga gd gl gn gu gv ha he hi ho hr ht hu hy hz ia id ie ig ii ik io is it iu ja jv ka kg ki kj kk kl km kn ko kr ks ku kv kw ky la lb lg li ln lo lt lu lv mg mh mi mk ml mn mr ms mt my na nb nd ne ng nl nn no nr nv ny oc oj om or os pa pi pl ps pt qu rm rn ro ru rw sa sc sd se sg si sk sl sm sn so sq sr ss st su sv sw ta te tg th ti tk tl tn to tr ts tt tw ty ug uk ur uz ve vi vo wa wo xh yi yo za zu'.split()
text_resources = [path for path in (res / 'values').glob('*.xml')
                  if any(child.tag in {'string', 'plurals', 'string-array'}
                         for child in ET.parse(path).getroot())]
for language in languages:
    target = res / ('values-' + language)
    target.mkdir(exist_ok=True)
    for path in text_resources:
        shutil.copyfile(path, target / path.name)

for qualifier in ['values-zh', 'values-zh-rTW', 'values-zh-rHK', 'values-b+zh+Hans', 'values-b+zh+Hant']:
    target = res / qualifier
    target.mkdir(exist_ok=True)
    for path in (res / 'values-zh-rCN').glob('*.xml'):
        shutil.copyfile(path, target / path.name)
print('Complete English fallback and Simplified Chinese resources generated.')
