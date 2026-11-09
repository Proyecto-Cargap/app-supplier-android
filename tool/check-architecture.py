#!/usr/bin/env python3
"""Comprueba fronteras estáticas y autonomía; no certifica por sí solo toda la arquitectura."""
from pathlib import Path
import re, sys
root=Path(__file__).resolve().parents[1]
violations=[]
if (root/'Core').exists():
    areas=[(root/'Core/Sources/Domain', ['SwiftUI','UIKit','Observation','Infrastructure','Application','Presentation','Security']),
           (root/'Core/Sources/Application', ['SwiftUI','UIKit','Observation','Infrastructure','Presentation','Security']),
           (root/'Core/Sources/Presentation', ['Infrastructure','Security','UIKit']),
           (root/'App/AccessScreen.swift', ['Infrastructure'])]
    for area,forbidden in areas:
        for f in ([area] if area.is_file() else area.rglob('*.swift')):
            text=f.read_text()
            for imp in re.findall(r'^import (\w+)',text,re.M):
                if imp in forbidden: violations.append(f'{f.relative_to(root)}: import {imp}')
            if area.name in ['Domain','Application','Presentation'] and re.search(r'\b(URLSession|SecItem|KeychainSessionStore|SessionDTO)\b',text):
                violations.append(f'{f.relative_to(root)}: detalle técnico fuera de infraestructura')
else:
    for module in ['domain','application','app']:
        for f in (root/module/'src/main').rglob('*.kt'):
            text=f.read_text()
            if '/composition/' in str(f): continue
            imports=re.findall(r'^import (.+)',text,re.M)
            if module in ['domain','application']:
                bad=[i for i in imports if i.startswith(('android.','androidx.','okhttp3.','dagger.')) or '.data.' in i or '.presentation.' in i]
            else:
                bad=[i for i in imports if '.data.' in i or i.startswith(('okhttp3.','android.security.'))]
            violations += [f'{f.relative_to(root)}: import {i}' for i in bad]
for f in [root/'Core/Package.swift', root/'settings.gradle.kts']:
    if f.exists() and re.search(r'\.\./|app-(customer|supplier)-(ios|android)',f.read_text()):
        violations.append(f'{f.name}: referencia a proyecto hermano')
if violations:
    print('\n'.join(violations));sys.exit(1)
print('Fronteras arquitectónicas: correctas')
