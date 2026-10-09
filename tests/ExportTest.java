package cn.local.attendance;
import java.nio.file.*;
import java.util.*;
public final class ExportTest {
 public static void main(String[] args) throws Exception {
  List<AttendanceStore.Entry> entries=Arrays.asList(new AttendanceStore.Entry(0,"0001234567890123456789","虚构甲",1),new AttendanceStore.Entry(1,"512600000001","虚构乙",0),new AttendanceStore.Entry(2,"=1+1","=1+1 & <虚构> \"名\"",2),new AttendanceStore.Entry(3,"512600000003","虚构同名",2),new AttendanceStore.Entry(4,"512600000004","虚构同名",1),new AttendanceStore.Entry(5,"512600000005","虚构𠮷",0));
  AttendanceStore.Record record=new AttendanceStore.Record("test-record-001","虚构活动 & <例>",1728306000000L,1728306060000L,entries);
  if(record.arrived()!=2||record.absent()!=2||record.leave()!=2)throw new AssertionError("count");
  Files.write(Paths.get(args[0]),XlsxExporter.export(record));
  List<AttendanceStore.Entry> old=Arrays.asList(new AttendanceStore.Entry(0,"","虚构旧甲",1),new AttendanceStore.Entry(1,"","虚构旧乙",0));
  Files.write(Paths.get(args[1]),XlsxExporter.export(new AttendanceStore.Record("old-snapshot","",1000L,2000L,old)));
  System.out.println("PASS: generated new three-state and legacy synthetic exports");
 }
}
