import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
base=Path(__file__).resolve().parents[1]/'build'
def inspect(path):
 with zipfile.ZipFile(path) as z:
  assert z.testzip() is None
  for name in z.namelist(): ET.fromstring(z.read(name))
  root=ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
  cells={c.get('r'):c for c in root.findall('.//s:c',ns)}
  values={ref:''.join(c.itertext()) for ref,c in cells.items()}
  assert not root.findall('.//s:f',ns)
  assert len(z.namelist())==6
  return root,cells,values
root,cells,v=inspect(base/'test-export.xlsx')
assert v['B2']=='虚构活动 & <例>' and v['B7']==v['B2']
assert v['C7']=='0001234567890123456789' and v['D7']=='虚构甲' and v['E7']=='已到'
assert v['E8']=='未到' and v['E9']=='请假'
assert v['C9']=='=1+1' and v['D9']=='=1+1 & <虚构> "名"'
assert v['D10']==v['D11']=='虚构同名' and v['D12']=='虚构𠮷'
for n in range(7,13):assert cells['C'+str(n)].get('t')=='inlineStr'
assert v['B15']=='6' and v['B16']==v['B17']==v['B18']=='2'
assert root.find('s:autoFilter',ns).get('ref')=='A6:E12'
assert root.find('.//s:pane',ns).get('topLeftCell')=='A7'
root,cells,v=inspect(base/'test-legacy-export.xlsx')
assert v['B2']=='未命名活动（旧记录）' and v['B7']==v['B2']
assert v['C7']==v['C8']=='' and v['E7']=='已到' and v['E8']=='未到'
print('PASS: XLSX CRC/XML, activity, ID as exact text, three states, counts, Unicode, duplicate names, formula safety, legacy blank IDs')
