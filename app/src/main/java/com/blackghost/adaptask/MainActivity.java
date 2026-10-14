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
 void dashboard(LinearLayout root){
  int pending=0,overdue=0,today=0,completed=0;
  long now=System.currentTimeMillis();
  Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
  long start=cal.getTimeInMillis();cal.add(Calendar.DAY_OF_MONTH,1);long end=cal.getTimeInMillis();
  try(Cursor c=store.all()){while(c.moveToNext()){
   boolean done=c.getInt(5)==1;long deadline=c.getLong(4);
   if(done){completed++;continue;}
   pending++;
   if(deadline<now)overdue++;
   else if(deadline>=start&&deadline<end)today++;
  }}

  LinearLayout stats=panel();stats.setBackground(bg(SKY));
  LinearLayout line1=new LinearLayout(this);line1.setOrientation(LinearLayout.HORIZONTAL);
  line1.addView(stat("En attente",pending,Color.rgb(30,64,110)),new LinearLayout.LayoutParams(0,-2,1));
  line1.addView(stat("Aujourd'hui",today,Color.rgb(28,100,87)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(line1);
  LinearLayout line2=new LinearLayout(this);line2.setOrientation(LinearLayout.HORIZONTAL);
  line2.addView(stat("En retard",overdue,Color.rgb(169,54,54)),new LinearLayout.LayoutParams(0,-2,1));
  line2.addView(stat("Terminées",completed,Color.rgb(86,91,104)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(line2);section(root,stats);
 }
 LinearLayout stat(String name,int value,int color){
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setBackground(bg(Color.WHITE));
  TextView count=label(String.valueOf(value),26);count.setTextColor(color);count.setTypeface(null,Typeface.BOLD);card.addView(count);
  card.addView(label(name,14));return card;
 }
 void render(){
  LinearLayout shell=new LinearLayout(this);shell.setOrientation(1);shell.setBackgroundColor(Color.rgb(247,251,255));setContentView(shell);
  ScrollView scroll=new ScrollView(this);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(15),dp(15),dp(15),dp(15));scroll.addView(root);
  TextView heading=label("Adaptask",28);heading.setTextColor(BLUE);heading.setTypeface(null,Typeface.BOLD);root.addView(heading);

  dashboard(root);
  LinearLayout form=panel();section(root,form);
  TextView formHeading=label("✚ Nouvelle tâche",21);formHeading.setTypeface(null,Typeface.BOLD);form.addView(formHeading);
  Button settingsButton=new Button(this);settingsButton.setText("⚙ Paramètres des rappels");settingsButton.setOnClickListener(v->settings());form.addView(settingsButton);
  title=new EditText(this);title.setHint("Que devez-vous faire ?");form.addView(title);
  category=new EditText(this);category.setHint("Catégorie (ex : Travail)");form.addView(category);
  priority=new Spinner(this);String[] labels=new String[4];for(int p=0;p<4;p++)labels[p]=levels[p]+" · "+ReminderReceiver.interval(this,p)+" min";
  priority.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));priority.setSelection(1);form.addView(priority);
  recurrence=new Spinner(this);recurrence.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,repeats));form.addView(label("Répétition",15));form.addView(recurrence);
  dateButton=new Button(this);dateButton.setText("Échéance : "+date(due));form.addView(dateButton);
  dateButton.setOnClickListener(v->{Calendar cal=Calendar.getInstance();cal.setTimeInMillis(due);new DatePickerDialog(this,(view,y,m,d)->{new TimePickerDialog(this,(tv,h,min)->{Calendar x=Calendar.getInstance();x.set(y,m,d,h,min,0);due=x.getTimeInMillis();dateButton.setText("Échéance : "+date(due));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});
  Button add=new Button(this);add.setText("＋ Ajouter la tâche");add.setTextColor(Color.WHITE);add.setBackground(bg(BLUE));form.addView(add);
  add.setOnClickListener(v->{String t=title.getText().toString().trim();if(t.isEmpty()){title.setError("Indique un titre");return;}if(due<=System.currentTimeMillis()){Toast.makeText(this,"Choisis une échéance future",Toast.LENGTH_SHORT).show();return;}long id=store.add(t,category.getText().toString().trim(),priority.getSelectedItemPosition(),due,recurrence.getSelectedItemPosition());ReminderReceiver.schedule(this,id,due);render();});
  TextView taskHeading=label("Mes tâches",22);taskHeading.setTypeface(null,Typeface.BOLD);root.addView(taskHeading);
  LinearLayout filters=panel();
  filters.addView(label("Filtrer par statut",15));
  Spinner status=new Spinner(this);
  status.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Toutes les tâches","En attente","En retard","Terminées"}));
  status.setSelection(statusFilter);filters.addView(status);
  filters.addView(label("Filtrer par priorité",15));
  Spinner level=new Spinner(this);
  level.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Toutes les priorités","Faible","Normale","Urgente","Critique"}));
  level.setSelection(priorityFilter);filters.addView(level);section(root,filters);
  status.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
   public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
    if(statusFilter!=position){statusFilter=position;render();}
   }
  });
  level.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
   public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
    if(priorityFilter!=position){priorityFilter=position;render();}
   }
  });
  EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Rechercher titre ou catégorie");search.setText(searchQuery);search.setPadding(dp(12),dp(12),dp(12),dp(12));search.setBackground(bg(Color.WHITE));section(root,search);
  taskList=new LinearLayout(this);taskList.setOrientation(1);root.addView(taskList);
  search.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence text,int start,int count,int after){}
   public void onTextChanged(CharSequence text,int start,int before,int count){searchQuery=text.toString();populateTasks();}
   public void afterTextChanged(android.text.Editable text){}
  });
  populateTasks();
  LinearLayout nav=new LinearLayout(this);nav.setOrientation(0);nav.setBackground(bg(Color.WHITE));
  String[] navNames={"⌂ Accueil","☷ Tâches","▣ Catégories","⚙ Paramètres"};
  for(int k=0;k<4;k++){final int index=k;Button button=new Button(this);button.setText(navNames[k]);button.setTextSize(11);button.setAllCaps(false);button.setTextColor(BLUE);button.setBackground(bg(Color.WHITE));button.setOnClickListener(v->{if(index==3)settings();else if(index==1)scroll.post(()->scroll.smoothScrollTo(0,taskHeading.getTop()));else if(index==2)new AlertDialog.Builder(this).setTitle("Catégories").setMessage("Les catégories sont libres et modifiables dans chaque tâche.").setPositiveButton("OK",null).show();else scroll.smoothScrollTo(0,0);});nav.addView(button,new LinearLayout.LayoutParams(0,dp(58),1));}shell.addView(nav);
 }
 void populateTasks(){
  if(taskList==null)return;
  taskList.removeAllViews();
  int visibleCount=0;
  long filterNow=System.currentTimeMillis();
  String query=searchQuery.trim().toLowerCase(Locale.ROOT);
  try(Cursor c=store.all()){while(c.moveToNext()){
   final long id=c.getLong(0);String name=c.getString(1),cat=c.getString(2);int p=c.getInt(3);long time=c.getLong(4);boolean done=c.getInt(5)==1;int repeat=c.getInt(6);
   if(statusFilter==1&&done)continue;
   if(statusFilter==2&&(done||time>=filterNow))continue;
   if(statusFilter==3&&!done)continue;
   if(priorityFilter>0&&p!=priorityFilter-1)continue;
   if(!query.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(query)&&!(cat==null?"":cat).toLowerCase(Locale.ROOT).contains(query))continue;
   visibleCount++;
   LinearLayout item=panel();
   TextView text=label((done?"✓ ":"● ")+name+"\n"+cat+" · "+date(time)+" · "+levels[p]+(repeat==0?"":" · "+repeats[repeat]),16);item.addView(text);
   if(!done){
    LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);
    Button edit=new Button(this);edit.setText("Modifier");edit.setOnClickListener(v->editTask(id,name,cat,p,time,repeat));actions.addView(edit);
    Button snooze=new Button(this);snooze.setText("Reporter 10 min");snooze.setOnClickListener(v->{ReminderReceiver.schedule(this,id,System.currentTimeMillis()+600000L);Toast.makeText(this,"Rappel reporté de 10 min",Toast.LENGTH_SHORT).show();});actions.addView(snooze);
    Button finish=new Button(this);finish.setText("Terminer");finish.setOnClickListener(v->{ReminderReceiver.cancel(this,id);long next=store.complete(id);if(next>0)ReminderReceiver.schedule(this,id,next);render();});actions.addView(finish);item.addView(actions);
   }
   Button delete=new Button(this);delete.setText("Supprimer");
   delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Supprimer cette tâche ?").setMessage(name).setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(dialog,which)->{ReminderReceiver.cancel(this,id);store.delete(id);render();}).show());
   item.addView(delete);section(taskList,item);
  }}
  if(visibleCount==0)taskList.addView(label("Aucune tâche ne correspond à la recherche et aux filtres.",15));
 }
}
