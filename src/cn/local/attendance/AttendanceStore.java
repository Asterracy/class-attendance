package cn.local.attendance;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;

final class AttendanceStore extends SQLiteOpenHelper {
    static final class Entry {
        final int position,status; final String studentId,name;
        Entry(int p,String id,String n,int s) {AttendanceRules.statusLabel(s);position=p;studentId=id;name=n;status=s;}
        String label() {return AttendanceRules.personLabel(studentId,name);}
    }
    static final class Record {
        final String id,activity; final long started,saved; final List<Entry> entries;
        Record(String i,String a,long s,long v,List<Entry> e) {id=i;activity=a;started=s;saved=v;entries=e;}
        int count(int status) {int n=0;for(Entry e:entries)if(e.status==status)n++;return n;}
        int arrived() {return count(AttendanceRules.PRESENT);}
        int absent() {return count(AttendanceRules.ABSENT);}
        int leave() {return count(AttendanceRules.LEAVE);}
        String activityLabel() {return AttendanceRules.activityLabel(activity);}
    }
    private final Context context;
    AttendanceStore(Context c) {super(c,"attendance.db",null,2);context=c;}
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE roster (position INTEGER PRIMARY KEY, name TEXT NOT NULL, student_id TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE draft (id INTEGER PRIMARY KEY CHECK(id=1), started INTEGER NOT NULL, activity TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE draft_entries (position INTEGER PRIMARY KEY, name TEXT NOT NULL, arrived INTEGER NOT NULL CHECK(arrived IN(0,1)), student_id TEXT NOT NULL DEFAULT '', status INTEGER NOT NULL DEFAULT 0 CHECK(status IN(0,1,2)))");
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, started INTEGER NOT NULL, saved INTEGER NOT NULL, activity TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE record_entries (record_id TEXT NOT NULL REFERENCES records(id) ON DELETE CASCADE, position INTEGER NOT NULL, name TEXT NOT NULL, arrived INTEGER NOT NULL CHECK(arrived IN(0,1)), student_id TEXT NOT NULL DEFAULT '', status INTEGER NOT NULL DEFAULT 0 CHECK(status IN(0,1,2)), PRIMARY KEY(record_id,position))");
        db.execSQL("CREATE TABLE asset_updates (id INTEGER PRIMARY KEY CHECK(id=1), fingerprint TEXT NOT NULL)");
        List<AttendanceRules.Person> people=assetRoster();
        if(people.isEmpty())try(BufferedReader r=new BufferedReader(new InputStreamReader(context.getAssets().open("names.txt"),"UTF-8"))) {
            String line;while((line=r.readLine())!=null)if(!line.trim().isEmpty())people.add(new AttendanceRules.Person("",line.trim()));
        }catch(IOException ex){throw new IllegalStateException("无法读取初始名单",ex);}
        int p=0;for(AttendanceRules.Person person:people)insertName(db,p++,person);
    }
    @Override public void onConfigure(SQLiteDatabase db) {db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion==1&&newVersion==2) {
            db.execSQL("ALTER TABLE roster ADD COLUMN student_id TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE draft ADD COLUMN activity TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE records ADD COLUMN activity TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE draft_entries ADD COLUMN student_id TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE draft_entries ADD COLUMN status INTEGER NOT NULL DEFAULT 0 CHECK(status IN(0,1,2))");
            db.execSQL("UPDATE draft_entries SET status=arrived");
            db.execSQL("ALTER TABLE record_entries ADD COLUMN student_id TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE record_entries ADD COLUMN status INTEGER NOT NULL DEFAULT 0 CHECK(status IN(0,1,2))");
            db.execSQL("UPDATE record_entries SET status=arrived");
            db.execSQL("CREATE TABLE asset_updates (id INTEGER PRIMARY KEY CHECK(id=1), fingerprint TEXT NOT NULL)");
        }else throw new IllegalStateException("不支持的数据版本");
    }
    private List<AttendanceRules.Person> assetRoster() {
        try(InputStream in=context.getAssets().open("roster.txt");ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            return AttendanceRules.parseRoster(new String(out.toByteArray(),"UTF-8"));
        }catch(FileNotFoundException ex){return new ArrayList<>();}catch(IOException ex){throw new IllegalStateException("无法读取学号名单",ex);}
    }
    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);List<AttendanceRules.Person> incoming=assetRoster();if(incoming.isEmpty())return;
        String fingerprint;
        try {MessageDigest digest=MessageDigest.getInstance("SHA-256");for(AttendanceRules.Person p:incoming)digest.update((p.studentId+"\t"+p.name+"\n").getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte v:digest.digest())b.append(String.format(Locale.ROOT,"%02x",v&255));fingerprint=b.toString();}catch(Exception ex){throw new IllegalStateException("名单校验失败",ex);}
        try(Cursor c=db.rawQuery("SELECT fingerprint FROM asset_updates WHERE id=1",null)){if(c.moveToFirst()&&fingerprint.equals(c.getString(0)))return;}
        db.beginTransaction();try {
            // The user-provided current class roster is authoritative for future attendance.
            // Replacing only this table leaves all saved snapshots and the active draft intact.
            db.delete("roster",null,null);int position=0;for(AttendanceRules.Person person:incoming)insertName(db,position++,person);
            // A pre-update draft remains usable. Enrich only safely matched blank IDs; never change saved snapshots.
            List<Entry> draft=entries(db,"draft_entries",null,null);List<AttendanceRules.Person> draftPeople=new ArrayList<>();for(Entry e:draft)draftPeople.add(new AttendanceRules.Person(e.studentId,e.name));
            for(Map.Entry<Integer,String> match:AttendanceRules.matchIds(draftPeople,incoming).entrySet()) {
                Entry e=draft.get(match.getKey());if(!e.studentId.isEmpty())continue;ContentValues v=new ContentValues();v.put("student_id",match.getValue());db.update("draft_entries",v,"position=?",new String[]{String.valueOf(e.position)});
            }
            ContentValues v=new ContentValues();v.put("id",1);v.put("fingerprint",fingerprint);db.insertWithOnConflict("asset_updates",null,v,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void insertName(SQLiteDatabase db,int p,AttendanceRules.Person person) {ContentValues v=new ContentValues();v.put("position",p);v.put("name",person.name);v.put("student_id",person.studentId);db.insertOrThrow("roster",null,v);}
    List<Entry> roster() {return entries(getReadableDatabase(),"roster",null,null);}
    void replaceRoster(List<String> names) {
        List<AttendanceRules.Person> old=new ArrayList<>(),chosen=new ArrayList<>();for(Entry e:roster())old.add(new AttendanceRules.Person(e.studentId,e.name));for(String n:names)chosen.add(new AttendanceRules.Person("",n));
        Map<Integer,String> ids=AttendanceRules.matchIds(chosen,old);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {db.delete("roster",null,null);for(int p=0;p<chosen.size();p++)insertName(db,p,new AttendanceRules.Person(ids.getOrDefault(p,""),chosen.get(p).name));db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    Record draft() {try(Cursor c=getReadableDatabase().rawQuery("SELECT started,activity FROM draft WHERE id=1",null)){if(!c.moveToFirst())return null;return new Record("draft",c.getString(1),c.getLong(0),0,entries(getReadableDatabase(),"draft_entries",null,null));}}
    void startDraft(String activity) {
        String name=AttendanceRules.activityName(activity);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {if(draft()!=null)throw new IllegalStateException("已有未保存考勤");if(roster().isEmpty())throw new IllegalStateException("尚未导入名单");
            ContentValues v=new ContentValues();v.put("id",1);v.put("started",System.currentTimeMillis());v.put("activity",name);db.insertOrThrow("draft",null,v);
            db.execSQL("INSERT INTO draft_entries(position,name,arrived,student_id,status) SELECT position,name,0,student_id,0 FROM roster ORDER BY position");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void check(int position,int status) {AttendanceRules.statusLabel(status);ContentValues v=new ContentValues();v.put("status",status);v.put("arrived",status==AttendanceRules.PRESENT?1:0);if(getWritableDatabase().update("draft_entries",v,"position=?",new String[]{String.valueOf(position)})!=1)throw new IllegalStateException("草稿已失效");}
    void discard(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("draft_entries",null,null);db.delete("draft",null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    String save() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{Record draft=draft();if(draft==null||draft.entries.isEmpty())throw new IllegalStateException("没有可保存的考勤");
            String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("started",draft.started);v.put("saved",System.currentTimeMillis());v.put("activity",draft.activity);db.insertOrThrow("records",null,v);
            db.execSQL("INSERT INTO record_entries(record_id,position,name,arrived,student_id,status) SELECT ?,position,name,arrived,student_id,status FROM draft_entries",new Object[]{id});db.delete("draft_entries",null,null);db.delete("draft",null,null);db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    private List<Entry> entries(SQLiteDatabase db,String table,String where,String[] args){List<Entry> out=new ArrayList<>();boolean roster=table.equals("roster");try(Cursor c=db.query(table,roster?new String[]{"position","student_id","name"}:new String[]{"position","student_id","name","status"},where,args,null,null,"position")){while(c.moveToNext())out.add(new Entry(c.getInt(0),c.getString(1),c.getString(2),roster?0:c.getInt(3)));}return out;}
    Record record(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT started,saved,activity FROM records WHERE id=?",new String[]{id})){if(!c.moveToFirst())return null;return new Record(id,c.getString(2),c.getLong(0),c.getLong(1),entries(getReadableDatabase(),"record_entries","record_id=?",new String[]{id}));}}
    List<Record> records(){List<Record> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM records ORDER BY saved DESC,id DESC",null)){while(c.moveToNext())out.add(record(c.getString(0)));}return out;}
    void delete(String id){getWritableDatabase().delete("records","id=?",new String[]{id});}
    void clearRecords(){getWritableDatabase().delete("records",null,null);}
}
