package com.blackghost.adaptask;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
public class TaskStore extends SQLiteOpenHelper {
 public TaskStore(Context c){super(c,"adaptask.db",null,7);}
 public void onCreate(SQLiteDatabase db){createSpaces(db);db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,category TEXT NOT NULL,priority INTEGER NOT NULL,due INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,recurrence INTEGER NOT NULL DEFAULT 0,next_alarm INTEGER NOT NULL DEFAULT 0,repeat_interval INTEGER NOT NULL DEFAULT 1,repeat_days INTEGER NOT NULL DEFAULT 0,space_id INTEGER NOT NULL DEFAULT 1,reminder_minutes INTEGER NOT NULL DEFAULT 0,task_type INTEGER NOT NULL DEFAULT 0,description TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0,closed_at INTEGER NOT NULL DEFAULT 0)");}
 public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion<2)db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence INTEGER NOT NULL DEFAULT 0");if(oldVersion<3){db.execSQL("ALTER TABLE tasks ADD COLUMN next_alarm INTEGER NOT NULL DEFAULT 0");db.execSQL("UPDATE tasks SET next_alarm=due WHERE done=0");}if(oldVersion<4){db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_interval INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_days INTEGER NOT NULL DEFAULT 0");}if(oldVersion<5){createSpaces(db);db.execSQL("ALTER TABLE tasks ADD COLUMN space_id INTEGER NOT NULL DEFAULT 1");db.execSQL("INSERT OR IGNORE INTO categories(space_id,name) SELECT 1,TRIM(category) FROM tasks WHERE TRIM(category)<>'' GROUP BY LOWER(TRIM(category))");}if(oldVersion<6)db.execSQL("ALTER TABLE tasks ADD COLUMN reminder_minutes INTEGER NOT NULL DEFAULT 0");if(oldVersion<7){db.execSQL("ALTER TABLE tasks ADD COLUMN task_type INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE tasks ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE tasks ADD COLUMN closed_at INTEGER NOT NULL DEFAULT 0");db.execSQL("UPDATE tasks SET created_at=due WHERE created_at=0");}}
 static void createSpaces(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS spaces(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE COLLATE NOCASE)");
  db.execSQL("INSERT OR IGNORE INTO spaces(id,name) VALUES(1,'Général')");
  db.execSQL("CREATE TABLE IF NOT EXISTS categories(id INTEGER PRIMARY KEY AUTOINCREMENT,space_id INTEGER NOT NULL,name TEXT NOT NULL COLLATE NOCASE,UNIQUE(space_id,name))");
 }
 public Cursor spaces(){return getReadableDatabase().rawQuery("SELECT id,name FROM spaces ORDER BY id",null);}
 public Cursor categories(long space){return getReadableDatabase().rawQuery("SELECT id,name FROM categories WHERE space_id=? ORDER BY name COLLATE NOCASE",new String[]{String.valueOf(space)});}
 public long addSpace(String name){ContentValues v=new ContentValues();v.put("name",name.trim());return getWritableDatabase().insert("spaces",null,v);}
 public boolean renameSpace(long id,String name){if(id==1)return false;ContentValues v=new ContentValues();v.put("name",name.trim());return getWritableDatabase().update("spaces",v,"id=?",new String[]{String.valueOf(id)})>0;}
 public long addCategory(long space,String name){ContentValues v=new ContentValues();v.put("space_id",space);v.put("name",name.trim());return getWritableDatabase().insert("categories",null,v);}
 public void ensureCategory(long space,String name){if(name==null||name.trim().isEmpty())return;getWritableDatabase().execSQL("INSERT OR IGNORE INTO categories(space_id,name) VALUES(?,?)",new Object[]{space,name.trim()});}
 public void renameCategory(long id,long space,String oldName,String newName){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
  ContentValues v=new ContentValues();v.put("name",newName.trim());db.update("categories",v,"id=? AND space_id=?",new String[]{String.valueOf(id),String.valueOf(space)});
  ContentValues t=new ContentValues();t.put("category",newName.trim());db.update("tasks",t,"space_id=? AND category=? COLLATE NOCASE",new String[]{String.valueOf(space),oldName});db.setTransactionSuccessful();
 }finally{db.endTransaction();}}
 public void moveTask(long id,long space){ContentValues v=new ContentValues();v.put("space_id",space);getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});try(Cursor c=getReadableDatabase().rawQuery("SELECT category FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){if(c.moveToFirst())ensureCategory(space,c.getString(0));}}
 public long add(String title,String category,int priority,long due,int recurrence,int interval,int days,long spaceId,int reminderMinutes,int taskType,String description){ensureCategory(spaceId,category);ContentValues v=new ContentValues();v.put("space_id",spaceId);v.put("reminder_minutes",Math.max(0,reminderMinutes));v.put("task_type",taskType);v.put("description",description);v.put("title",title);v.put("category",category);v.put("priority",priority);v.put("due",due);v.put("recurrence",recurrence);v.put("repeat_interval",Math.max(1,interval));v.put("repeat_days",recurrence==3?dayOfMonth(due):days);v.put("created_at",System.currentTimeMillis());v.put("next_alarm",due);return getWritableDatabase().insert("tasks",null,v);}
 public void update(long id,String title,String category,int priority,long due,int recurrence,int interval,int days,long spaceId,int reminderMinutes,int taskType,String description){
  ensureCategory(spaceId,category);ContentValues v=new ContentValues();v.put("space_id",spaceId);v.put("reminder_minutes",Math.max(0,reminderMinutes));v.put("task_type",taskType);v.put("description",description);v.put("title",title);v.put("category",category);v.put("priority",priority);v.put("due",due);v.put("recurrence",recurrence);v.put("repeat_interval",Math.max(1,interval));v.put("repeat_days",days);v.put("next_alarm",due);
  getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});
 }
 public void delete(long id){getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)});}
 public long complete(long id){
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
  try(Cursor c=db.rawQuery("SELECT due,recurrence,repeat_interval,repeat_days FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){
   if(!c.moveToFirst()){db.setTransactionSuccessful();return -1;}
   long due=c.getLong(0);int recurrence=c.getInt(1),interval=Math.max(1,c.getInt(2)),days=c.getInt(3);
   ContentValues v=new ContentValues();
   if(recurrence==0){v.put("done",1);v.put("closed_at",System.currentTimeMillis());v.put("next_alarm",0);db.update("tasks",v,"id=? AND done=0",new String[]{String.valueOf(id)});db.setTransactionSuccessful();return -1;}
   long next=nextDue(due,recurrence,interval,days,System.currentTimeMillis());
   v.put("due",next);v.put("done",0);v.put("closed_at",0);v.put("next_alarm",next);
   db.update("tasks",v,"id=? AND done=0",new String[]{String.valueOf(id)});db.setTransactionSuccessful();return next;
  }finally{db.endTransaction();}
 }
 static int dayOfMonth(long millis){Calendar c=Calendar.getInstance();c.setTimeInMillis(millis);return c.get(Calendar.DAY_OF_MONTH);}
 public static long nextDue(long due,int recurrence,int interval,int days,long now){
  Calendar cal=Calendar.getInstance();cal.setTimeInMillis(due);interval=Math.max(1,interval);
  if(recurrence==3){int day=days>=1&&days<=31?days:cal.get(Calendar.DAY_OF_MONTH);int hour=cal.get(Calendar.HOUR_OF_DAY),minute=cal.get(Calendar.MINUTE),second=cal.get(Calendar.SECOND);
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
 public Cursor all(){return getReadableDatabase().rawQuery("SELECT id,title,category,priority,due,done,recurrence,repeat_interval,repeat_days,space_id,reminder_minutes,task_type,description,created_at,closed_at FROM tasks ORDER BY done ASC,priority DESC,due ASC",null);}
 public Cursor reportRows(long space){return getReadableDatabase().rawQuery("SELECT id,title,category,priority,due,done,task_type,description,created_at,closed_at FROM tasks WHERE space_id=? ORDER BY task_type DESC,due DESC",new String[]{String.valueOf(space)});}
 public Cursor pending(){return getReadableDatabase().rawQuery("SELECT id,title,priority,due,next_alarm FROM tasks WHERE done=0",null);}
 public long nextAlarm(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT next_alarm FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getLong(0):0;}}
 public void setNextAlarm(long id,long when){ContentValues v=new ContentValues();v.put("next_alarm",when);getWritableDatabase().update("tasks",v,"id=? AND done=0",new String[]{String.valueOf(id)});}
 public boolean claimAlarm(long id,long expected,long replacement){ContentValues v=new ContentValues();v.put("next_alarm",replacement);return getWritableDatabase().update("tasks",v,"id=? AND done=0 AND next_alarm=?",new String[]{String.valueOf(id),String.valueOf(expected)})==1;}
 public boolean isPending(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
 public String title(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT title FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getString(0):"Tâche";}}
 public int reminderMinutes(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT reminder_minutes FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getInt(0):0;}}
 public int priority(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT priority FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getInt(0):1;}}
}
