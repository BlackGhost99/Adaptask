package com.blackghost.adaptask;
import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;
public class MainActivity extends Activity {
 TaskStore store; EditText title,category; Spinner priority,recurrence; Button dateButton; long due;
 int statusFilter=0,priorityFilter=0;
 final int SKY=Color.rgb(229,244,255),BLUE=Color.rgb(22,139,246),INK=Color.rgb(24,43,74);
 String searchQuery="";
 LinearLayout taskList;
 final String[] levels={"Faible","Normale","Urgente","Critique"};
 final String[] repeats={"Une seule fois","Chaque jour","Chaque semaine"};
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new TaskStore(this);due=System.currentTimeMillis()+3600000L;
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);
  try(Cursor pending=store.pending()){while(pending.moveToNext()){long id=pending.getLong(0);long when=pending.getLong(4);if(when<=0||when<System.currentTimeMillis())ReminderReceiver.schedule(this,id,System.currentTimeMillis()+60_000L);else ReminderReceiver.arm(this,id,when);}}
  render();
 }
 String date(long d){return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(d));}
 TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(INK);t.setPadding(0,dp(8),0,dp(8));return t;}
 GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
 LinearLayout panel(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(14),dp(12),dp(14),dp(12));l.setBackground(bg(Color.WHITE));l.setElevation(dp(3));return l;}
 void section(LinearLayout parent,View child){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(15));parent.addView(child,lp);}

 void settings(){
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(20),dp(8),dp(20),dp(8));
  EditText[] values=new EditText[4];
  for(int p=0;p<4;p++){form.addView(label(levels[p]+" — intervalle (minutes)",16));values[p]=new EditText(this);values[p].setInputType(2);values[p].setSingleLine(true);values[p].setText(String.valueOf(ReminderReceiver.interval(this,p)));form.addView(values[p]);}
  new AlertDialog.Builder(this).setTitle("Personnaliser les rappels").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{
   android.content.SharedPreferences.Editor editor=getSharedPreferences("adaptask_settings",MODE_PRIVATE).edit();
   for(int p=0;p<4;p++){try{int minutes=Integer.parseInt(values[p].getText().toString());if(minutes>=1&&minutes<=10080)editor.putInt("interval_"+p,minutes);}catch(NumberFormatException ignored){}}
   editor.apply();render();Toast.makeText(this,"Intervalles enregistrés",Toast.LENGTH_SHORT).show();
  }).show();
 }
 void editTask(long id,String oldTitle,String oldCategory,int oldPriority,long oldDue,int oldRepeat){
  final long[] chosenDue={oldDue};
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(20),dp(8),dp(20),dp(8));
  EditText editTitle=new EditText(this);editTitle.setHint("Titre");editTitle.setText(oldTitle);form.addView(editTitle);
  EditText editCategory=new EditText(this);editCategory.setHint("Catégorie");editCategory.setText(oldCategory);form.addView(editCategory);
  Spinner editPriority=new Spinner(this);editPriority.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,levels));editPriority.setSelection(oldPriority);form.addView(editPriority);
  Spinner editRepeat=new Spinner(this);editRepeat.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,repeats));editRepeat.setSelection(oldRepeat);form.addView(editRepeat);
  Button editDate=new Button(this);editDate.setText("Échéance : "+date(chosenDue[0]));form.addView(editDate);
  editDate.setOnClickListener(v->{Calendar cal=Calendar.getInstance();cal.setTimeInMillis(chosenDue[0]);new DatePickerDialog(this,(view,y,m,d)->{
   new TimePickerDialog(this,(tv,h,min)->{Calendar x=Calendar.getInstance();x.set(y,m,d,h,min,0);chosenDue[0]=x.getTimeInMillis();editDate.setText("Échéance : "+date(chosenDue[0]));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();
  },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Modifier la tâche").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
   String newTitle=editTitle.getText().toString().trim();
   if(newTitle.isEmpty()){editTitle.setError("Titre obligatoire");return;}
   if(chosenDue[0]<=System.currentTimeMillis()){Toast.makeText(this,"Choisis une échéance future",Toast.LENGTH_SHORT).show();return;}
   store.update(id,newTitle,editCategory.getText().toString().trim(),editPriority.getSelectedItemPosition(),chosenDue[0],editRepeat.getSelectedItemPosition());
   ReminderReceiver.cancel(this,id);ReminderReceiver.schedule(this,id,chosenDue[0]);dialog.dismiss();render();
  }));
  dialog.show();
 }

 GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(0);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
 void spaced(LinearLayout parent,View v,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(bottom);parent.addView(v,lp);}
 TextView chip(String name,boolean selected){TextView v=text(name,12,selected?Color.WHITE:INK,false);v.setGravity(Gravity.CENTER);v.setPadding(dp(11),dp(10),dp(11),dp(10));v.setBackground(shape(selected?BLUE:Color.rgb(240,244,250),12));return v;}
 void dashboard(LinearLayout root){
  int[] counts=new int[4];long now=System.currentTimeMillis();Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);long start=cal.getTimeInMillis();cal.add(Calendar.DAY_OF_MONTH,1);long end=cal.getTimeInMillis();
  try(Cursor c=store.all()){while(c.moveToNext()){boolean done=c.getInt(5)==1;long due=c.getLong(4);if(done)counts[3]++;else{counts[0]++;if(due<now)counts[2]++;else if(due>=start&&due<end)counts[1]++;}}}
  LinearLayout stats=row();String[] names={"En attente","Aujourd'hui","En retard","Terminées"};String[] icons={"▤","▣","!","✓"};int[] colors={BLUE,BLUE,Color.rgb(228,56,72),Color.rgb(24,171,111)};
  for(int i=0;i<4;i++){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER);card.setPadding(dp(2),dp(10),dp(2),dp(10));card.setBackground(shape(Color.WHITE,15));card.setElevation(dp(2));TextView count=text(icons[i]+" "+counts[i],19,colors[i],true);count.setGravity(Gravity.CENTER);card.addView(count);TextView caption=text(names[i],10,INK,false);caption.setGravity(Gravity.CENTER);card.addView(caption);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(76),1);lp.setMargins(dp(2),0,dp(2),0);stats.addView(card,lp);}
  spaced(root,stats,16);
 }
 void chooseDate(){Calendar cal=Calendar.getInstance();cal.setTimeInMillis(due);new DatePickerDialog(this,(view,y,m,d)->new TimePickerDialog(this,(tv,h,min)->{Calendar x=Calendar.getInstance();x.set(y,m,d,h,min,0);due=x.getTimeInMillis();dateButton.setText("▣  "+date(due));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show(),cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();}
 void render(){
  LinearLayout shell=new LinearLayout(this);shell.setOrientation(1);shell.setBackgroundColor(Color.rgb(247,251,255));setContentView(shell);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(12),dp(12),dp(16));scroll.addView(root);
  LinearLayout header=row();TextView menu=text("☰",23,INK,false);menu.setPadding(dp(7),0,dp(17),0);header.addView(menu);
  TextView heading=text("Adaptask",27,BLUE,true);header.addView(heading,new LinearLayout.LayoutParams(0,dp(56),1));
  TextView searchIcon=text("⌕",27,BLUE,true);searchIcon.setPadding(dp(10),0,dp(15),0);header.addView(searchIcon);
  TextView settingsIcon=chip("⚙",false);settingsIcon.setTextColor(BLUE);settingsIcon.setOnClickListener(v->settings());header.addView(settingsIcon);spaced(root,header,13);
  dashboard(root);
  LinearLayout form=panel();spaced(root,form,16);
  LinearLayout formHead=row();TextView formTitle=text("✚  Nouvelle tâche",19,INK,true);formHead.addView(formTitle,new LinearLayout.LayoutParams(0,dp(44),1));TextView idea=chip("☼",false);idea.setOnClickListener(v->settings());formHead.addView(idea);spaced(form,formHead,8);
  title=new EditText(this);title.setSingleLine(true);title.setTextSize(14);title.setHint("☷  Que devez-vous faire ?");title.setPadding(dp(12),dp(10),dp(12),dp(10));title.setBackground(shape(Color.rgb(245,249,254),13));spaced(form,title,9);
  LinearLayout topFields=row();category=new EditText(this);category.setSingleLine(true);category.setTextSize(12);category.setHint("▱ Catégorie (ex : Travail)");category.setPadding(dp(8),dp(12),dp(8),dp(12));category.setBackground(shape(Color.rgb(245,249,254),12));topFields.addView(category,new LinearLayout.LayoutParams(0,dp(52),1));
  priority=new Spinner(this);priority.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,levels));priority.setSelection(1);priority.setBackground(shape(Color.rgb(245,249,254),12));LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(0,dp(52),1);pl.leftMargin=dp(8);topFields.addView(priority,pl);spaced(form,topFields,9);
  LinearLayout bottomFields=row();dateButton=new Button(this);dateButton.setText("▣  "+date(due));dateButton.setTextSize(11);dateButton.setAllCaps(false);dateButton.setTextColor(INK);dateButton.setBackground(shape(Color.rgb(245,249,254),12));dateButton.setOnClickListener(v->chooseDate());bottomFields.addView(dateButton,new LinearLayout.LayoutParams(0,dp(52),1));
  recurrence=new Spinner(this);recurrence.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,repeats));recurrence.setBackground(shape(Color.rgb(245,249,254),12));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(52),1);rp.leftMargin=dp(8);bottomFields.addView(recurrence,rp);spaced(form,bottomFields,12);
  TextView add=chip("＋  Ajouter la tâche",true);add.setTextSize(15);spaced(form,add,0);
  add.setOnClickListener(v->{String t=title.getText().toString().trim();if(t.isEmpty()){title.setError("Indique un titre");return;}if(due<=System.currentTimeMillis()){Toast.makeText(this,"Choisis une échéance future",Toast.LENGTH_SHORT).show();return;}long id=store.add(t,category.getText().toString().trim(),priority.getSelectedItemPosition(),due,recurrence.getSelectedItemPosition());ReminderReceiver.schedule(this,id,due);render();});
  LinearLayout filters=panel();spaced(root,filters,14);
  EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(13);search.setHint("⌕  Rechercher une tâche ou une catégorie…");search.setText(searchQuery);search.setPadding(dp(10),dp(10),dp(10),dp(10));search.setBackground(shape(Color.rgb(243,247,253),14));spaced(filters,search,9);
  HorizontalScrollView statusScroll=new HorizontalScrollView(this);statusScroll.setHorizontalScrollBarEnabled(false);LinearLayout statusRow=row();String[] statuses={"☷ Toutes","◷ En attente","▣ Aujourd'hui","! En retard","✓ Terminées"};
  for(int i=0;i<statuses.length;i++){final int pos=i;TextView c=chip(statuses[i],statusFilter==i);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));lp.rightMargin=dp(5);statusRow.addView(c,lp);c.setOnClickListener(v->{statusFilter=pos;render();});}statusScroll.addView(statusRow);spaced(filters,statusScroll,7);
  HorizontalScrollView priorityScroll=new HorizontalScrollView(this);priorityScroll.setHorizontalScrollBarEnabled(false);LinearLayout priorityRow=row();String[] priorities={"▦ Toutes","⚑ Faible","⚑ Normale","⚑ Urgente","⚑ Critique"};
  for(int i=0;i<priorities.length;i++){final int pos=i;TextView c=chip(priorities[i],priorityFilter==i);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));lp.rightMargin=dp(5);priorityRow.addView(c,lp);c.setOnClickListener(v->{priorityFilter=pos;render();});}priorityScroll.addView(priorityRow);filters.addView(priorityScroll);
  TextView taskHeading=text("Mes tâches",20,INK,true);spaced(root,taskHeading,10);
  taskList=new LinearLayout(this);taskList.setOrientation(1);root.addView(taskList);
  search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){searchQuery=s.toString();populateTasks();}public void afterTextChanged(android.text.Editable e){}});
  populateTasks();
  LinearLayout nav=row();nav.setBackground(shape(Color.WHITE,12));String[] navs={"⌂\nAccueil","☷\nTâches","▱\nCatégories","⚙\nParamètres"};
  for(int i=0;i<4;i++){final int index=i;TextView button=text(navs[i],12,i==0?BLUE:INK,i==0);button.setGravity(Gravity.CENTER);nav.addView(button,new LinearLayout.LayoutParams(0,dp(61),1));button.setOnClickListener(v->{if(index==3)settings();else if(index==1)scroll.post(()->scroll.smoothScrollTo(0,taskHeading.getTop()));else if(index==2)new AlertDialog.Builder(this).setTitle("Catégories").setMessage("Les catégories sont modifiables depuis les tâches.").setPositiveButton("OK",null).show();else scroll.smoothScrollTo(0,0);});}shell.addView(nav);
 }
 void populateTasks(){
  if(taskList==null)return;taskList.removeAllViews();int count=0;long now=System.currentTimeMillis();String q=searchQuery.trim().toLowerCase(Locale.ROOT);
  try(Cursor c=store.all()){while(c.moveToNext()){
   final long id=c.getLong(0);String name=c.getString(1),cat=c.getString(2);int p=c.getInt(3);long time=c.getLong(4);boolean done=c.getInt(5)==1;int repeat=c.getInt(6);
   if(statusFilter==1&&done)continue;
   if(statusFilter==2&&(done||!new SimpleDateFormat("yyyyMMdd",Locale.getDefault()).format(new Date(time)).equals(new SimpleDateFormat("yyyyMMdd",Locale.getDefault()).format(new Date(now)))))continue;
   if(statusFilter==3&&(done||time>=now))continue;
   if(statusFilter==4&&!done)continue;
   if(priorityFilter>0&&p!=priorityFilter-1)continue;
   if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q)&&!(cat==null?"":cat).toLowerCase(Locale.ROOT).contains(q))continue;
   count++;LinearLayout card=panel();LinearLayout line=row();TextView check=chip(done?"✓":"□",false);check.setTextSize(20);line.addView(check);if(!done)check.setOnClickListener(v->finishTask(id));
   LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(9),0,0,0);
   TextView heading=text(name,15,INK,true);content.addView(heading);TextView meta=text((cat==null?"":cat)+"  ▣ "+date(time)+"  · "+levels[p]+(repeat==0?"":" ↻"),11,p==3?Color.RED:p==2?Color.rgb(225,104,21):BLUE,false);content.addView(meta);
   line.addView(content,new LinearLayout.LayoutParams(0,-2,1));
   TextView more=chip("⋮",false);more.setTextSize(21);line.addView(more);more.setOnClickListener(v->{PopupMenu popup=new PopupMenu(this,more);popup.getMenu().add("Modifier");if(!done){popup.getMenu().add("Reporter 10 min");popup.getMenu().add("Terminer");}popup.getMenu().add("Supprimer");popup.setOnMenuItemClickListener(m->{String choice=m.getTitle().toString();if(choice.equals("Modifier"))editTask(id,name,cat,p,time,repeat);else if(choice.equals("Reporter 10 min")){ReminderReceiver.cancel(this,id);ReminderReceiver.schedule(this,id,System.currentTimeMillis()+600000L);Toast.makeText(this,"Rappel reporté",Toast.LENGTH_SHORT).show();}else if(choice.equals("Terminer"))finishTask(id);else if(choice.equals("Supprimer"))new AlertDialog.Builder(this).setTitle("Supprimer cette tâche ?").setMessage(name).setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{ReminderReceiver.cancel(this,id);store.delete(id);render();}).show();return true;});popup.show();});
   card.addView(line);spaced(taskList,card,9);
  }}
  if(count==0)taskList.addView(label("Aucune tâche ne correspond à votre recherche.",14));
 }
 void finishTask(long id){ReminderReceiver.cancel(this,id);long next=store.complete(id);if(next>0)ReminderReceiver.schedule(this,id,next);render();}
}
