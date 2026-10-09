package cn.local.attendance;

import java.util.*;

/** Pure rules shared by the app and JVM tests. Fixtures use fictional people only. */
final class AttendanceRules {
    static final int ABSENT=0, PRESENT=1, LEAVE=2;
    static String statusLabel(int status) {
        switch(status) {case ABSENT:return "未到";case PRESENT:return "已到";case LEAVE:return "请假";default:throw new IllegalArgumentException("无效考勤状态");}
    }
    static String activityName(String value) {
        String name=value==null?"":value.trim();
        if(name.isEmpty())throw new IllegalArgumentException("请填写活动名称");
        if(name.codePointCount(0,name.length())>100)throw new IllegalArgumentException("活动名称最多 100 字");
        for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))throw new IllegalArgumentException("活动名称含无效字符");
        return name;
    }
    static String activityLabel(String activity) {return activity.isEmpty()?"未命名活动（旧记录）":activity;}
    static String personLabel(String id,String name) {return (id.isEmpty()?"学号未提供":id)+"  "+name;}
    static final class Person {
        final String studentId,name;
        Person(String id,String n) {studentId=id;name=n;}
    }
    static List<Person> parseRoster(String text) {
        List<Person> people=new ArrayList<>();Set<String> ids=new HashSet<>();
        if(text.isEmpty())return people;
        String[] lines=text.split("\\r?\\n",-1);
        for(int i=0;i<lines.length;i++) {
            if(i==lines.length-1&&lines[i].isEmpty())break;
            String[] fields=lines[i].split("\\t",-1);
            if(fields.length!=2)throw new IllegalArgumentException("名单第 "+(i+1)+" 行须为学号、姓名两列");
            String id=fields[0],name=fields[1];
            if(id.isEmpty()||!id.equals(id.trim())||id.length()>64||name.isEmpty()||!name.equals(name.trim())||name.codePointCount(0,name.length())>40)throw new IllegalArgumentException("名单第 "+(i+1)+" 行格式无效");
            for(char c:lines[i].toCharArray())if(c!='\t'&&(Character.isISOControl(c)||c=='\ufeff'))throw new IllegalArgumentException("名单含无效字符");
            if(!ids.add(id))throw new IllegalArgumentException("名单含重复学号");
            people.add(new Person(id,name));
        }
        if(people.size()>2000)throw new IllegalArgumentException("名单最多 2000 人");
        return people;
    }
    /** Match duplicate names by position only when the entire sequence agrees. */
    static Map<Integer,String> matchIds(List<Person> current,List<Person> incoming) {
        Map<Integer,String> out=new HashMap<>(); boolean same=current.size()==incoming.size();
        if(same)for(int i=0;i<current.size();i++)if(!current.get(i).name.equals(incoming.get(i).name)){same=false;break;}
        Map<String,Integer> oldCounts=new HashMap<>(),newCounts=new HashMap<>();Map<String,String> ids=new HashMap<>();
        for(Person p:current)oldCounts.put(p.name,oldCounts.getOrDefault(p.name,0)+1);
        for(Person p:incoming){newCounts.put(p.name,newCounts.getOrDefault(p.name,0)+1);ids.put(p.name,p.studentId);}
        for(int i=0;i<current.size();i++) {
            String name=current.get(i).name;
            if(same)out.put(i,incoming.get(i).studentId);
            else if(oldCounts.get(name)==1&&newCounts.getOrDefault(name,0)==1)out.put(i,ids.get(name));
        }
        return out;
    }
}
