package com.blackghost.adaptask;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class NotificationActionReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  long id=i.getLongExtra("task_id",-1);if(id<0)return;
  TaskStore store=new TaskStore(c);if(!store.isPending(id))return;
  String action=i.getAction();
  if("complete".equals(action)){ReminderReceiver.cancel(c,id);long next=store.complete(id);if(next>0)ReminderReceiver.schedule(c,id,next);}
  else if("snooze".equals(action)){ReminderReceiver.cancel(c,id);ReminderReceiver.schedule(c,id,System.currentTimeMillis()+600000L);}
  ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel((int)id);
 }
}
