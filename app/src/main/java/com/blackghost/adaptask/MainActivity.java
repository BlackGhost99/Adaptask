package com.blackghost.adaptask;
import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;
public class MainActivity extends Activity {
 TaskStore store; EditText title,category; Spinner priority; Button dateButton; long due;
 int statusFilter=0,priorityFilter=0;
 String searchQuery="";
 LinearLayout taskList;
 final String[] levels={"Faible","Normale","Urgente","Critique"};
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new TaskStore(this);due=System.currentTimeMillis()+3600000L;
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);
  render();
 }
 String date(long d){return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(d));}
 TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(0,dp(9),0,dp(9));return t;}
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
 void editTask(long id,String oldTitle,String oldCategory,int oldPriority,long oldDue){
  final long[] chosenDue={oldDue};
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(20),dp(8),dp(20),dp(8));
  EditText editTitle=new EditText(this);editTitle.setHint("Titre");editTitle.setText(oldTitle);form.addView(editTitle);
  EditText editCategory=new EditText(this);editCategory.setHint("Catégorie");editCategory.setText(oldCategory);form.addView(editCategory);
  Spinner editPriority=new Spinner(this);editPriority.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,levels));editPriority.setSelection(oldPriority);form.addView(editPriority);
  Button editDate=new Button(this);editDate.setText("Échéance : "+date(chosenDue[0]));form.addView(editDate);
  editDate.setOnClickListener(v->{Calendar cal=Calendar.getInstance();cal.setTimeInMillis(chosenDue[0]);new DatePickerDialog(this,(view,y,m,d)->{
   new TimePickerDialog(this,(tv,h,min)->{Calendar x=Calendar.getInstance();x.set(y,m,d,h,min,0);chosenDue[0]=x.getTimeInMillis();editDate.setText("Échéance : "+date(chosenDue[0]));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();
  },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Modifier la tâche").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
   String newTitle=editTitle.getText().toString().trim();
   if(newTitle.isEmpty()){editTitle.setError("Titre obligatoire");return;}
   if(chosenDue[0]<=System.currentTimeMillis()){Toast.makeText(this,"Choisis une échéance future",Toast.LENGTH_SHORT).show();return;}
   store.update(id,newTitle,editCategory.getText().toString().trim(),editPriority.getSelectedItemPosition(),chosenDue[0]);
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
  root.addView(label("Tableau de bord",21));
  LinearLayout line1=new LinearLayout(this);line1.setOrientation(LinearLayout.HORIZONTAL);
  line1.addView(stat("En attente",pending,Color.rgb(30,64,110)),new LinearLayout.LayoutParams(0,-2,1));
  line1.addView(stat("Aujourd'hui",today,Color.rgb(28,100,87)),new LinearLayout.LayoutParams(0,-2,1));root.addView(line1);
  LinearLayout line2=new LinearLayout(this);line2.setOrientation(LinearLayout.HORIZONTAL);
  line2.addView(stat("En retard",overdue,Color.rgb(169,54,54)),new LinearLayout.LayoutParams(0,-2,1));
  line2.addView(stat("Terminées",completed,Color.rgb(86,91,104)),new LinearLayout.LayoutParams(0,-2,1));root.addView(line2);
 }
 LinearLayout stat(String name,int value,int color){
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(12),dp(12),dp(12));
  TextView count=label(String.valueOf(value),26);count.setTextColor(color);card.addView(count);
  card.addView(label(name,14));return card;
 }
 void render(){
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(22),dp(18),dp(22));scroll.addView(root);setContentView(scroll);
  TextView heading=label("Adaptask",28);heading.setTextColor(Color.rgb(29,64,112));root.addView(heading);
  root.addView(label("Vos tâches, vos règles, votre rythme",15));
  dashboard(root);
  Button settingsButton=new Button(this);settingsButton.setText("⚙ Personnaliser les rappels");settingsButton.setOnClickListener(v->settings());root.addView(settingsButton);
  title=new EditText(this);title.setHint("Nouvelle tâche");root.addView(title);
  category=new EditText(this);category.setHint("Catégorie libre (ex : Travail)");root.addView(category);
  priority=new Spinner(this);String[] labels=new String[4];for(int p=0;p<4;p++)labels[p]=levels[p]+" · "+ReminderReceiver.interval(this,p)+" min";
  priority.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));priority.setSelection(1);root.addView(priority);
  dateButton=new Button(this);dateButton.setText("Échéance : "+date(due));root.addView(dateButton);
  dateButton.setOnClickListener(v->{Calendar cal=Calendar.getInstance();cal.setTimeInMillis(due);new DatePickerDialog(this,(view,y,m,d)->{new TimePickerDialog(this,(tv,h,min)->{Calendar x=Calendar.getInstance();x.set(y,m,d,h,min,0);due=x.getTimeInMillis();dateButton.setText("Échéance : "+date(due));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});
  Button add=new Button(this);add.setText("Ajouter la tâche");root.addView(add);
  add.setOnClickListener(v->{String t=title.getText().toString().trim();if(t.isEmpty()){title.setError("Indique un titre");return;}if(due<=System.currentTimeMillis()){Toast.makeText(this,"Choisis une échéance future",Toast.LENGTH_SHORT).show();return;}long id=store.add(t,category.getText().toString().trim(),priority.getSelectedItemPosition(),due);ReminderReceiver.schedule(this,id,due);render();});
  root.addView(label("Mes tâches",22));
  LinearLayout filters=new LinearLayout(this);filters.setOrientation(1);
  filters.addView(label("Filtrer par statut",15));
  Spinner status=new Spinner(this);
  status.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Toutes les tâches","En attente","En retard","Terminées"}));
  status.setSelection(statusFilter);filters.addView(status);
  filters.addView(label("Filtrer par priorité",15));
  Spinner level=new Spinner(this);
  level.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Toutes les priorités","Faible","Normale","Urgente","Critique"}));
  level.setSelection(priorityFilter);filters.addView(level);root.addView(filters);
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
  EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Rechercher titre ou catégorie");search.setText(searchQuery);root.addView(search);
  taskList=new LinearLayout(this);taskList.setOrientation(1);root.addView(taskList);
  search.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence text,int start,int count,int after){}
   public void onTextChanged(CharSequence text,int start,int before,int count){searchQuery=text.toString();populateTasks();}
   public void afterTextChanged(android.text.Editable text){}
  });
  populateTasks();
 }
 void populateTasks(){
  if(taskList==null)return;
  taskList.removeAllViews();
  int visibleCount=0;
  long filterNow=System.currentTimeMillis();
  String query=searchQuery.trim().toLowerCase(Locale.ROOT);
  try(Cursor c=store.all()){while(c.moveToNext()){
   final long id=c.getLong(0);String name=c.getString(1),cat=c.getString(2);int p=c.getInt(3);long time=c.getLong(4);boolean done=c.getInt(5)==1;
   if(statusFilter==1&&done)continue;
   if(statusFilter==2&&(done||time>=filterNow))continue;
   if(statusFilter==3&&!done)continue;
   if(priorityFilter>0&&p!=priorityFilter-1)continue;
   if(!query.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(query)&&!(cat==null?"":cat).toLowerCase(Locale.ROOT).contains(query))continue;
   visibleCount++;
   LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(dp(10),dp(10),dp(10),dp(10));
   TextView text=label((done?"✓ ":"● ")+name+"\n"+cat+" · "+date(time)+" · "+levels[p],16);item.addView(text);
   if(!done){
    LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
    Button edit=new Button(this);edit.setText("Modifier");edit.setOnClickListener(v->editTask(id,name,cat,p,time));actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
    Button snooze=new Button(this);snooze.setText("Reporter 10 min");snooze.setOnClickListener(v->{ReminderReceiver.schedule(this,id,System.currentTimeMillis()+600000L);Toast.makeText(this,"Rappel reporté de 10 min",Toast.LENGTH_SHORT).show();});actions.addView(snooze,new LinearLayout.LayoutParams(0,-2,1));
    Button finish=new Button(this);finish.setText("Terminer");finish.setOnClickListener(v->{store.done(id);ReminderReceiver.cancel(this,id);render();});actions.addView(finish,new LinearLayout.LayoutParams(0,-2,1));item.addView(actions);
   }
   Button delete=new Button(this);delete.setText("Supprimer");
   delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Supprimer cette tâche ?").setMessage(name).setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(dialog,which)->{ReminderReceiver.cancel(this,id);store.delete(id);render();}).show());
   item.addView(delete);taskList.addView(item);
  }}
  if(visibleCount==0)taskList.addView(label("Aucune tâche ne correspond à la recherche et aux filtres.",15));
 }
}
