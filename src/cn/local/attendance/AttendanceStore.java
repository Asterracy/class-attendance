package cn.local.attendance;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import java.io.*;
import java.util.*;

final class AttendanceStore extends SQLiteOpenHelper {
    static final class Entry {
        final int position; final String name; final boolean arrived;
        Entry(int p, String n, boolean a) { position=p; name=n; arrived=a; }
    }
    static final class Record {
        final String id; final long started, saved; final List<Entry> entries;
        Record(String i,long s,long v,List<Entry> e) {id=i;started=s;saved=v;entries=e;}
        int arrived() { int n=0; for(Entry e:entries) if(e.arrived)n++; return n; }
    }
    private final Context context;
    AttendanceStore(Context c) { super(c,"attendance.db",null,1); context=c; }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE roster (position INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE draft (id INTEGER PRIMARY KEY CHECK(id=1), started INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE draft_entries (position INTEGER PRIMARY KEY, name TEXT NOT NULL, arrived INTEGER NOT NULL CHECK(arrived IN(0,1)))");
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, started INTEGER NOT NULL, saved INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE record_entries (record_id TEXT NOT NULL REFERENCES records(id) ON DELETE CASCADE, position INTEGER NOT NULL, name TEXT NOT NULL, arrived INTEGER NOT NULL CHECK(arrived IN(0,1)), PRIMARY KEY(record_id,position))");
        try(BufferedReader r=new BufferedReader(new InputStreamReader(context.getAssets().open("names.txt"),"UTF-8"))) {
            String line; int p=0; while((line=r.readLine())!=null) if(!line.trim().isEmpty()) insertName(db,p++,line.trim());
        } catch(IOException ex) { throw new IllegalStateException("无法读取初始名单",ex); }
    }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("不支持的数据版本"); }
    private void insertName(SQLiteDatabase db,int p,String name) { ContentValues v=new ContentValues();v.put("position",p);v.put("name",name);db.insertOrThrow("roster",null,v); }
    List<String> roster() { List<String> names=new ArrayList<>(); try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM roster ORDER BY position",null)) {while(c.moveToNext())names.add(c.getString(0));} return names; }
    void replaceRoster(List<String> names) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {db.delete("roster",null,null);int p=0;for(String n:names)insertName(db,p++,n);db.setTransactionSuccessful();} finally {db.endTransaction();}
    }
    Record draft() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT started FROM draft WHERE id=1",null)) {
            if(!c.moveToFirst())return null; return new Record("draft",c.getLong(0),0,entries("draft_entries",null,null));
        }
    }
    void startDraft() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {if(draft()!=null)throw new IllegalStateException("已有未保存考勤");
            ContentValues v=new ContentValues();v.put("id",1);v.put("started",System.currentTimeMillis());db.insertOrThrow("draft",null,v);
            db.execSQL("INSERT INTO draft_entries(position,name,arrived) SELECT position,name,0 FROM roster ORDER BY position");db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    void check(int position, boolean arrived) {ContentValues v=new ContentValues();v.put("arrived",arrived?1:0);if(getWritableDatabase().update("draft_entries",v,"position=?",new String[]{String.valueOf(position)})!=1)throw new IllegalStateException("草稿已失效");}
    void discard() {SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {db.delete("draft_entries",null,null);db.delete("draft",null,null);db.setTransactionSuccessful();} finally {db.endTransaction();}}
    String save() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {Record draft=draft();if(draft==null||draft.entries.isEmpty())throw new IllegalStateException("没有可保存的考勤");
            String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("started",draft.started);v.put("saved",System.currentTimeMillis());db.insertOrThrow("records",null,v);
            db.execSQL("INSERT INTO record_entries(record_id,position,name,arrived) SELECT ?,position,name,arrived FROM draft_entries",new Object[]{id});
            db.delete("draft_entries",null,null);db.delete("draft",null,null);db.setTransactionSuccessful();return id;
        } finally {db.endTransaction();}
    }
    private List<Entry> entries(String table,String where,String[] args) {List<Entry> out=new ArrayList<>();try(Cursor c=getReadableDatabase().query(table,new String[]{"position","name","arrived"},where,args,null,null,"position")) {while(c.moveToNext())out.add(new Entry(c.getInt(0),c.getString(1),c.getInt(2)==1));}return out;}
    Record record(String id) {try(Cursor c=getReadableDatabase().rawQuery("SELECT started,saved FROM records WHERE id=?",new String[]{id})) {if(!c.moveToFirst())return null;return new Record(id,c.getLong(0),c.getLong(1),entries("record_entries","record_id=?",new String[]{id}));}}
    List<Record> records() {List<Record> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM records ORDER BY saved DESC,id DESC",null)) {while(c.moveToNext())out.add(record(c.getString(0)));}return out;}
    void delete(String id) {getWritableDatabase().delete("records","id=?",new String[]{id});}
    void clearRecords() {getWritableDatabase().delete("records",null,null);}
}
