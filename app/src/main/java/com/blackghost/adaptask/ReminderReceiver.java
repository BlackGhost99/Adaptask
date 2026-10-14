package com.blackghost.adaptask;
import android.app.*;
import android.content.*;
import android.os.Build;
public class ReminderReceiver extends BroadcastReceiver {
 public static final String CHANNEL="adaptask_reminders";
 public static final int[] DEFAULT_MINUTES={240,60,15,5};
 public static int interval(Context c,int priority){return c.getSharedPreferences("adaptask_settings",Context.MODE_PRIVATE).getInt("interval_"+priority,DEFAULT_MINUTES[Math.max(0,Math.min(3,priority))]);}
 static PendingIntent alarmIntent(Context c,long id,long when,int flags){Intent intent=new Intent(c,ReminderReceiver.class).putExtra("task_id",id).putExtra("expected",when);return PendingIntent.getBroadcast(c,(int)id,intent,flags|PendingIntent.FLAG_IMMUTABLE);}
 public static void schedule(Context c,long id,long when){new TaskStore(c).setNextAlarm(id,when);arm(c,id,when);}
 static void arm(Context c,long id,long when){
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
  PendingIntent pi=alarmIntent(c,id,when,PendingIntent.FLAG_UPDATE_CURRENT);
  try{if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else if(Build.VERSION.SDK_INT<31)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}
  catch(SecurityException ex){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}
 }
 public static void cancel(Context c,long id){
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
  PendingIntent pi=alarmIntent(c,id,0,PendingIntent.FLAG_NO_CREATE);
  if(pi!=null){am.cancel(pi);pi.cancel();}
  ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel((int)id);
 }
 static PendingIntent action(Context c,long id,String action){Intent i=new Intent(c,NotificationActionReceiver.class).setAction(action).putExtra("task_id",id);int request=(int)(id*3+(action.equals("complete")?1:2));return PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void channel(NotificationManager nm){if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Rappels Adaptask",NotificationManager.IMPORTANCE_HIGH));}
 @Override public void onReceive(Context c,Intent i){
  long id=i.getLongExtra("task_id",-1),expected=i.getLongExtra("expected",-1);if(id<0||expected<=0)return;
  TaskStore store=new TaskStore(c);long next=System.currentTimeMillis()+Math.max(1,interval(c,store.priority(id)))*60_000L;
  if(!store.claimAlarm(id,expected,next))return;
  arm(c,id,next);
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);channel(nm);
  Intent open=new Intent(c,MainActivity.class);PendingIntent tap=PendingIntent.getActivity(c,(int)id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Rappel Adaptask").setContentText(store.title(id)).setContentIntent(tap).setAutoCancel(true)
   .addAction(new Notification.Action.Builder(android.R.drawable.checkbox_on_background,"Terminer",action(c,id,"complete")).build())
   .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_recent_history,"Reporter 10 min",action(c,id,"snooze")).build()).build();
  if(Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)nm.notify((int)id,n);
 }
}
