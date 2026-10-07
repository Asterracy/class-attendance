"""SQL-level persistence checks, not Android device/UI integration tests."""
import re
import sqlite3
import tempfile
from pathlib import Path

source = (Path(__file__).resolve().parents[1] / 'src/cn/local/attendance/AttendanceStore.java').read_text()
ddl = re.findall(r'db\.execSQL\("(CREATE TABLE [^"]+)"\)', source)
assert len(ddl) == 5
with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / 'test.db'
    db = sqlite3.connect(path)
    db.execute('PRAGMA foreign_keys=ON')
    for sql in ddl:
        db.execute(sql)
    db.executemany('INSERT INTO roster VALUES(?,?)', [(0, '测试甲'), (1, '测试乙')])
    db.execute('INSERT INTO draft VALUES(1,1000)')
    db.execute('INSERT INTO draft_entries SELECT position,name,0 FROM roster')
    db.execute('UPDATE draft_entries SET arrived=1 WHERE position=0')
    db.commit()
    db.close()
    db = sqlite3.connect(path)
    db.execute('PRAGMA foreign_keys=ON')
    assert db.execute('SELECT arrived FROM draft_entries ORDER BY position').fetchall() == [(1,), (0,)]
    try:
        with db:
            db.execute('INSERT INTO records VALUES(?,?,?)', ('first', 1000, 2000))
            db.execute('INSERT INTO record_entries SELECT ?,position,name,arrived FROM draft_entries', ('first',))
            db.execute('INSERT INTO record_entries VALUES(?,?,?,?)', ('first', 0, '故意冲突', 0))
    except sqlite3.DatabaseError:
        pass
    else:
        raise AssertionError('Expected transaction failure')
    assert db.execute('SELECT count(*) FROM records').fetchone()[0] == 0
    assert db.execute('SELECT count(*) FROM draft_entries').fetchone()[0] == 2
    with db:
        for key in ['first', 'second']:
            db.execute('INSERT INTO records VALUES(?,?,?)', (key, 1000, 2000))
            db.execute('INSERT INTO record_entries SELECT ?,position,name,arrived FROM draft_entries', (key,))
        db.execute('DELETE FROM draft_entries')
        db.execute('DELETE FROM draft')
        db.execute('DELETE FROM roster')
        db.execute('INSERT INTO roster VALUES(0,?)', ('新名单',))
    assert db.execute('SELECT name,arrived FROM record_entries WHERE record_id=? ORDER BY position', ('first',)).fetchall() == [('测试甲', 1), ('测试乙', 0)]
    assert db.execute('SELECT count(*) FROM records').fetchone()[0] == 2
    with db:
        db.execute('DELETE FROM records WHERE id=?', ('first',))
    assert db.execute('SELECT count(*) FROM record_entries WHERE record_id=?', ('first',)).fetchone()[0] == 0
    assert db.execute('SELECT count(*) FROM record_entries WHERE record_id=?', ('second',)).fetchone()[0] == 2
    db.close()
print('PASS: SQLite draft reopen, rollback, independent records, immutable snapshots, cascading delete')
