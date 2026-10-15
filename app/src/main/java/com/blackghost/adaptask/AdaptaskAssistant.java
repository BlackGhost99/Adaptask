package com.blackghost.adaptask;
import java.text.Normalizer;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small offline, bilingual command parser. No network, account or external API. */
public final class AdaptaskAssistant {
 public static final class Draft {
  public String title="",description="",category="";
  public int type=0,priority=1,recurrence=0,reminderMinutes=0;
  public long due=System.currentTimeMillis()+3600000L;
  public String language="fr";
 }
 private static String normalized(String text){
  return Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}+","");
 }
 private static boolean has(String s,String regex){return Pattern.compile(regex).matcher(s).find();}
 private static int number(String s,String regex){Matcher m=Pattern.compile(regex).matcher(s);return m.find()?Integer.parseInt(m.group(1)):-1;}
 public static Draft parse(String spoken,String lang){
  Draft d=new Draft();d.language="en".equals(lang)?"en":"fr";
  if(spoken==null)return d;
  String s=normalized(spoken).trim();
  d.type=has(s,"\\b(incident|panne|failure|breakdown|outage|problem|probleme)\\b")?1:0;
  if(has(s,"\\b(critique|critical)\\b"))d.priority=3;
  else if(has(s,"\\b(urgent|urgente|high priority)\\b"))d.priority=2;
  else if(has(s,"\\b(faible|low priority)\\b"))d.priority=0;
  int mins=number(s,"(?:dans|in)\\s+(\\d+)\\s*(?:minutes?|mins?)\\b");
  int hours=number(s,"(?:dans|in)\\s+(\\d+)\\s*(?:heures?|hours?|hrs?)\\b");
  int days=number(s,"(?:dans|in)\\s+(\\d+)\\s*(?:jours?|days?)\\b");
  long now=System.currentTimeMillis();
  if(mins>0)d.due=now+mins*60000L;
  else if(hours>0)d.due=now+hours*3600000L;
  else if(days>0)d.due=now+days*86400000L;
  else if(has(s,"\\b(demain|tomorrow)\\b"))d.due=now+86400000L;
  int reminder=number(s,"(?:rappelle.moi|rappel|remind me|reminder)\\s+(?:dans|in)\\s+(\\d+)\\s*(?:minutes?|mins?)\\b");
  if(reminder>0){d.due=now+reminder*60000L;d.reminderMinutes=1;}
  if(has(s,"\\b(tous les jours|chaque jour|every day|daily)\\b"))d.recurrence=1;
  else if(has(s,"\\b(chaque semaine|every week|weekly)\\b"))d.recurrence=2;
  else if(has(s,"\\b(chaque mois|every month|monthly)\\b"))d.recurrence=3;
  if(d.type==1)d.recurrence=0;
  Matcher cat=Pattern.compile("(?:categorie|category)\\s*[:：]?\\s*([\\p{L}0-9_-]+)").matcher(s);
  if(cat.find())d.category=cat.group(1);
  String t=spoken.trim();
  t=t.replaceFirst("(?iu)^\\s*(?:cree|creer|ajoute|ajouter|note|create|add|log|record)\\s+(?:une?|an?|the)?\\s*(?:tache|task|incident)?\\s*[:,-]?\\s*","");
  t=t.replaceAll("(?iu)\\b(?:priorite|priority)\\s*(?:critique|critical|urgente?|high|faible|low|normale?|normal)\\b","");
  t=t.replaceAll("(?iu)\\b(?:categorie|category)\\s*[:：]?\\s*[\\p{L}0-9_-]+","");
  t=t.replaceAll("(?iu)\\b(?:rappelle.moi|rappel|remind me|reminder)\\s+(?:dans|in)\\s+\\d+\\s*(?:minutes?|mins?)\\b","");
  t=t.replaceAll("(?iu)\\b(?:dans|in)\\s+\\d+\\s*(?:minutes?|mins?|heures?|hours?|hrs?|jours?|days?)\\b","");
  t=t.replaceAll("(?iu)\\b(?:demain|tomorrow|tous les jours|chaque jour|every day|daily|chaque semaine|every week|weekly|chaque mois|every month|monthly)\\b","");
  t=t.replaceAll("(?iu)\\b(?:incident|tache|task)\\s*[:,-]?\\s*","").replaceAll("\\s+"," ").trim();
  d.title=t.isEmpty()?spoken.trim():t;
  d.description=spoken.trim();
  return d;
 }
 public static String suggestion(String input,String lang){
  boolean en="en".equals(lang);String t=normalized(input==null?"":input);
  if(t.trim().isEmpty())return en?"Try: Create an incident, check the network...":"Essayez : Crée un incident, vérifier le réseau...";
  if(!has(t,"\\b(urgent|urgente|critique|critical|normal|normale|faible|priority|priorite)\\b"))
   return en?"Suggestion: add 'urgent priority' or 'normal priority'":"Suggestion : ajoutez « priorité urgente » ou « priorité normale »";
  if(!has(t,"\\b(dans|in|demain|tomorrow|aujourd|today|minutes?|heures?|hours?|jours?|days?)\\b"))
   return en?"Suggestion: add 'in 30 minutes' or 'tomorrow'":"Suggestion : ajoutez « dans 30 minutes » ou « demain »";
  if(!has(t,"\\b(categorie|category)\\b"))
   return en?"Suggestion: add 'category maintenance'":"Suggestion : ajoutez « catégorie maintenance »";
  return en?"Ready for review. You can add more details.":"Prêt à vérifier. Vous pouvez ajouter des détails.";
 }
 private AdaptaskAssistant(){}
}
