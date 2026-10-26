package com.blackghost.adaptask;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;
public final class BackupManager {
 private static final String[] TASK={"id","title","category","priority","due","done","recurrence","next_alarm","repeat_interval","repeat_days","space_id","reminder_minutes","task_type","description","created_at","closed_at"};
 private static final String[] SPACE={"id","name"};
 private static final String[] CATEGORY={"id","space_id","name"};
 private static final String[] BUDGET={"id","capital"};
 private static final String[] EXPENSE={"id","title","category","amount","spent_at"};
 private static JSONArray exportTable(SQLiteDatabase db,String table,String[] columns)throws JSONException{
  JSONArray out=new JSONArray();try(Cursor c=db.rawQuery("SELECT "+android.text.TextUtils.join(",",columns)+" FROM "+table,null)){
   while(c.moveToNext()){JSONObject item=new JSONObject();for(int j=0;j<columns.length;j++)item.put(columns[j],c.getType(j)==Cursor.FIELD_TYPE_INTEGER?Long.valueOf(c.getLong(j)):c.getString(j));out.put(item);}
  }return out;
 }
 public static String exportData(Context context,TaskStore store)throws JSONException{
  SQLiteDatabase db=store.getReadableDatabase();JSONObject root=new JSONObject();
  root.put("format","adaptask-backup");root.put("version",2);root.put("spaces",exportTable(db,"spaces",SPACE));root.put("categories",exportTable(db,"categories",CATEGORY));root.put("tasks",exportTable(db,"tasks",TASK));root.put("budget",exportTable(db,"budget_config",BUDGET));root.put("expenses",exportTable(db,"expenses",EXPENSE));
  JSONObject settings=new JSONObject();for(int p=0;p<4;p++)settings.put("interval_"+p,ReminderReceiver.interval(context,p));root.put("settings",settings);
  return root.toString(2);
 }
 private static ContentValues values(JSONObject row,String[] columns)throws JSONException{
  ContentValues v=new ContentValues();for(String col:columns){if(!row.has(col)||row.isNull(col)){if(col.equals("task_type")||col.equals("created_at")||col.equals("closed_at")){v.put(col,0);continue;}if(col.equals("description")){v.put(col,"");continue;}throw new JSONException("Champ manquant : "+col);}
   if(col.equals("title")||col.equals("category")||col.equals("name")||col.equals("description"))v.put(col,row.getString(col));else v.put(col,row.getLong(col));}
  return v;
 }
 public static void restoreData(Context context,TaskStore store,String json)throws JSONException{
  JSONObject root=new JSONObject(json);if(!"adaptask-backup".equals(root.optString("format"))||(root.optInt("version")!=1&&root.optInt("version")!=2))throw new JSONException("Format de sauvegarde non reconnu");
  boolean hasBudget=root.optInt("version")>=2;
  JSONArray spaces=root.getJSONArray("spaces"),categories=root.getJSONArray("categories"),tasks=root.getJSONArray("tasks");
  if(spaces.length()>10000||categories.length()>100000||tasks.length()>200000)throw new JSONException("Sauvegarde trop volumineuse");
  ArrayList<ContentValues> ss=new ArrayList<>(),cc=new ArrayList<>(),tt=new ArrayList<>();
  HashSet<Long> spaceIds=new HashSet<>(),taskIds=new HashSet<>();boolean hasGeneral=false;
  for(int j=0;j<spaces.length();j++){ContentValues v=values(spaces.getJSONObject(j),SPACE);long id=v.getAsLong("id");if(id<=0||!spaceIds.add(id)||v.getAsString("name").trim().isEmpty())throw new JSONException("Espace invalide");if(id==1)hasGeneral=true;ss.add(v);}
  if(!hasGeneral)throw new JSONException("Espace Général manquant");
  for(int j=0;j<categories.length();j++){ContentValues v=values(categories.getJSONObject(j),CATEGORY);if(v.getAsLong("id")<=0||!spaceIds.contains(v.getAsLong("space_id"))||v.getAsString("name").trim().isEmpty())throw new JSONException("Catégorie invalide");cc.add(v);}
  for(int j=0;j<tasks.length();j++){ContentValues v=values(tasks.getJSONObject(j),TASK);long id=v.getAsLong("id");if(id<=0||!taskIds.add(id)||!spaceIds.contains(v.getAsLong("space_id"))||v.getAsString("title").trim().isEmpty()||v.getAsInteger("priority")<0||v.getAsInteger("priority")>3||v.getAsInteger("recurrence")<0||v.getAsInteger("recurrence")>5||v.getAsInteger("task_type")<0||v.getAsInteger("task_type")>1||v.getAsLong("due")<=0||v.getAsInteger("reminder_minutes")<0||v.getAsInteger("reminder_minutes")>10080)throw new JSONException("Tâche invalide");tt.add(v);}
  ArrayList<ContentValues> budgets=new ArrayList<>(),expenses=new ArrayList<>();
  if(hasBudget){
   JSONArray ba=root.getJSONArray("budget"),ex=root.getJSONArray("expenses");
   if(ba.length()!=1||ex.length()>200000)throw new JSONException("Données de budget invalides ou trop volumineuses");
   ContentValues v=values(ba.getJSONObject(0),BUDGET);
   if(v.getAsLong("id")!=1||v.getAsLong("capital")<0||v.getAsLong("capital")>BudgetStore.MAX_AMOUNT)throw new JSONException("Capital invalide");
   budgets.add(v);
   HashSet<Long> expenseIds=new HashSet<>();
   for(int j=0;j<ex.length();j++){
    ContentValues row=values(ex.getJSONObject(j),EXPENSE);
    long id=row.getAsLong("id");
    try{BudgetStore.validate(row.getAsString("title"),row.getAsString("category"),row.getAsLong("amount"),row.getAsLong("spent_at"));}
    catch(IllegalArgumentException err){throw new JSONException("Dépense invalide : "+err.getMessage());}
    if(id<=0||!expenseIds.add(id))throw new JSONException("Identifiant de dépense invalide");
    expenses.add(row);
   }
  }
  JSONObject settings=root.getJSONObject("settings");int[] intervals=new int[4];for(int p=0;p<4;p++){intervals[p]=settings.getInt("interval_"+p);if(intervals[p]<1||intervals[p]>10080)throw new JSONException("Paramètre de rappel invalide");}
  SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
  try{
   if(hasBudget){db.delete("expenses",null,null);db.delete("budget_config",null,null);}
   db.delete("tasks",null,null);db.delete("categories",null,null);db.delete("spaces",null,null);
   for(ContentValues v:ss)if(db.insertOrThrow("spaces",null,v)<0)throw new JSONException("Espace non restauré");
   for(ContentValues v:cc)if(db.insertOrThrow("categories",null,v)<0)throw new JSONException("Catégorie non restaurée");
   for(ContentValues v:tt)if(db.insertOrThrow("tasks",null,v)<0)throw new JSONException("Tâche non restaurée");
   if(hasBudget){for(ContentValues v:budgets)db.insertOrThrow("budget_config",null,v);for(ContentValues v:expenses)db.insertOrThrow("expenses",null,v);}
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
  android.content.SharedPreferences.Editor e=context.getSharedPreferences("adaptask_settings",Context.MODE_PRIVATE).edit();for(int p=0;p<4;p++)e.putInt("interval_"+p,intervals[p]);e.apply();
 }
 private BackupManager(){}
}
