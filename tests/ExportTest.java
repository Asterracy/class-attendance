package cn.local.attendance;
import java.nio.file.*;
import java.util.*;
public final class ExportTest {
 public static void main(String[] args) throws Exception {
  List<AttendanceStore.Entry> entries=Arrays.asList(new AttendanceStore.Entry(0,"测试甲",true),new AttendanceStore.Entry(1,"测试乙",false),new AttendanceStore.Entry(2,"=1+1 & <测试> \"名\"",true),new AttendanceStore.Entry(3,"同名",false),new AttendanceStore.Entry(4,"同名",true),new AttendanceStore.Entry(5,"扩展字𠮷",false));
  AttendanceStore.Record record=new AttendanceStore.Record("test-record-001",1728306000000L,1728306060000L,entries);
  if(record.arrived()!=3)throw new AssertionError("count");
  Files.write(Paths.get(args[0]),XlsxExporter.export(record));
  System.out.println("Exported 6 synthetic entries: 3 arrived, 3 not arrived");
 }
}
