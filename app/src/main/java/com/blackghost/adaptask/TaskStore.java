package com.blackghost.adaptask;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
public class TaskStore extends SQLiteOpenHelper {
 public TaskStore(Context c){super(c,"adaptask.db",null,3);}
 public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,category TEXT NOT NULL,priority INTEGER NOT NULL,due INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,recurrence INTEGER NOT NULL DEFAULT 0,next_alarm INTEGER NOT NULL DEFAULT 0)");}
 public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion<2)db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence INTEGER NOT NULL DEFAULT 0");if(oldVersion<3)db.execSQL("ALTER TABLE tasks ADD COLUMN next_alarm INTEGER NOT NULL DEFAULT 0");}
 public long add(String title,String category,int priority,long due,int recurrence){ContentValues v=new ContentValues();v.put("title",title);v.put("category",category);v.put("priority",priority);v.put("due",due);v.put("recurrence",recurrence);v.put("next_alarm",due);return getWritableDatabase().insert("tasks",null,v);}
 public void update(long id,String title,String category,int priority,long due,int recurrence){
  ContentValues v=new ContentValues();v.put("title",title);v.put("category",category);v.put("priority",priority);v.put("due",due);v.put("recurrence",recurrence);v.put("next_alarm",due);
  getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});
 }
 public void delete(long id){getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)});}
 public long complete(long id){
  try(Cursor c=getReadableDatabase().rawQuery("SELECT due,recurrence FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){
   if(!c.moveToFirst())return -1;
   long due=c.getLong(0);int recurrence=c.getInt(1);ContentValues v=new ContentValues();
   if(recurrence==0){v.put("done",1);v.put("next_alarm",0);getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});return -1;}
   Calendar cal=Calendar.getInstance();cal.setTimeInMillis(due);
   int field=recurrence==1?Calendar.DAY_OF_MONTH:Calendar.WEEK_OF_YEAR;
   long now=System.currentTimeMillis();do{cal.add(field,1);}while(cal.getTimeInMillis()<=now);
   long next=cal.getTimeInMillis();v.put("due",next);v.put("done",0);v.put("next_alarm",next);
   getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});return next;
  }
 }
 public Cursor all(){return getReadableDatabase().rawQuery("SELECT id,title,category,priority,due,done,recurrence FROM tasks ORDER BY done ASC,priority DESC,due ASC",null);}
 public Cursor pending(){return getReadableDatabase().rawQuery("SELECT id,title,priority,due,next_alarm FROM tasks WHERE done=0",null);}
 public long nextAlarm(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT next_alarm FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getLong(0):0;}}
 public void setNextAlarm(long id,long when){ContentValues v=new ContentValues();v.put("next_alarm",when);getWritableDatabase().update("tasks",v,"id=? AND done=0",new String[]{String.valueOf(id)});}
 public boolean claimAlarm(long id,long expected,long replacement){ContentValues v=new ContentValues();v.put("next_alarm",replacement);return getWritableDatabase().update("tasks",v,"id=? AND done=0 AND next_alarm=?",new String[]{String.valueOf(id),String.valueOf(expected)})==1;}
 public boolean isPending(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
 public String title(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT title FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getString(0):"Tâche";}}
 public int priority(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT priority FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getInt(0):1;}}
}
