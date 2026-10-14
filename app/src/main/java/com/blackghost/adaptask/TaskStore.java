package com.blackghost.adaptask;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
public class TaskStore extends SQLiteOpenHelper {
 public TaskStore(Context c){super(c,"adaptask.db",null,4);}
 public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,category TEXT NOT NULL,priority INTEGER NOT NULL,due INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,recurrence INTEGER NOT NULL DEFAULT 0,next_alarm INTEGER NOT NULL DEFAULT 0,repeat_interval INTEGER NOT NULL DEFAULT 1,repeat_days INTEGER NOT NULL DEFAULT 0)");}
 public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion<2)db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence INTEGER NOT NULL DEFAULT 0");if(oldVersion<3){db.execSQL("ALTER TABLE tasks ADD COLUMN next_alarm INTEGER NOT NULL DEFAULT 0");db.execSQL("UPDATE tasks SET next_alarm=due WHERE done=0");}if(oldVersion<4){db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_interval INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_days INTEGER NOT NULL DEFAULT 0");}}
 public long add(String title,String category,int priority,long due,int recurrence,int interval,int days){ContentValues v=new ContentValues();v.put("title",title);v.put("category",category);v.put("priority",priority);v.put("due",due);v.put("recurrence",recurrence);v.put("repeat_interval",Math.max(1,interval));v.put("repeat_days",days);v.put("next_alarm",due);return getWritableDatabase().insert("tasks",null,v);}
 public void update(long id,String title,String category,int priority,long due,int recurrence,int interval,int days){
  ContentValues v=new ContentValues();v.put("title",title);v.put("category",category);v.put("priority",priority);v.put("due",due);v.put("recurrence",recurrence);v.put("repeat_interval",Math.max(1,interval));v.put("repeat_days",days);v.put("next_alarm",due);
  getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});
 }
 public void delete(long id){getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)});}
 public long complete(long id){
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
  try(Cursor c=db.rawQuery("SELECT due,recurrence,repeat_interval,repeat_days FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){
   if(!c.moveToFirst()){db.setTransactionSuccessful();return -1;}
   long due=c.getLong(0);int recurrence=c.getInt(1),interval=Math.max(1,c.getInt(2)),days=c.getInt(3);
   ContentValues v=new ContentValues();
   if(recurrence==0){v.put("done",1);v.put("next_alarm",0);db.update("tasks",v,"id=? AND done=0",new String[]{String.valueOf(id)});db.setTransactionSuccessful();return -1;}
   long next=nextDue(due,recurrence,interval,days,System.currentTimeMillis());
   v.put("due",next);v.put("done",0);v.put("next_alarm",next);
   db.update("tasks",v,"id=? AND done=0",new String[]{String.valueOf(id)});db.setTransactionSuccessful();return next;
  }finally{db.endTransaction();}
 }
 public static long nextDue(long due,int recurrence,int interval,int days,long now){
  Calendar cal=Calendar.getInstance();cal.setTimeInMillis(due);interval=Math.max(1,interval);
  if(recurrence==3){int day=cal.get(Calendar.DAY_OF_MONTH);int hour=cal.get(Calendar.HOUR_OF_DAY),minute=cal.get(Calendar.MINUTE),second=cal.get(Calendar.SECOND);
   do{cal.set(Calendar.DAY_OF_MONTH,1);cal.add(Calendar.MONTH,1);int max=cal.getActualMaximum(Calendar.DAY_OF_MONTH);cal.set(Calendar.DAY_OF_MONTH,Math.min(day,max));cal.set(Calendar.HOUR_OF_DAY,hour);cal.set(Calendar.MINUTE,minute);cal.set(Calendar.SECOND,second);}while(cal.getTimeInMillis()<=now);
  }else if(recurrence==5){
   if(days==0)days=1<<(cal.get(Calendar.DAY_OF_WEEK)-1);
   do{cal.add(Calendar.DAY_OF_MONTH,1);}while(cal.getTimeInMillis()<=now||(days&(1<<(cal.get(Calendar.DAY_OF_WEEK)-1)))==0);
  }else{
   int field=recurrence==2?Calendar.WEEK_OF_YEAR:Calendar.DAY_OF_MONTH;
   int step=recurrence==4?interval:1;
   do{cal.add(field,step);}while(cal.getTimeInMillis()<=now);
  }
  return cal.getTimeInMillis();
 }
 public Cursor all(){return getReadableDatabase().rawQuery("SELECT id,title,category,priority,due,done,recurrence,repeat_interval,repeat_days FROM tasks ORDER BY done ASC,priority DESC,due ASC",null);}
 public Cursor pending(){return getReadableDatabase().rawQuery("SELECT id,title,priority,due,next_alarm FROM tasks WHERE done=0",null);}
 public long nextAlarm(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT next_alarm FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getLong(0):0;}}
 public void setNextAlarm(long id,long when){ContentValues v=new ContentValues();v.put("next_alarm",when);getWritableDatabase().update("tasks",v,"id=? AND done=0",new String[]{String.valueOf(id)});}
 public boolean claimAlarm(long id,long expected,long replacement){ContentValues v=new ContentValues();v.put("next_alarm",replacement);return getWritableDatabase().update("tasks",v,"id=? AND done=0 AND next_alarm=?",new String[]{String.valueOf(id),String.valueOf(expected)})==1;}
 public boolean isPending(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
 public String title(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT title FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getString(0):"Tâche";}}
 public int priority(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT priority FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getInt(0):1;}}
}
