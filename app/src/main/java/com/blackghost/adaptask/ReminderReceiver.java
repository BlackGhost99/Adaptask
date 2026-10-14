package com.blackghost.adaptask;
import android.app.*;
import android.content.*;
import android.os.Build;
public class ReminderReceiver extends BroadcastReceiver {
 public static final String CHANNEL="adaptask_reminders";
 public static final int[] DEFAULT_MINUTES={240,60,15,5};
 public static int interval(Context c,int priority){
  return c.getSharedPreferences("adaptask_settings",Context.MODE_PRIVATE).getInt("interval_"+priority,DEFAULT_MINUTES[Math.max(0,Math.min(3,priority))]);
 }
 public static void schedule(Context c,long id,long when){
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
  Intent intent=new Intent(c,ReminderReceiver.class).putExtra("task_id",id);
  PendingIntent pi=PendingIntent.getBroadcast(c,(int)id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
  else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
 }
 public static void cancel(Context c,long id){
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
  PendingIntent pi=PendingIntent.getBroadcast(c,(int)id,new Intent(c,ReminderReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
  if(pi!=null)am.cancel(pi);
 }
 @Override public void onReceive(Context c,Intent i){
  long id=i.getLongExtra("task_id",-1);TaskStore store=new TaskStore(c);
  if(id<0||!store.isPending(id))return;
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Rappels Adaptask",NotificationManager.IMPORTANCE_HIGH));
  Intent open=new Intent(c,MainActivity.class);
  PendingIntent tap=PendingIntent.getActivity(c,(int)id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Rappel Adaptask").setContentText(store.title(id)).setContentIntent(tap).setAutoCancel(true).build();
  if(Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)nm.notify((int)id,n);
  schedule(c,id,System.currentTimeMillis()+interval(c,store.priority(id))*60_000L);
 }
}
