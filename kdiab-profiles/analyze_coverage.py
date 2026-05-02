import xml.etree.ElementTree as ET
import sys

try:
    tree = ET.parse('backend/build/reports/kover/report.xml')
    root = tree.getroot()
except Exception as e:
    print(f"Error parsing XML: {e}")
    sys.exit(1)

total_missed = 0
total_covered = 0

class_stats = []

for package in root.findall('package'):
    for cls in package.findall('class'):
        missed = 0
        covered = 0
        line_counter = None
        for counter in cls.findall('counter'):
            if counter.get('type') == 'LINE':
                line_counter = counter
                break
        
        if line_counter is not None:
            m = int(line_counter.get('missed'))
            c = int(line_counter.get('covered'))
            missed += m
            covered += c
            total_missed += m
            total_covered += c
            
            if m > 0:
                class_stats.append({
                    'name': cls.get('name'),
                    'missed': m,
                    'covered': c,
                    'pct': (c / (c + m)) * 100 if (c + m) > 0 else 0
                })

class_stats.sort(key=lambda x: x['missed'], reverse=True)

print(f"Total Coverage: {total_covered}/{total_covered + total_missed} ({ (total_covered / (total_covered + total_missed)) * 100:.2f}%)")
print("\nTop 10 Missed Classes:")
for s in class_stats[:10]:
    print(f"{s['name']}: Missed {s['missed']} lines ({s['pct']:.1f}%)")
