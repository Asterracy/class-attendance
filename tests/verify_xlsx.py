import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

p = Path(__file__).resolve().parents[1] / 'build/test-export.xlsx'
ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with zipfile.ZipFile(p) as z:
    assert z.testzip() is None
    for name in z.namelist():
        ET.fromstring(z.read(name))
    root = ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
    values = {c.get('r'): ''.join(c.itertext()) for c in root.findall('.//s:c', ns)}
    assert values['B6'] == '测试甲' and values['C6'] == '已到'
    assert values['B7'] == '测试乙' and values['C7'] == '未到'
    assert values['B8'] == '=1+1 & <测试> "名"'
    assert root.find('.//s:c[@r="B8"]', ns).get('t') == 'inlineStr'
    assert root.find('.//s:c[@r="B8"]/s:f', ns) is None
    assert values['B9'] == values['B10'] == '同名'
    assert values['B11'] == '扩展字𠮷'
    assert values['B14'] == '6' and values['B15'] == values['B16'] == '3'
    assert root.find('s:autoFilter', ns).get('ref') == 'A5:C11'
    assert len(z.namelist()) == 6
print('PASS: XLSX CRC/XML, name/status rows, counts, Unicode, duplicates, formula safety, filter')
