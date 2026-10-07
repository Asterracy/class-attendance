package cn.local.attendance;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int GREEN=0xff166a53, INK=0xff182923, MUTED=0xff65736d, BG=0xfffafcfb;
    private AttendanceStore store;
    private LinearLayout root, body, footer;
    private TextView summary;
    private String screen="home", currentRecord, pendingExport;
    private boolean busy;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new AttendanceStore(this);
        if(state!=null) {screen=state.getString("screen","home");currentRecord=state.getString("record");pendingExport=state.getString("export");}
        try {store.getWritableDatabase();render();}catch(Exception ex){fatal(ex);}
    }
    @Override public void onSaveInstanceState(Bundle state) {super.onSaveInstanceState(state);state.putString("screen",screen);state.putString("record",currentRecord);state.putString("export",pendingExport);}
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color) {TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable round(int color,int stroke) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Button button(String title,boolean primary,Runnable action) {Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:GREEN);b.setBackground(round(primary?GREEN:Color.WHITE,primary?0:0xffd9e5df));b.setMinHeight(dp(52));b.setOnClickListener(v->action.run());return b;}
    private void base(String title) {
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            }else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout header=new LinearLayout(this);header.setPadding(dp(20),dp(8),dp(12),dp(8));header.setGravity(Gravity.CENTER_VERTICAL);
        if(!screen.equals("home")) {Button back=button("‹",false,()->home());back.setContentDescription("返回姓名列表");header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        TextView t=text(title,23,INK);t.setTypeface(null,Typeface.BOLD);t.setPadding(dp(screen.equals("home")?0:12),0,0,0);header.addView(t,new LinearLayout.LayoutParams(0,dp(56),1));
        Button more=button("⋮",false,()->{});more.setContentDescription("更多：记录、名单与说明");more.setBackgroundColor(Color.TRANSPARENT);more.setTextSize(26);more.setOnClickListener(v->menu(v));header.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),0,dp(20),0);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);footer.setPadding(dp(20),dp(12),dp(20),dp(16));root.addView(footer);setContentView(root);root.requestApplyInsets();
    }
    private LinearLayout scrolling() {ScrollView sv=new ScrollView(this);sv.setFillViewport(false);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);body.addView(sv,new LinearLayout.LayoutParams(-1,0,1));return list;}
    private void divider(LinearLayout list) {View d=new View(this);d.setBackgroundColor(0xffe5ece8);list.addView(d,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void actionButton(Button b) {LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(8);footer.addView(b,p);}
    private void home(){screen="home";currentRecord=null;render();}
    private void render() {
        if(screen.equals("history")){history();return;}if(screen.equals("detail")&&currentRecord!=null){detail(currentRecord);return;}
        AttendanceStore.Record draft=store.draft();base(draft==null?"班级考勤":"正在考勤");
        if(draft==null) {
            List<String> names=store.roster();LinearLayout list=scrolling();
            if(names.isEmpty()) {TextView empty=text("尚未导入姓名\n点击右上角 ⋮ → 编辑名单",17,MUTED);empty.setPadding(0,dp(40),0,dp(40));list.addView(empty);}
            for(String name:names) {TextView row=text(name,19,INK);row.setMinHeight(dp(54));list.addView(row);divider(list);}
            actionButton(button("开始考勤",true,()->{
                if(store.roster().isEmpty()){editRoster();return;}
                safe(()->{store.startDraft();render();});
            }));
        }else {
            summary=text("",15,GREEN);summary.setPadding(0,dp(4),0,dp(12));body.addView(summary);updateSummary();
            LinearLayout list=scrolling();
            for(AttendanceStore.Entry e:draft.entries) {
                CheckBox box=new CheckBox(this);box.setText(e.name);box.setTextSize(19);box.setTextColor(INK);box.setButtonTintList(ColorStateList.valueOf(GREEN));box.setMinHeight(dp(56));box.setPadding(dp(4),0,dp(8),0);box.setChecked(e.arrived);box.setContentDescription(e.name+"，"+(e.arrived?"已到":"未到"));
                box.setOnCheckedChangeListener((v,checked)->{try {store.check(e.position,checked);box.setContentDescription(e.name+"，"+(checked?"已到":"未到"));updateSummary();}catch(Exception ex){box.setOnCheckedChangeListener(null);box.setChecked(!checked);error(ex);render();}});list.addView(box);divider(list);
            }
            actionButton(button("保存本次考勤",true,()->saveDraft()));
            actionButton(button("取消本次",false,()->confirm("取消本次考勤？","当前勾选草稿将被清空，已保存记录不受影响。","确认取消",()->{store.discard();render();})));
        }
    }
    private void updateSummary(){AttendanceStore.Record r=store.draft();if(r!=null&&summary!=null)summary.setText("共 "+r.entries.size()+" 人   ·   已到 "+r.arrived()+"   ·   未到 "+(r.entries.size()-r.arrived()));}
    private void saveDraft() {
        AttendanceStore.Record r=store.draft();if(r==null)return;
        confirm("保存考勤","已到 "+r.arrived()+" 人，未到 "+(r.entries.size()-r.arrived())+" 人。\n保存为独立记录，不覆盖历史。","确认保存",()->{
            currentRecord=store.save();screen="detail";render();toast("已保存到本机");
        });
    }
    private void menu(View anchor) {
        PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add("查看记录");p.getMenu().add("编辑名单");p.getMenu().add("使用说明");
        p.setOnMenuItemClickListener(item->{switch(item.getTitle().toString()){case "查看记录":screen="history";render();break;case "编辑名单":editRoster();break;default:new AlertDialog.Builder(this).setTitle("班级考勤 1.0.0").setMessage("仅在本机存储，无账号、无网络权限。\n\n开始考勤后，只勾选实际到场的人。未勾选显示“未到”，不表示处分结论。勾选草稿自动保留，退出后可继续。\n\n保存记录后可导出真正 Excel (.xlsx)。在文件选择器选择位置；可从历史重新导出。修改名单不影响已有记录。\n\n卸载应用或系统清除应用数据会删除本机记录，请先导出需要保留的记录。").setPositiveButton("知道了",null).show();}return true;});p.show();
    }
    private void history() {
        base("考勤记录");List<AttendanceStore.Record> records=store.records();LinearLayout list=scrolling();
        if(records.isEmpty()){TextView t=text("还没有保存的记录",17,MUTED);t.setPadding(0,dp(32),0,0);list.addView(t);}
        for(AttendanceStore.Record r:records) {TextView row=text(XlsxExporter.date(r.saved)+"\n已到 "+r.arrived()+" / "+r.entries.size()+" 人",16,INK);row.setPadding(dp(12),dp(16),dp(12),dp(16));row.setBackground(round(Color.WHITE,0xffe5ece8));row.setOnClickListener(v->{currentRecord=r.id;screen="detail";render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);list.addView(row,lp);}
        if(!records.isEmpty())actionButton(button("清空全部历史",false,()->confirm("永久清空全部历史？","将删除 "+records.size()+" 条已保存考勤，无法撤销。请确认需要的记录已导出。\n当前名单和勾选草稿会保留。","确认清空历史",()->{store.clearRecords();history();})));
    }
    private void detail(String id) {
        AttendanceStore.Record r=store.record(id);if(r==null){screen="history";history();return;}base("考勤详情");
        TextView meta=text("保存于 "+XlsxExporter.date(r.saved)+"\n共 "+r.entries.size()+" 人 · 已到 "+r.arrived()+" · 未到 "+(r.entries.size()-r.arrived()),14,MUTED);meta.setPadding(0,dp(4),0,dp(16));body.addView(meta);
        LinearLayout list=scrolling();for(AttendanceStore.Entry e:r.entries){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(e.name,19,INK);row.addView(name,new LinearLayout.LayoutParams(0,dp(54),1));TextView status=text(e.arrived?"已到":"未到",16,e.arrived?GREEN:MUTED);row.addView(status);list.addView(row);divider(list);}
        actionButton(button("导出 Excel (.xlsx)",true,()->export(id)));
        actionButton(button("删除这条记录",false,()->confirm("永久删除这条记录？",XlsxExporter.date(r.saved)+"\n已到 "+r.arrived()+" / "+r.entries.size()+" 人。\n删除后无法撤销，请先导出需要保留的记录。","确认删除",()->{store.delete(id);screen="history";render();})));
    }
    private void editRoster() {
        if(store.draft()!=null){new AlertDialog.Builder(this).setTitle("有正在进行的考勤").setMessage("请先保存或取消本次考勤，再编辑名单。退出后草稿仍会保留。").setPositiveButton("返回考勤",(d,w)->home()).show();return;}
        EditText input=new EditText(this);input.setTextSize(17);input.setGravity(Gravity.TOP);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setMinLines(8);input.setMaxLines(14);input.setPadding(dp(16),dp(8),dp(16),dp(8));input.setHint("每行一个姓名，可直接粘贴");input.setText(android.text.TextUtils.join("\n",store.roster()));
        ScrollView scroll=new ScrollView(this);scroll.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("编辑名单").setMessage("每行一个姓名；也支持逗号或分号分隔。仅录入姓名。修改不会改变历史记录。").setView(scroll).setNegativeButton("取消",null).setPositiveButton("核对并保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=input.getText().toString().trim();List<String> names=new ArrayList<>();Set<String> seen=new HashSet<>();boolean duplicate=false;
            if(!value.isEmpty())for(String token:value.split("[\\r\\n,，;；\\t]+")){String name=token.trim();if(name.isEmpty())continue;if(name.codePointCount(0,name.length())>40){input.setError("单个姓名过长，请检查分隔");return;}for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i))){input.setError("姓名中含无效控制字符");return;}names.add(name);if(!seen.add(name))duplicate=true;}
            if(names.size()>2000){input.setError("最多支持 2000 人，请检查名单");return;}
            final List<String> chosen=names;
            String message=names.isEmpty()?"将清空当前姓名名单。已保存记录保留。":("将保存 "+names.size()+" 个姓名。"+(duplicate?"\n存在同名条目，将分别保留，请核对。":"")+"\n原名单将被替换，历史记录保持不变。");
            confirm(names.isEmpty()?"确认清空名单？":"确认保存名单",message,names.isEmpty()?"确认清空名单":"确认保存",()->{store.replaceRoster(chosen);dialog.dismiss();home();toast("名单已保存");});
        }));dialog.show();
    }
    private void export(String id) {
        if(busy){toast("正在导出，请稍候");return;}AttendanceStore.Record r=store.record(id);if(r==null){toast("记录不存在");return;}
        pendingExport=id;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");i.putExtra(Intent.EXTRA_TITLE,"考勤_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date(r.saved))+"_"+r.id.substring(0,8)+".xlsx");
        try{startActivityForResult(i,41);}catch(ActivityNotFoundException ex){pendingExport=null;toast("此手机没有文件保存组件，无法选择导出位置");}
    }
    @Override public void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request!=41)return;String id=pendingExport;pendingExport=null;if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();busy=true;toast("正在保存 Excel…");
        new Thread(()->{try{AttendanceStore.Record r=store.record(id);if(r==null)throw new IOException("考勤记录已不存在");byte[] bytes=XlsxExporter.export(r);try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入选定位置");out.write(bytes);out.flush();}runOnUiThread(()->{busy=false;toast("Excel 已导出");});}catch(Exception ex){runOnUiThread(()->{busy=false;error(ex);});}},"excel-export").start();
    }
    private void confirm(String title,String message,String positive,Runnable action){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("返回",null).setPositiveButton(positive,(d,w)->safe(action)).show();}
    private void safe(Runnable r){try{r.run();}catch(Exception ex){error(ex);}}
    private void error(Exception ex){new AlertDialog.Builder(this).setTitle("操作未完成").setMessage("数据写入或文件操作失败，请检查可用空间后重试。\n"+(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage())).setPositiveButton("知道了",null).show();}
    private void fatal(Exception ex){new AlertDialog.Builder(this).setTitle("无法打开本机记录").setMessage("为保护现有数据，应用未重置数据库。\n"+ex.getMessage()).setPositiveButton("关闭",(d,w)->finish()).setCancelable(false).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(!screen.equals("home")){home();return;}if(store.draft()!=null){new AlertDialog.Builder(this).setTitle("退出并保留草稿？").setMessage("当前勾选已保存在本机，下次打开可继续。还未生成正式考勤记录。").setNegativeButton("继续考勤",null).setPositiveButton("保留草稿并退出",(d,w)->finish()).show();}else super.onBackPressed();}
}
