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
 TaskStore store; LinearLayout list; EditText title,category; Spinner priority; Button dateButton; long due;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new TaskStore(this);due=System.currentTimeMillis()+3600000L;
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);
  render();
 }
 String date(long d){return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(d));}
 void render(){
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(22),dp(18),dp(22));scroll.addView(root);setContentView(scroll);
  TextView heading=new TextView(this);heading.setText("Adaptask");heading.setTextSize(28);heading.setTextColor(Color.rgb(29,64,112));root.addView(heading);
  TextView tagline=new TextView(this);tagline.setText("Vos tâches, vos règles, votre rythme");root.addView(tagline);
  title=new EditText(this);title.setHint("Nouvelle tâche");root.addView(title);
  category=new EditText(this);category.setHint("Catégorie libre (ex : Travail)");root.addView(category);
  priority=new Spinner(this);String[] labels={"Faible · 4 h","Normale · 1 h","Urgente · 15 min","Critique · 5 min"};
  priority.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));priority.setSelection(1);root.addView(priority);
  dateButton=new Button(this);dateButton.setText("Échéance : "+date(due));root.addView(dateButton);
  dateButton.setOnClickListener(v->{Calendar cal=Calendar.getInstance();cal.setTimeInMillis(due);new DatePickerDialog(this,(view,y,m,d)->{new TimePickerDialog(this,(tv,h,min)->{Calendar x=Calendar.getInstance();x.set(y,m,d,h,min,0);due=x.getTimeInMillis();dateButton.setText("Échéance : "+date(due));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});
  Button add=new Button(this);add.setText("Ajouter la tâche");root.addView(add);
  add.setOnClickListener(v->{String t=title.getText().toString().trim();if(t.isEmpty()){title.setError("Indique un titre");return;}if(due<=System.currentTimeMillis()){Toast.makeText(this,"Choisis une échéance future",Toast.LENGTH_SHORT).show();return;}long id=store.add(t,category.getText().toString().trim(),priority.getSelectedItemPosition(),due);ReminderReceiver.schedule(this,id,due);render();});
  TextView sub=new TextView(this);sub.setText("\nMes tâches");sub.setTextSize(21);root.addView(sub);
  list=new LinearLayout(this);list.setOrientation(1);root.addView(list);
  try(Cursor c=store.all()){while(c.moveToNext()){
   final long id=c.getLong(0);String name=c.getString(1),cat=c.getString(2);int p=c.getInt(3);long time=c.getLong(4);boolean done=c.getInt(5)==1;
   LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(dp(10),dp(10),dp(10),dp(10));
   TextView text=new TextView(this);text.setText((done?"✓ ":"● ")+name+"\n"+cat+" · "+date(time)+" · "+new String[]{"Faible","Normale","Urgente","Critique"}[p]);text.setTextSize(16);item.addView(text);
   if(!done){Button finish=new Button(this);finish.setText("Terminer");finish.setOnClickListener(v->{store.done(id);ReminderReceiver.cancel(this,id);render();});item.addView(finish);}
   list.addView(item);
  }}
 }
}
