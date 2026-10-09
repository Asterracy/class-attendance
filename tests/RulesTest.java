package cn.local.attendance;
import java.util.*;
public final class RulesTest {
 static void expectError(Runnable r){try{r.run();throw new AssertionError("expected invalid input");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args){
  for(String value:new String[]{""," ","\t\n",null})expectError(()->AttendanceRules.activityName(value));
  expectError(()->AttendanceRules.activityName("x\ny"));
  if(!AttendanceRules.activityName(" 虚构活动 ").equals("虚构活动"))throw new AssertionError("trim");
  expectError(()->AttendanceRules.statusLabel(3));expectError(()->AttendanceRules.statusLabel(-1));
  List<AttendanceRules.Person> incoming=AttendanceRules.parseRoster("0001\t虚构甲\n0002\t虚构同名\n0003\t虚构同名\n");
  if(!incoming.get(0).studentId.equals("0001"))throw new AssertionError("ID text");
  expectError(()->AttendanceRules.parseRoster("0001\t虚构甲\n0001\t虚构乙\n"));
  expectError(()->AttendanceRules.parseRoster("\t虚构甲\n"));
  List<AttendanceRules.Person> old=Arrays.asList(new AttendanceRules.Person("","虚构甲"),new AttendanceRules.Person("","虚构同名"),new AttendanceRules.Person("","虚构同名"));
  Map<Integer,String> exact=AttendanceRules.matchIds(old,incoming);
  if(exact.size()!=3||!exact.get(2).equals("0003"))throw new AssertionError("ordered duplicates");
  List<AttendanceRules.Person> reordered=Arrays.asList(old.get(1),old.get(0),old.get(2));
  Map<Integer,String> unique=AttendanceRules.matchIds(reordered,incoming);
  if(unique.size()!=1||!unique.get(1).equals("0001"))throw new AssertionError("ambiguous duplicates");
  if(!AttendanceRules.activityLabel("").contains("旧记录"))throw new AssertionError("legacy activity");
  System.out.println("PASS: activity validation, status bounds, ID text, duplicate rejection and safe roster/draft matching");
 }
}
