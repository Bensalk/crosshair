#!/usr/bin/env python3
"""Write CHANGELOG.md from the in-app changelog in MainActivity.kt.

  python3 tools/changelog.py > CHANGELOG.md
"""
import os, re
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = open(os.path.join(ROOT, 'CrosshairOverlay/app/src/main/java/com/example/crosshair/MainActivity.kt'), encoding='utf-8').read()
text = re.search(r'val changelog = """(.*?)"""', src, re.S).group(1)
print('# Changelog\n')
print('Generated from the in-app changelog (`MainActivity.kt`) with `python3 tools/changelog.py > CHANGELOG.md`.')
print('Download any version from the [Releases page](https://github.com/Bensalk/crosshair/releases).\n')
for line in text.strip().splitlines():
    line = line.strip()
    if not line:
        continue
    if line.startswith('•'):
        print('- ' + line[1:].strip())
    else:
        print(f'\n## {line}\n')
