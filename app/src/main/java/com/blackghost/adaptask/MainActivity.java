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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.text.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class MainActivity extends Activity {
 SpeechRecognizer voiceRecognizer; EditText voiceTarget; String voiceLanguage="fr-FR";
 TaskStore store; EditText title,category; Spinner priority,recurrence,taskType;EditText description; Button dateButton; long due;
 int statusFilter=0,priorityFilter=0;long activeSpace=1;
 final int SKY=Color.rgb(229,244,255),BLUE=Color.rgb(22,139,246),INK=Color.rgb(24,43,74);
 String searchQuery="";
 LinearLayout taskList;
 final String[] levels={"Faible","Normale","Urgente","Critique"};
 final String[] repeats={"Une seule fois","Chaque jour","Chaque semaine","Chaque mois","Tous les X jours","Jours de la semaine"};
 int repeatInterval=2,repeatDays=0,reminderOverride=0;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new TaskStore(this);due=System.currentTimeMillis()+3600000L;
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);
  try(Cursor pending=store.pending()){while(pending.moveToNext()){long id=pending.getLong(0);long when=pending.getLong(4);if(when<=0||when==pending.getLong(3))ReminderReceiver.scheduleForDue(this,id,pending.getLong(3));else if(when>System.currentTimeMillis())ReminderReceiver.arm(this,id,when);}}
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
  Button notificationSettings=new Button(this);notificationSettings.setText("Autoriser les notifications Android");form.addView(notificationSettings);notificationSettings.setOnClickListener(v->{Intent i=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);});
  if(Build.VERSION.SDK_INT>=31){Button exactAlarm=new Button(this);exactAlarm.setText("Vérifier les alarmes et rappels exacts");form.addView(exactAlarm);exactAlarm.setOnClickListener(v->{Intent i=new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName()));try{startActivity(i);}catch(Exception e){startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}});}
  Button backup=new Button(this);backup.setText("Exporter une sauvegarde JSON");form.addView(backup);backup.setOnClickListener(v->startBackup());
  Button restore=new Button(this);restore.setText("Restaurer une sauvegarde JSON");form.addView(restore);restore.setOnClickListener(v->startRestore());
  for(int p=0;p<4;p++){form.addView(label(levels[p]+" — intervalle (minutes)",16));values[p]=new EditText(this);values[p].setInputType(2);values[p].setSingleLine(true);values[p].setText(String.valueOf(ReminderReceiver.interval(this,p)));form.addView(values[p]);}
  new AlertDialog.Builder(this).setTitle("Personnaliser les rappels").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{
   android.content.SharedPreferences.Editor editor=getSharedPreferences("adaptask_settings",MODE_PRIVATE).edit();
   for(int p=0;p<4;p++){try{int minutes=Integer.parseInt(values[p].getText().toString());if(minutes>=1&&minutes<=10080)editor.putInt("interval_"+p,minutes);}catch(NumberFormatException ignored){}}
   editor.apply();render();Toast.makeText(this,"Intervalles enregistrés",Toast.LENGTH_SHORT).show();
  }).show();
 }
 void startBackup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"adaptask-sauvegarde.json");startActivityForResult(i,501);}
 void startRestore(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,502);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
  final android.net.Uri uri=data.getData();
  if(request==501){try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Fichier inaccessible");out.write(BackupManager.exportData(this,store).getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Sauvegarde enregistrée",Toast.LENGTH_LONG).show();}catch(Exception e){new AlertDialog.Builder(this).setTitle("Échec de sauvegarde").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}
  if(request==503){try{exportReport(uri);Toast.makeText(this,"Rapport CSV exporté",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Export impossible : "+e.getMessage(),Toast.LENGTH_LONG).show();}}
  if(request==502){new AlertDialog.Builder(this).setTitle("Restaurer les données ?").setMessage("Cette opération remplacera TOUTES les tâches, espaces et catégories actuels par ceux du fichier. Fais d'abord une sauvegarde de tes données actuelles.").setNegativeButton("Annuler",null).setPositiveButton("Remplacer",(d,w)->restoreFile(uri)).show();}
 }
 void restoreFile(android.net.Uri uri){try{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Fichier inaccessible");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>20*1024*1024)throw new IOException("Fichier supérieur à 20 Mo");bytes.write(buffer,0,n);}}
  ArrayList<Long> previous=new ArrayList<>();try(Cursor c=store.pending()){while(c.moveToNext())previous.add(c.getLong(0));}
  BackupManager.restoreData(this,store,new String(bytes.toByteArray(),StandardCharsets.UTF_8));
  for(long id:previous)ReminderReceiver.cancel(this,id);
  try(Cursor c=store.pending()){while(c.moveToNext()){long id=c.getLong(0),when=c.getLong(4);if(when<=0||when==c.getLong(3))ReminderReceiver.scheduleForDue(this,id,c.getLong(3));else ReminderReceiver.schedule(this,id,Math.max(System.currentTimeMillis()+5000L,when));}}
  activeSpace=1;render();Toast.makeText(this,"Restauration terminée",Toast.LENGTH_LONG).show();
 }catch(Exception e){new AlertDialog.Builder(this).setTitle("Restauration impossible").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}
 void editTask(long id,String oldTitle,String oldCategory,int oldPriority,long oldDue,int oldRepeat,int oldInterval,int oldDays,long oldSpace,int oldReminder,int oldType,String oldDescription){
  final long[] chosenDue={oldDue};final long[] chosenSpace={oldSpace};final int[] chosenReminder={oldReminder};
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(20),dp(8),dp(20),dp(8));
  EditText editTitle=new EditText(this);editTitle.setHint("Titre");editTitle.setText(oldTitle);form.addView(editTitle);
  Button editSpace=new Button(this);editSpace.setText("Espace : "+spaceName(oldSpace));form.addView(editSpace);editSpace.setOnClickListener(v->pickSpace(idSpace->{chosenSpace[0]=idSpace;editSpace.setText("Espace : "+spaceName(idSpace));}));
  Spinner editType=new Spinner(this);editType.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tâche habituelle","Incident"}));editType.setSelection(oldType);form.addView(editType);
  EditText editDescription=new EditText(this);editDescription.setHint("Description / contexte de l'incident");editDescription.setText(oldDescription);editDescription.setMinLines(2);form.addView(editDescription);
  EditText editCategory=new EditText(this);editCategory.setHint("Catégorie");editCategory.setText(oldCategory);form.addView(editCategory);
  Spinner editPriority=new Spinner(this);editPriority.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,levels));editPriority.setSelection(oldPriority);form.addView(editPriority);
  Spinner editRepeat=new Spinner(this);editRepeat.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,repeats));editRepeat.setSelection(oldRepeat);form.addView(editRepeat);
  final int[] ruleInterval={oldInterval},ruleDays={oldDays};Button editRule=new Button(this);editRule.setText("Configurer la répétition");form.addView(editRule);editRule.setOnClickListener(v->configureRepeat(editRepeat.getSelectedItemPosition(),ruleInterval,ruleDays));
  Button reminderButton=new Button(this);reminderButton.setText(reminderDescription(chosenReminder[0]));form.addView(reminderButton);reminderButton.setOnClickListener(v->chooseReminder(chosenReminder,reminderButton));
  Button editDate=new Button(this);editDate.setText("Échéance : "+date(chosenDue[0]));form.addView(editDate);
  editDate.setOnClickListener(v->{Calendar cal=Calendar.getInstance();cal.setTimeInMillis(chosenDue[0]);new DatePickerDialog(this,(view,y,m,d)->{
   new TimePickerDialog(this,(tv,h,min)->{Calendar x=Calendar.getInstance();x.set(y,m,d,h,min,0);chosenDue[0]=x.getTimeInMillis();editDate.setText("Échéance : "+date(chosenDue[0]));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();
  },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Modifier la tâche").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
   String newTitle=editTitle.getText().toString().trim();
   if(newTitle.isEmpty()){editTitle.setError("Titre obligatoire");return;}
   if(chosenDue[0]<=System.currentTimeMillis() && store.isPending(id)){Toast.makeText(this,"Choisis une échéance future",Toast.LENGTH_SHORT).show();return;}
   if(editType.getSelectedItemPosition()==1&&editRepeat.getSelectedItemPosition()!=0){Toast.makeText(this,"Un incident ne peut pas être récurrent",Toast.LENGTH_LONG).show();return;}
   store.update(id,newTitle,editCategory.getText().toString().trim(),editPriority.getSelectedItemPosition(),chosenDue[0],editRepeat.getSelectedItemPosition(),ruleInterval[0],ruleDays[0],chosenSpace[0],chosenReminder[0],editType.getSelectedItemPosition(),editDescription.getText().toString().trim());
   ReminderReceiver.cancel(this,id);if(store.isPending(id))ReminderReceiver.scheduleForDue(this,id,chosenDue[0]);dialog.dismiss();render();
  }));
  dialog.show();
 }

 GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(0);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
 void spaced(LinearLayout parent,View v,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(bottom);parent.addView(v,lp);}
 TextView chip(String name,boolean selected){TextView v=text(name,12,selected?Color.WHITE:INK,false);v.setGravity(Gravity.CENTER);v.setPadding(dp(11),dp(10),dp(11),dp(10));v.setBackground(shape(selected?BLUE:Color.rgb(240,244,250),12));return v;}
 void configureRepeat(int mode,int[] interval,int[] days){configureRepeat(mode,interval,days,()->{});}
 void configureRepeat(int mode,int[] interval,int[] days,Runnable saved){
  if(mode==4){EditText input=new EditText(this);input.setInputType(2);input.setText(String.valueOf(Math.max(1,interval[0])));input.setSelectAllOnFocus(true);
   new AlertDialog.Builder(this).setTitle("Tous les combien de jours ?").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{try{int n=Integer.parseInt(input.getText().toString());if(n>=1&&n<=365){interval[0]=n;saved.run();}else Toast.makeText(this,"Entre 1 et 365 jours",Toast.LENGTH_SHORT).show();}catch(NumberFormatException e){Toast.makeText(this,"Nombre invalide",Toast.LENGTH_SHORT).show();}}).show();
  }else if(mode==5){String[] weekdays={"Dimanche","Lundi","Mardi","Mercredi","Jeudi","Vendredi","Samedi"};boolean[] selected=new boolean[7];for(int j=0;j<7;j++)selected[j]=(days[0]&(1<<j))!=0;
   new AlertDialog.Builder(this).setTitle("Choisir les jours").setMultiChoiceItems(weekdays,selected,(d,index,checked)->selected[index]=checked).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{int mask=0;for(int j=0;j<7;j++)if(selected[j])mask|=1<<j;if(mask==0){Toast.makeText(this,"Sélectionne au moins un jour",Toast.LENGTH_SHORT).show();return;}days[0]=mask;saved.run();}).show();
  }else new AlertDialog.Builder(this).setTitle("Répétition").setMessage("Choisis « Tous les X jours » ou « Jours de la semaine » pour définir des règles personnalisées.").setPositiveButton("OK",null).show();
 }
 interface SpaceChosen{void selected(long id);}
 String spaceName(long id){try(Cursor c=store.spaces()){while(c.moveToNext())if(c.getLong(0)==id)return c.getString(1);}return "Général";}
 void pickSpace(SpaceChosen callback){ArrayList<String> names=new ArrayList<>();ArrayList<Long> ids=new ArrayList<>();try(Cursor c=store.spaces()){while(c.moveToNext()){ids.add(c.getLong(0));names.add(c.getString(1));}}new AlertDialog.Builder(this).setTitle("Choisir un espace").setItems(names.toArray(new String[0]),(d,which)->callback.selected(ids.get(which))).setNegativeButton("Annuler",null).show();}
 void createSpace(){EditText input=new EditText(this);input.setHint("Nom de l'espace");new AlertDialog.Builder(this).setTitle("Nouvel espace").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Créer",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty())return;long id=store.addSpace(name);if(id>0){activeSpace=id;render();}else Toast.makeText(this,"Espace déjà existant",Toast.LENGTH_SHORT).show();}).show();}
 void renameSpace(){if(activeSpace==1){Toast.makeText(this,"L'espace Général est protégé",Toast.LENGTH_SHORT).show();return;}EditText input=new EditText(this);input.setText(spaceName(activeSpace));new AlertDialog.Builder(this).setTitle("Renommer l'espace").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Renommer",(d,w)->{String name=input.getText().toString().trim();if(!name.isEmpty()){try{store.renameSpace(activeSpace,name);render();}catch(Exception ex){Toast.makeText(this,"Nom déjà utilisé",Toast.LENGTH_SHORT).show();}}}).show();}
 void spaceBar(LinearLayout root){LinearLayout bar=row();TextView current=chip("▱ "+spaceName(activeSpace)+"  ▾",true);bar.addView(current,new LinearLayout.LayoutParams(0,dp(43),1));current.setOnClickListener(v->pickSpace(id->{activeSpace=id;render();}));TextView add=chip("＋ Espace",false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(43));lp.leftMargin=dp(7);bar.addView(add,lp);add.setOnClickListener(v->createSpace());TextView rename=chip("✎",false);LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-2,dp(43));rl.leftMargin=dp(6);bar.addView(rename,rl);rename.setOnClickListener(v->renameSpace());spaced(root,bar,12);}
 void manageCategories(){
  ArrayList<String> names=new ArrayList<>();ArrayList<Long> ids=new ArrayList<>();try(Cursor c=store.categories(activeSpace)){while(c.moveToNext()){ids.add(c.getLong(0));names.add(c.getString(1));}}
  String[] options=new String[names.size()+1];options[0]="＋ Ajouter une catégorie";for(int i=0;i<names.size();i++)options[i+1]=names.get(i);
  new AlertDialog.Builder(this).setTitle("Catégories · "+spaceName(activeSpace)).setItems(options,(d,which)->{
   if(which==0){EditText input=new EditText(this);input.setHint("Nom de catégorie");new AlertDialog.Builder(this).setTitle("Nouvelle catégorie").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Ajouter",(dialog,w)->{String name=input.getText().toString().trim();if(!name.isEmpty()){store.addCategory(activeSpace,name);manageCategories();}}).show();}
   else{long id=ids.get(which-1);String old=names.get(which-1);EditText input=new EditText(this);input.setText(old);new AlertDialog.Builder(this).setTitle("Renommer la catégorie").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(dialog,w)->{String name=input.getText().toString().trim();if(!name.isEmpty()){try{store.renameCategory(id,activeSpace,old,name);render();}catch(Exception ex){Toast.makeText(this,"Catégorie déjà existante",Toast.LENGTH_SHORT).show();}}}).show();}
  }).setNegativeButton("Fermer",null).show();
 }
 String reminderDescription(int minutes){return minutes<=0?"♬ Rappels : selon la priorité":"♬ Rappels : toutes les "+minutes+" min";}
 void chooseReminder(int[] value,TextView display){chooseReminder(value,display,()->{});}
 void chooseReminder(int[] value,TextView display,Runnable saved){
  String[] choices={"Selon la priorité (défaut)","Toutes les 1 min","Toutes les 5 min","Toutes les 10 min","Toutes les 15 min","Toutes les 30 min","Toutes les 60 min","Personnaliser…"};
  int[] options={0,1,5,10,15,30,60};
  new AlertDialog.Builder(this).setTitle("Fréquence de rappel").setItems(choices,(d,which)->{
   if(which<7){value[0]=options[which];display.setText(reminderDescription(value[0]));saved.run();}
   else{EditText input=new EditText(this);input.setInputType(2);input.setHint("Minutes (1 à 10080)");new AlertDialog.Builder(this).setTitle("Intervalle personnalisé").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(dialog,w)->{try{int minutes=Integer.parseInt(input.getText().toString().trim());if(minutes<1||minutes>10080){Toast.makeText(this,"Intervalle entre 1 et 10080 min",Toast.LENGTH_SHORT).show();return;}value[0]=minutes;display.setText(reminderDescription(minutes));saved.run();}catch(NumberFormatException ex){Toast.makeText(this,"Nombre invalide",Toast.LENGTH_SHORT).show();}}).show();}
  }).show();
 }
 void showReports(){
  int total=0,completed=0,late=0,incidents=0,open=0,resolved=0,measured=0;int[] incidentPriorities=new int[4];long sumResolution=0;long now=System.currentTimeMillis();
  try(Cursor c=store.reportRows(activeSpace)){while(c.moveToNext()){total++;boolean done=c.getInt(5)==1;int type=c.getInt(6);long due=c.getLong(4),created=c.getLong(8),closed=c.getLong(9);if(done)completed++;else if(due<now)late++;if(type==1){incidents++;incidentPriorities[c.getInt(3)]++;if(done){resolved++;if(closed>created&&created>0){sumResolution+=closed-created;measured++;}}else open++;}}}
  long average=measured==0?0:sumResolution/measured/60000;
  String summary="Espace : "+spaceName(activeSpace)+"\n\nTotal : "+total+"\nTerminées : "+completed+"\nEn retard : "+late+"\n\nIncidents : "+incidents+"\nOuverts : "+open+"\nRésolus : "+resolved+"\nTemps moyen de résolution (données disponibles) : "+average+" min ("+measured+" incidents mesurés)\nRépartition : faible "+incidentPriorities[0]+", normale "+incidentPriorities[1]+", urgente "+incidentPriorities[2]+", critique "+incidentPriorities[3];
  new AlertDialog.Builder(this).setTitle("Rapports et statistiques").setMessage(summary).setNegativeButton("Fermer",null).setPositiveButton("Exporter CSV",(d,w)->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/csv");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"adaptask-rapport.csv");startActivityForResult(i,503);}).show();
 }
 static String csv(String s){return "\"" +(s==null?"":s.replace("\"","\"\""))+"\"";}
 void exportReport(android.net.Uri uri)throws Exception{
  StringBuilder b=new StringBuilder("\uFEFFID;Type;Titre;Catégorie;Priorité;Échéance;Statut;Description;Création;Résolution;Durée résolution (minutes)\n");
  try(Cursor c=store.reportRows(activeSpace)){while(c.moveToNext()){
   long created=c.getLong(8),closed=c.getLong(9);String[] values={String.valueOf(c.getLong(0)),c.getInt(6)==1?"Incident":"Tâche",c.getString(1),c.getString(2),levels[c.getInt(3)],date(c.getLong(4)),c.getInt(5)==1?"Terminée":"Ouverte",c.getString(7),created>0?date(created):"",closed>0?date(closed):"",closed>created&&created>0?String.valueOf((closed-created)/60000):""};
   for(int j=0;j<values.length;j++){if(j>0)b.append(';');b.append(csv(values[j]));}b.append('\n');
  }}
  try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Fichier inaccessible");out.write(b.toString().getBytes(StandardCharsets.UTF_8));}
 }
 void dashboard(LinearLayout root){
  int[] counts=new int[4];long now=System.currentTimeMillis();Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);long start=cal.getTimeInMillis();cal.add(Calendar.DAY_OF_MONTH,1);long end=cal.getTimeInMillis();
  try(Cursor c=store.all()){while(c.moveToNext()){boolean done=c.getInt(5)==1;long due=c.getLong(4);if(c.getLong(9)!=activeSpace)continue;if(done)counts[3]++;else{counts[0]++;if(due<now)counts[2]++;else if(due>=start&&due<end)counts[1]++;}}}
  LinearLayout stats=row();String[] names={"En attente","Aujourd'hui","En retard","Terminées"};String[] icons={"▤","▣","!","✓"};int[] colors={BLUE,BLUE,Color.rgb(228,56,72),Color.rgb(24,171,111)};
  for(int i=0;i<4;i++){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER);card.setPadding(dp(2),dp(10),dp(2),dp(10));card.setBackground(shape(Color.WHITE,15));card.setElevation(dp(2));TextView count=text(icons[i]+" "+counts[i],19,colors[i],true);count.setGravity(Gravity.CENTER);card.addView(count);TextView caption=text(names[i],10,INK,false);caption.setGravity(Gravity.CENTER);card.addView(caption);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(76),1);lp.setMargins(dp(2),0,dp(2),0);stats.addView(card,lp);}
  spaced(root,stats,16);
 }
 void chooseDate(){Calendar cal=Calendar.getInstance();cal.setTimeInMillis(due);new DatePickerDialog(this,(view,y,m,d)->new TimePickerDialog(this,(tv,h,min)->{Calendar x=Calendar.getInstance();x.set(y,m,d,h,min,0);due=x.getTimeInMillis();dateButton.setText("▣  "+date(due));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show(),cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();}
 void startLocalDictation(EditText target,String lang){
  voiceTarget=target;voiceLanguage=lang;
  if(Build.VERSION.SDK_INT<31||!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
   new AlertDialog.Builder(this).setTitle("Voix locale indisponible / Offline speech unavailable")
    .setMessage("Le téléphone ne dispose pas actuellement d'un moteur vocal local compatible. Utilise la saisie texte. Aucun enregistrement ne sera envoyé sur Internet. / Please use text input; no audio will be uploaded.")
    .setPositiveButton("OK",null).show();return;
  }
  if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},52);return;
  }
  if(voiceRecognizer!=null){voiceRecognizer.destroy();voiceRecognizer=null;}
  try{
   voiceRecognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
   voiceRecognizer.setRecognitionListener(new RecognitionListener(){
    public void onReadyForSpeech(Bundle params){Toast.makeText(MainActivity.this,"Parlez / Speak",Toast.LENGTH_SHORT).show();}
    public void onBeginningOfSpeech(){}
    public void onRmsChanged(float rmsdB){}
    public void onBufferReceived(byte[] buffer){}
    public void onEndOfSpeech(){}
    public void onError(int error){Toast.makeText(MainActivity.this,"Dictée locale indisponible (code "+error+"). Saisie manuelle possible.",Toast.LENGTH_LONG).show();}
    public void onResults(Bundle results){
     ArrayList<String> phrases=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
     if(phrases!=null&&!phrases.isEmpty()&&voiceTarget!=null)voiceTarget.setText(phrases.get(0));
    }
    public void onPartialResults(Bundle partialResults){}
    public void onEvent(int eventType,Bundle params){}
   });
   Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
   intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
   intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,lang);
   intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
   voiceRecognizer.startListening(intent);
  }catch(Exception ex){Toast.makeText(this,"Reconnaissance locale indisponible / Offline recognition unavailable",Toast.LENGTH_LONG).show();}
 }
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults);
  if(requestCode==52&&grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED&&voiceTarget!=null)startLocalDictation(voiceTarget,voiceLanguage);
 }
 @Override protected void onDestroy(){
  if(voiceRecognizer!=null){voiceRecognizer.destroy();voiceRecognizer=null;}
  super.onDestroy();
 }
 void assistantDialog(){
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(18),dp(12),dp(18),dp(12));
  Spinner language=new Spinner(this);language.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Français","English"}));layout.addView(language);
  EditText command=new EditText(this);command.setMinLines(3);command.setHint("Ex : Incident climatisation en panne, priorité urgente, dans 30 minutes. / Create task check network in 2 hours.");layout.addView(command);
  Button microphone=new Button(this);microphone.setText("🎙 Dicter hors ligne / Offline voice");layout.addView(microphone);
  microphone.setOnClickListener(v->startLocalDictation(command,language.getSelectedItemPosition()==1?"en-US":"fr-FR"));
  new AlertDialog.Builder(this).setTitle("Adaptask Assistant · hors ligne / offline").setView(layout).setNegativeButton("Annuler",null).setPositiveButton("Analyser / Parse",(dialog,which)->{
   if(command.getText().toString().trim().isEmpty())return;
   AdaptaskAssistant.Draft draft=AdaptaskAssistant.parse(command.getText().toString(),language.getSelectedItemPosition()==1?"en":"fr");
   String summary="Type : "+(draft.type==1?"Incident":"Tâche / Task")+"\\nTitre / Title : "+draft.title+"\\nPriorité / Priority : "+levels[draft.priority]+"\\nDate / Due : "+date(draft.due)+"\\nCatégorie / Category : "+draft.category+"\\nRépétition / Repeat : "+repeats[draft.recurrence];
   new AlertDialog.Builder(this).setTitle("Vérifier la proposition / Review").setMessage(summary).setNegativeButton("Corriger / Edit",null).setPositiveButton("Remplir le formulaire / Fill form",(d,w)->{
    title.setText(draft.title);category.setText(draft.category);priority.setSelection(draft.priority);taskType.setSelection(draft.type);recurrence.setSelection(draft.recurrence);description.setText(draft.description);due=draft.due;dateButton.setText("▣  "+date(due));reminderOverride=draft.reminderMinutes;
    Toast.makeText(this,"Vérifie les champs puis ajoute la tâche / Review before saving",Toast.LENGTH_LONG).show();
   }).show();
  }).show();
 }
 void render(){
  LinearLayout shell=new LinearLayout(this);shell.setOrientation(1);shell.setBackgroundColor(Color.rgb(247,251,255));setContentView(shell);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(12),dp(12),dp(16));scroll.addView(root);
  LinearLayout header=row();TextView menu=text("☰",23,INK,false);menu.setPadding(dp(7),0,dp(17),0);header.addView(menu);
  TextView heading=text("Adaptask",27,BLUE,true);header.addView(heading,new LinearLayout.LayoutParams(0,dp(56),1));
  TextView searchIcon=text("⌕",27,BLUE,true);searchIcon.setPadding(dp(10),0,dp(15),0);header.addView(searchIcon);
  TextView settingsIcon=chip("⚙",false);settingsIcon.setTextColor(BLUE);settingsIcon.setOnClickListener(v->settings());header.addView(settingsIcon);spaced(root,header,13);
  spaceBar(root);dashboard(root);TextView reportButton=chip("▥ Rapports et statistiques",false);spaced(root,reportButton,13);reportButton.setOnClickListener(v->showReports());
  TextView assistantButton=chip("✦ Assistant FR / EN · saisie rapide hors ligne",true);spaced(root,assistantButton,12);assistantButton.setOnClickListener(v->assistantDialog());
  LinearLayout form=panel();spaced(root,form,16);
  LinearLayout formHead=row();TextView formTitle=text("✚  Nouvelle tâche",19,INK,true);formHead.addView(formTitle,new LinearLayout.LayoutParams(0,dp(44),1));TextView idea=chip("☼",false);idea.setOnClickListener(v->settings());formHead.addView(idea);spaced(form,formHead,8);
  title=new EditText(this);title.setSingleLine(true);title.setTextSize(14);title.setHint("☷  Que devez-vous faire ?");title.setPadding(dp(12),dp(10),dp(12),dp(10));title.setBackground(shape(Color.rgb(245,249,254),13));spaced(form,title,9);
  taskType=new Spinner(this);taskType.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tâche habituelle","Incident"}));spaced(form,taskType,8);
  description=new EditText(this);description.setHint("Description / contexte / impact (facultatif)");description.setMinLines(2);spaced(form,description,8);
  LinearLayout topFields=row();category=new EditText(this);category.setSingleLine(true);category.setTextSize(12);category.setHint("▱ Catégorie (ex : Travail)");category.setPadding(dp(8),dp(12),dp(8),dp(12));category.setBackground(shape(Color.rgb(245,249,254),12));topFields.addView(category,new LinearLayout.LayoutParams(0,dp(52),1));
  priority=new Spinner(this);priority.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,levels));priority.setSelection(1);priority.setBackground(shape(Color.rgb(245,249,254),12));LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(0,dp(52),1);pl.leftMargin=dp(8);topFields.addView(priority,pl);spaced(form,topFields,9);
  LinearLayout bottomFields=row();dateButton=new Button(this);dateButton.setText("▣  "+date(due));dateButton.setTextSize(11);dateButton.setAllCaps(false);dateButton.setTextColor(INK);dateButton.setBackground(shape(Color.rgb(245,249,254),12));dateButton.setOnClickListener(v->chooseDate());bottomFields.addView(dateButton,new LinearLayout.LayoutParams(0,dp(52),1));
  recurrence=new Spinner(this);recurrence.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,repeats));recurrence.setBackground(shape(Color.rgb(245,249,254),12));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(52),1);rp.leftMargin=dp(8);bottomFields.addView(recurrence,rp);spaced(form,bottomFields,8);
  TextView ruleButton=chip("⚙ Configurer la répétition",false);spaced(form,ruleButton,10);ruleButton.setOnClickListener(v->{int[] interval={repeatInterval},days={repeatDays};configureRepeat(recurrence.getSelectedItemPosition(),interval,days,()->{repeatInterval=interval[0];repeatDays=days[0];});});
  TextView reminderControl=chip(reminderDescription(reminderOverride),false);spaced(form,reminderControl,10);reminderControl.setOnClickListener(v->{int[] value={reminderOverride};chooseReminder(value,reminderControl,()->reminderOverride=value[0]);});
  TextView add=chip("＋  Ajouter la tâche",true);add.setTextSize(15);spaced(form,add,0);
  add.setOnClickListener(v->{String t=title.getText().toString().trim();if(t.isEmpty()){title.setError("Indique un titre");return;}if(due<=System.currentTimeMillis()){Toast.makeText(this,"Choisis une échéance future",Toast.LENGTH_SHORT).show();return;}if(taskType.getSelectedItemPosition()==1&&recurrence.getSelectedItemPosition()!=0){Toast.makeText(this,"Un incident ne peut pas être récurrent",Toast.LENGTH_LONG).show();return;}long id=store.add(t,category.getText().toString().trim(),priority.getSelectedItemPosition(),due,recurrence.getSelectedItemPosition(),repeatInterval,repeatDays,activeSpace,reminderOverride,taskType.getSelectedItemPosition(),description.getText().toString().trim());ReminderReceiver.scheduleForDue(this,id,due);render();});
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
  for(int i=0;i<4;i++){final int index=i;TextView button=text(navs[i],12,i==0?BLUE:INK,i==0);button.setGravity(Gravity.CENTER);nav.addView(button,new LinearLayout.LayoutParams(0,dp(61),1));button.setOnClickListener(v->{if(index==3)settings();else if(index==1)scroll.post(()->scroll.smoothScrollTo(0,taskHeading.getTop()));else if(index==2)manageCategories();else scroll.smoothScrollTo(0,0);});}shell.addView(nav);
 }
 void populateTasks(){
  if(taskList==null)return;taskList.removeAllViews();int count=0;long now=System.currentTimeMillis();String q=searchQuery.trim().toLowerCase(Locale.ROOT);
  try(Cursor c=store.all()){while(c.moveToNext()){
   final long id=c.getLong(0);String name=c.getString(1),cat=c.getString(2);int p=c.getInt(3);long time=c.getLong(4);boolean done=c.getInt(5)==1;int repeat=c.getInt(6),repeatEvery=c.getInt(7),repeatMask=c.getInt(8);long space=c.getLong(9);int customReminder=c.getInt(10),type=c.getInt(11);String details=c.getString(12);if(space!=activeSpace)continue;
   if(statusFilter==1&&done)continue;
   if(statusFilter==2&&(done||!new SimpleDateFormat("yyyyMMdd",Locale.getDefault()).format(new Date(time)).equals(new SimpleDateFormat("yyyyMMdd",Locale.getDefault()).format(new Date(now)))))continue;
   if(statusFilter==3&&(done||time>=now))continue;
   if(statusFilter==4&&!done)continue;
   if(priorityFilter>0&&p!=priorityFilter-1)continue;
   if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q)&&!(cat==null?"":cat).toLowerCase(Locale.ROOT).contains(q))continue;
   count++;LinearLayout card=panel();LinearLayout line=row();TextView check=chip(done?"✓":"□",false);check.setTextSize(20);line.addView(check);if(!done)check.setOnClickListener(v->finishTask(id));
   LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(9),0,0,0);
   TextView heading=text((type==1?"⚠ Incident · ":"")+name,15,INK,true);content.addView(heading);TextView meta=text((cat==null?"":cat)+"  ▣ "+date(time)+"  · "+levels[p]+(repeat==0?"":" ↻")+(customReminder>0?" · "+customReminder+" min":""),11,p==3?Color.RED:p==2?Color.rgb(225,104,21):BLUE,false);content.addView(meta);if(type==1&&!details.isEmpty())content.addView(text(details,12,INK,false));
   line.addView(content,new LinearLayout.LayoutParams(0,-2,1));
   TextView more=chip("⋮",false);more.setTextSize(21);line.addView(more);more.setOnClickListener(v->{PopupMenu popup=new PopupMenu(this,more);popup.getMenu().add("Modifier");popup.getMenu().add("Déplacer");if(!done){popup.getMenu().add("Reporter 10 min");popup.getMenu().add("Terminer");}popup.getMenu().add("Supprimer");popup.setOnMenuItemClickListener(m->{String choice=m.getTitle().toString();if(choice.equals("Déplacer"))pickSpace(selected->{store.moveTask(id,selected);render();});else if(choice.equals("Modifier"))editTask(id,name,cat,p,time,repeat,repeatEvery,repeatMask,space,customReminder,type,details);else if(choice.equals("Reporter 10 min")){ReminderReceiver.cancel(this,id);ReminderReceiver.schedule(this,id,System.currentTimeMillis()+600000L);Toast.makeText(this,"Rappel reporté",Toast.LENGTH_SHORT).show();}else if(choice.equals("Terminer"))finishTask(id);else if(choice.equals("Supprimer"))new AlertDialog.Builder(this).setTitle("Supprimer cette tâche ?").setMessage(name).setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{ReminderReceiver.cancel(this,id);store.delete(id);render();}).show();return true;});popup.show();});
   card.addView(line);spaced(taskList,card,9);
  }}
  if(count==0)taskList.addView(label("Aucune tâche ne correspond à votre recherche.",14));
 }
 void finishTask(long id){ReminderReceiver.cancel(this,id);long next=store.complete(id);if(next>0)ReminderReceiver.schedule(this,id,next);render();}
}
