"""Run the exact source DDL/migration/snapshot SQL on SQLite, not Android UI."""
import re,sqlite3,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'src/cn/local/attendance/AttendanceStore.java').read_text()
old_ddl=(root/'tests/legacy-schema.sql').read_text().split(';')
def statements(s): return re.findall(r'db\.execSQL\("([^"]+)"',s)
def section(a,b): return source[source.index(a):source.index(b)]
new_ddl=statements(section('@Override public void onCreate','@Override public void onConfigure'))
migration=statements(section('@Override public void onUpgrade','private List<AttendanceRules.Person> assetRoster'))
start=next(sql for sql in statements(source) if sql.startswith('INSERT INTO draft_entries'))
snapshot=next(sql for sql in statements(source) if sql.startswith('INSERT INTO record_entries'))
assert len(new_ddl)==6 and len(migration)==10
with tempfile.TemporaryDirectory() as tmp:
 path=Path(tmp)/'legacy.db'
 db=sqlite3.connect(path);db.execute('PRAGMA foreign_keys=ON')
 for sql in old_ddl:
  if sql.strip():db.execute(sql)
 db.executemany('INSERT INTO roster VALUES(?,?)',[(0,'虚构甲'),(1,'虚构乙'),(2,'虚构退出')])
 db.execute('INSERT INTO draft VALUES(1,1000)')
 db.execute('INSERT INTO draft_entries SELECT position,name,0 FROM roster')
 db.execute('UPDATE draft_entries SET arrived=1 WHERE position=0')
 db.execute('INSERT INTO records VALUES(?,?,?)',('old',500,600))
 db.execute('INSERT INTO record_entries SELECT ?,position,name,arrived FROM draft_entries',('old',))
 legacy=db.execute('SELECT record_id,position,name,arrived FROM record_entries').fetchall();db.commit()
 # SQLiteOpenHelper applies onUpgrade in a transaction. Any failed migration rolls back.
 try:
  with db:
   db.execute('BEGIN')
   for sql in migration: db.execute(sql)
   db.execute('INSERT INTO roster(position,name) VALUES(0,?)',('虚构冲突',))
 except sqlite3.IntegrityError:pass
 else:raise AssertionError('expected rollback')
 assert 'status' not in [r[1] for r in db.execute('PRAGMA table_info(record_entries)')]
 with db:
  db.execute('BEGIN')
  for sql in migration:db.execute(sql)
 assert db.execute('SELECT record_id,position,name,arrived FROM record_entries').fetchall()==legacy
 assert db.execute('SELECT status,student_id FROM record_entries ORDER BY position').fetchall()==[(1,''),(0,''),(0,'')]
 assert db.execute('SELECT activity FROM records').fetchone()==('',)
 assert db.execute('SELECT started,activity FROM draft').fetchone()==(1000,'')
 # Authoritative new roster shrinks 3 -> 2 while retaining active draft and historical people.
 with db:
  db.execute('DELETE FROM roster')
  db.executemany('INSERT INTO roster(position,name,student_id) VALUES(?,?,?)',[(0,'虚构甲','0001'),(1,'虚构乙','0002')])
 assert db.execute('SELECT count(*) FROM draft_entries').fetchone()[0]==3
 assert db.execute('SELECT count(*) FROM record_entries').fetchone()[0]==3
 # Every transition has a single scalar state; check all 9 ordered transitions and invalid states.
 for before in range(3):
  for after in range(3):
   db.execute('UPDATE draft_entries SET status=?,arrived=? WHERE position=0',(before,int(before==1)))
   db.execute('UPDATE draft_entries SET status=?,arrived=? WHERE position=0',(after,int(after==1)))
   assert db.execute('SELECT status,arrived FROM draft_entries WHERE position=0').fetchone()==(after,int(after==1))
 try:db.execute('UPDATE draft_entries SET status=3 WHERE position=0')
 except sqlite3.IntegrityError:pass
 else:raise AssertionError('invalid status accepted')
 db.commit();db.close();db=sqlite3.connect(path);db.execute('PRAGMA foreign_keys=ON')
 assert db.execute('SELECT status FROM draft_entries WHERE position=0').fetchone()==(2,)
 with db:
  db.execute('INSERT INTO records(id,started,saved,activity) VALUES(?,?,?,?)',('continued-old-draft',1000,2000,''))
  db.execute(snapshot,('continued-old-draft',));db.execute('DELETE FROM draft_entries');db.execute('DELETE FROM draft')
 # Cancellation is no write; invalid name is rejected by pure Java before the database is requested.
 before=db.total_changes
 assert db.execute('SELECT count(*) FROM draft').fetchone()[0]==0
 assert db.total_changes==before
 with db:
  db.execute('INSERT INTO draft(id,started,activity) VALUES(1,3000,?)',('虚构活动',));db.execute(start)
 assert db.execute('SELECT student_id,status FROM draft_entries ORDER BY position').fetchall()==[('0001',0),('0002',0)]
 try:
  with db:
   db.execute('INSERT INTO records(id,started,saved,activity) VALUES(?,?,?,?)',('failed',3000,4000,'虚构活动'))
   db.execute(snapshot,('failed',));db.execute(snapshot,('failed',))
 except sqlite3.IntegrityError:pass
 else:raise AssertionError('expected snapshot rollback')
 assert db.execute('SELECT count(*) FROM records WHERE id="failed"').fetchone()[0]==0
 assert db.execute('SELECT count(*) FROM draft_entries').fetchone()[0]==2
 with db:
  db.execute('UPDATE draft_entries SET status=1,arrived=1 WHERE position=0')
  for key in ['first','second']:
   db.execute('INSERT INTO records(id,started,saved,activity) VALUES(?,?,?,?)',(key,3000,4000,'虚构活动'));db.execute(snapshot,(key,))
  db.execute('DELETE FROM draft_entries');db.execute('DELETE FROM draft')
 assert db.execute('SELECT activity FROM records WHERE id="first"').fetchone()==('虚构活动',)
 assert db.execute('SELECT student_id,status FROM record_entries WHERE record_id="first" ORDER BY position').fetchall()==[('0001',1),('0002',0)]
 assert db.execute('SELECT record_id,position,name,arrived FROM record_entries WHERE record_id="old"').fetchall()==legacy
 with db:db.execute('DELETE FROM records WHERE id="first"')
 assert db.execute('SELECT count(*) FROM record_entries WHERE record_id="first"').fetchone()[0]==0
 assert db.execute('SELECT count(*) FROM record_entries WHERE record_id="second"').fetchone()[0]==2
 db.close()
 # Verify direct creation agrees with migrated schema and supports 3 states.
 db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
 for sql in new_ddl:db.execute(sql)
 db.execute('INSERT INTO roster(position,name,student_id) VALUES(0,?,?)',('虚构甲','0001'))
 db.execute('INSERT INTO draft(id,started,activity) VALUES(1,1000,?)',('虚构活动',));db.execute(start)
 db.execute('UPDATE draft_entries SET status=2,arrived=0 WHERE position=0')
 assert db.execute('SELECT status FROM draft_entries').fetchone()==(2,)
ui=(root/'src/cn/local/attendance/MainActivity.java').read_text()
assert '.setNegativeButton("取消",null).setPositiveButton("开始",null)' in ui
assert ui.index('AttendanceRules.activityName(input.getText().toString())')<ui.index('store.startDraft(activity)')
assert 'RadioGroup options=' in ui and 'options.setOnCheckedChangeListener' in ui
print('PASS: exact SQLite v1->v2 migration/rollback, old snapshots/draft, shrinking roster, all 9 status transitions, reopen, new snapshots, cascading delete; dialog wiring inspected (not UI execution)')
