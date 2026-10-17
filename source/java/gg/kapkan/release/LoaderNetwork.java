package gg.kapkan.release;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.jar.*;
import java.util.zip.CRC32;

/** The loader transports use the same bounded retry code as the base-game downloader. */
public final class LoaderNetwork {
 private LoaderNetwork() {}
 public static String text(HttpClient http,String url)throws IOException,InterruptedException {
  try{return RetryDownloader.text(http,URI.create(url));}
  catch(IllegalArgumentException e){throw new IOException("Invalid loader metadata address",e);}
 }
 public static boolean jarUsable(Path p,LinkOption... ignored) {
  if(!p.getFileName().toString().endsWith(".jar"))return Files.isRegularFile(p,ignored);
  try{validateJar(p,false,"");return true;}catch(IOException|RuntimeException e){return false;}
 }
 public static void jar(HttpClient http,String url,Path dest)throws IOException,InterruptedException {
  String required=dest.getFileName().toString().startsWith("fabric-loader-")?"net/fabricmc/loader/impl/launch/knot/KnotClient.class":"";
  RetryDownloader.downloadValidated(http,URI.create(url),dest,null,-1,p->validateJar(p,false,required));
 }
 public static void installer(HttpClient http,String url,Path dest,Consumer<String> log,String label)throws IOException,InterruptedException {
  LaunchLogPrivacy.logger(log).accept(label+": checking installer download");
  RetryDownloader.downloadValidated(http,URI.create(url),dest,null,-1,p->validateJar(p,true,""));
 }
 public static void validateJar(Path path,boolean executable,String required)throws IOException {
  if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("JAR is missing or not a regular file");
  long expanded=0;
  try(JarFile jar=new JarFile(path.toFile(),false)) {
   if(jar.size()==0)throw new IOException("JAR contains no entries");
   if(!required.isBlank()&&jar.getJarEntry(required)==null)throw new IOException("Loader entry point is missing: "+required);
   if(executable&&(jar.getManifest()==null||jar.getManifest().getMainAttributes().getValue("Main-Class")==null))throw new IOException("Installer JAR contains no Main-Class");
   var entries=jar.entries();byte[] buf=new byte[65536];
   while(entries.hasMoreElements()) {
    JarEntry entry=entries.nextElement();if(entry.isDirectory())continue;CRC32 crc=new CRC32();long count=0;
    try(InputStream in=jar.getInputStream(entry)){int n;while((n=in.read(buf))!=-1){expanded+=n;count+=n;if(expanded>512L*1024*1024)throw new IOException("JAR expands past the size limit");crc.update(buf,0,n);}}
    if(entry.getSize()!=count||(entry.getCrc()!=-1&&crc.getValue()!=entry.getCrc()))throw new IOException("JAR entry failed integrity check: "+entry.getName());
   }
  }
 }
}
