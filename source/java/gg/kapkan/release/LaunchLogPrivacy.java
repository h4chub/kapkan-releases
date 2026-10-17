package gg.kapkan.release;

import java.util.*;
import java.util.function.Consumer;

/** Redact the logged copy only. Authentication still receives the original arguments. */
public final class LaunchLogPrivacy {
 private LaunchLogPrivacy() {}
 private static boolean sensitive(String flag) {
  String k=flag.toLowerCase(Locale.ROOT).replace("-","").replace("_","");
  return k.equals("accesstoken")||k.equals("refreshtoken")||k.equals("clienttoken")||k.equals("idtoken")
   ||k.equals("password")||k.equals("session")||k.equals("userproperties")||k.equals("proxypass")||k.equals("proxypassword");
 }
 public static String command(List<String> args) {
  List<String> clean=new ArrayList<>();boolean hide=false;
  for(String s:args) {
   if(hide){clean.add("[REDACTED]");hide=false;continue;}
   int equal=s.indexOf('=');
   if(equal>=0&&sensitive(s.substring(0,equal))){clean.add(s.substring(0,equal+1)+"[REDACTED]");continue;}
   clean.add(s);if(sensitive(s))hide=true;
  }
  return String.join(" ",clean);
 }
 public static Consumer<String> logger(Consumer<String> sink) {
  return sink==null?s->{}:sink;
 }
}
