package com.blackghost.adaptask;
import android.content.*;
import android.database.Cursor;
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  try(Cursor rows=new TaskStore(c).pending()){
   while(rows.moveToNext()){long id=rows.getLong(0);long next=rows.getLong(4);if(next<=0||next==rows.getLong(3))ReminderReceiver.scheduleForDue(c,id,rows.getLong(3));else ReminderReceiver.schedule(c,id,Math.max(System.currentTimeMillis()+5000L,next));}
  }
 }
}
