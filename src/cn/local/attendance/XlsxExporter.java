package cn.local.attendance;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

final class XlsxExporter {
    static String date(long millis) {return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z",Locale.CHINA).format(new Date(millis));}
    static byte[] export(AttendanceStore.Record r) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)) {
            add(z,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            add(z,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            add(z,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"考勤记录\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            add(z,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            add(z,"xl/styles.xml","<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Microsoft YaHei\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Microsoft YaHei\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF166A53\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            StringBuilder s=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"6\" topLeftCell=\"A7\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"1\" width=\"10\" customWidth=\"1\"/><col min=\"2\" max=\"3\" width=\"30\" customWidth=\"1\"/><col min=\"4\" max=\"4\" width=\"24\" customWidth=\"1\"/><col min=\"5\" max=\"5\" width=\"12\" customWidth=\"1\"/></cols><sheetData>");
            row(s,1,text("A1","班级考勤记录",1));row(s,2,text("A2","活动名称",0)+text("B2",r.activityLabel(),0));row(s,3,text("A3","开始时间",0)+text("B3",date(r.started),0));row(s,4,text("A4","保存时间",0)+text("B4",date(r.saved),0));row(s,5,text("A5","记录编号",0)+text("B5",r.id,0));row(s,6,text("A6","序号",1)+text("B6","活动名称",1)+text("C6","学号",1)+text("D6","姓名",1)+text("E6","考勤状态",1));
            int n=7;for(AttendanceStore.Entry e:r.entries) {row(s,n,number("A"+n,n-6)+text("B"+n,r.activityLabel(),0)+text("C"+n,e.studentId,0)+text("D"+n,e.name,0)+text("E"+n,AttendanceRules.statusLabel(e.status),0));n++;}
            int last=n-1;n++;row(s,n,text("A"+n,"汇总",1));n++;row(s,n,text("A"+n,"总人数",0)+number("B"+n,r.entries.size()));n++;row(s,n,text("A"+n,"已到人数",0)+number("B"+n,r.arrived()));n++;row(s,n,text("A"+n,"未到人数",0)+number("B"+n,r.absent()));n++;row(s,n,text("A"+n,"请假人数",0)+number("B"+n,r.leave()));n++;row(s,n,text("A"+n,"说明",0)+text("B"+n,"状态仅表示本次手动选择结果；空白学号表示原记录未提供，旧记录不会补造学号或活动名称。",0));
            s.append("</sheetData><autoFilter ref=\"A6:E").append(Math.max(6,last)).append("\"/></worksheet>");add(z,"xl/worksheets/sheet1.xml",s.toString());
        }return bytes.toByteArray();
    }
    private static void add(ZipOutputStream z,String path,String xml)throws IOException {z.putNextEntry(new ZipEntry(path));z.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+xml).getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String escape(String v) {StringBuilder b=new StringBuilder();for(int i=0;i<v.length();) {int c=v.codePointAt(i);i+=Character.charCount(c);if(c==9||c==10||c==13||(c>=32&&c<=0xD7FF)||(c>=0xE000&&c<=0xFFFD)||(c>=0x10000&&c<=0x10FFFF))b.appendCodePoint(c);}return b.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String text(String ref,String v,int style) {return "<c r=\""+ref+"\" s=\""+style+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+escape(v)+"</t></is></c>";}
    private static String number(String ref,int n) {return "<c r=\""+ref+"\" t=\"n\"><v>"+n+"</v></c>";}
    private static void row(StringBuilder s,int n,String cells) {s.append("<row r=\"").append(n).append("\" ht=\"24\" customHeight=\"1\">").append(cells).append("</row>");}
}
