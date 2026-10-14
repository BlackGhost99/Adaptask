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
 void render(){
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(22),dp(18),dp(22));scroll.addView(root);setContentView(scroll);
  TextView heading=label("Adaptask",28);heading.setTextColor(Color.rgb(29,64,112));root.addView(heading);
  root.addView(label("Vos tâches, vos règles, votre rythme",15));
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
  try(Cursor c=store.all()){while(c.moveToNext()){
   final long id=c.getLong(0);String name=c.getString(1),cat=c.getString(2);int p=c.getInt(3);long time=c.getLong(4);boolean done=c.getInt(5)==1;
   LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(dp(10),dp(10),dp(10),dp(10));
   TextView text=label((done?"✓ ":"● ")+name+"\n"+cat+" · "+date(time)+" · "+levels[p],16);item.addView(text);
   if(!done){
    LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
    Button snooze=new Button(this);snooze.setText("Reporter 10 min");snooze.setOnClickListener(v->{ReminderReceiver.schedule(this,id,System.currentTimeMillis()+600000L);Toast.makeText(this,"Rappel reporté de 10 min",Toast.LENGTH_SHORT).show();});actions.addView(snooze,new LinearLayout.LayoutParams(0,-2,1));
    Button finish=new Button(this);finish.setText("Terminer");finish.setOnClickListener(v->{store.done(id);ReminderReceiver.cancel(this,id);render();});actions.addView(finish,new LinearLayout.LayoutParams(0,-2,1));item.addView(actions);
   }
   root.addView(item);
  }}
 }
}
