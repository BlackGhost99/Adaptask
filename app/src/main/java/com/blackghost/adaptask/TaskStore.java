package com.blackghost.adaptask;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
public class TaskStore extends SQLiteOpenHelper {
 public TaskStore(Context c){super(c,"adaptask.db",null,1);}
 public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,category TEXT NOT NULL,priority INTEGER NOT NULL,due INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0)");}
 public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
 public long add(String title,String category,int priority,long due){ContentValues v=new ContentValues();v.put("title",title);v.put("category",category);v.put("priority",priority);v.put("due",due);return getWritableDatabase().insert("tasks",null,v);}
 public void done(long id){ContentValues v=new ContentValues();v.put("done",1);getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});}
 public Cursor all(){return getReadableDatabase().rawQuery("SELECT id,title,category,priority,due,done FROM tasks ORDER BY done ASC,priority DESC,due ASC",null);}
 public Cursor pending(){return getReadableDatabase().rawQuery("SELECT id,title,priority,due FROM tasks WHERE done=0",null);}
 public boolean isPending(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM tasks WHERE id=? AND done=0",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
 public String title(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT title FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getString(0):"Tâche";}}
 public int priority(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT priority FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getInt(0):1;}}
}
