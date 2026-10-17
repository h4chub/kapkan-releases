import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.util.concurrent.atomic.AtomicInteger;
import gg.kapkan.launcher.install.BaseGameInstallerService;
import gg.kapkan.launcher.launch.*;
public class ReproduceClosed {
 public static void main(String[] args)throws Exception {
  HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  String url="http://127.0.0.1:"+s.getAddress().getPort(); AtomicInteger requests=new AtomicInteger();
  var raw=new java.net.ServerSocket(0,50,java.net.InetAddress.getByName("127.0.0.1"));
  Thread.ofVirtual().start(()->{try{while(!raw.isClosed()){try(var socket=raw.accept()){requests.incrementAndGet();var in=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));String line;while((line=in.readLine())!=null&&!line.isEmpty()){} var out=socket.getOutputStream();out.write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\nConnection: close\r\n\r\npartial".getBytes());out.flush();socket.shutdownOutput();}}}catch(Exception ignored){}});
  s.createContext("/manifest",x->{byte[] b=("{\"versions\":[{\"id\":\"26.1.2\",\"url\":\""+url+"/version\"}]}").getBytes(); x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});
  s.createContext("/version",x->{byte[] b=("{\"id\":\"26.1.2\",\"type\":\"release\",\"mainClass\":\"fixture.NoGame\",\"downloads\":{\"client\":{\"url\":\""+"http://127.0.0.1:"+raw.getLocalPort()+"/client\",\"size\":10000,\"sha1\":\"0000000000000000000000000000000000000000\"}},\"libraries\":[]}").getBytes();x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});
  s.createContext("/client",x->{requests.incrementAndGet();x.sendResponseHeaders(200,10000);x.getResponseBody().write(new byte[256]);try{x.getResponseBody().close();}catch(Exception ignored){}x.close();});s.start();
  try {
   var ctor=BaseGameInstallerService.class.getDeclaredConstructor(HttpClient.class,URI.class);ctor.setAccessible(true);
   var installer=ctor.newInstance(HttpClient.newHttpClient(),URI.create(url+"/manifest"));
   try{installer.ensureInstalled(new MinecraftInstallation(Files.createTempDirectory("kapkan-repro-")),"26.1.2",System.out::println);throw new AssertionError("must fail");}
   catch(LaunchException e){System.out.println("OBSERVED_MESSAGE="+e.getMessage());System.out.println("PRESERVED_CAUSE="+e.getCause());System.out.println("CLIENT_REQUEST_COUNT="+requests); if(!e.getMessage().contains("closed"))throw new AssertionError("different error");}
  }finally{s.stop(0);raw.close();}
 }
}
