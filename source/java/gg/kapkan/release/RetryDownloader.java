package gg.kapkan.release;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLException;

/**
 * Managed game and loader transport. Keeps Mojang's descriptors, hashes, installation paths,
 * library selection, and asset selection in the existing installer.
 * Never changes TLS trust, account authentication, or user game settings.
 */
public final class RetryDownloader {
    private RetryDownloader() {}
    static final long MAX_FILE = 2L * 1024 * 1024 * 1024;
    static final long MAX_TEXT = 32L * 1024 * 1024;
    static final Policy DEFAULT = new Policy(3, 700, 45_000, 60_000, 600_000);
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "KapKan-download-timeout"); t.setDaemon(true); return t;
    });
    record Policy(int attempts, long backoffMillis, long headerMillis, long idleMillis, long totalMillis) {
        Policy { if (attempts < 1 || attempts > 5 || backoffMillis < 0 || headerMillis < 1 || idleMillis < 1 || totalMillis < 1) throw new IllegalArgumentException("Invalid retry policy"); }
    }
    private enum Kind { CONNECTION, HTTP, INTEGRITY, LOCAL_FILE, TLS, METADATA }
    private static final class Problem extends IOException {
        final Kind kind; final boolean retry; final long retryAfter;
        Problem(Kind kind, String message, boolean retry, long wait, Throwable cause) {
            super(message, cause); this.kind=kind; this.retry=retry; this.retryAfter=wait;
        }
    }
    public static final class DownloadFailure extends IOException {
        DownloadFailure(String message, Throwable cause) { super(message, cause); }
    }

    public static void download(HttpClient client, URI uri, Path destination, String sha1, long expectedSize) throws IOException, InterruptedException {
        download(client, uri, destination, sha1, expectedSize, DEFAULT);
    }
    @FunctionalInterface public interface ContentValidator { void validate(Path path)throws IOException; }
    public static void downloadValidated(HttpClient client,URI uri,Path dest,String sha1,long size,ContentValidator validator)throws IOException,InterruptedException {
        download(client,uri,dest,sha1,size,DEFAULT,validator);
    }
    static void download(HttpClient client, URI uri, Path destination, String sha1, long expectedSize, Policy policy) throws IOException, InterruptedException {
        download(client,uri,destination,sha1,expectedSize,policy,p->{});
    }
    static void download(HttpClient client, URI uri, Path destination, String sha1, long expectedSize, Policy policy,ContentValidator validator) throws IOException, InterruptedException {
        validateUri(uri);
        Objects.requireNonNull(client); Objects.requireNonNull(destination);
        if (expectedSize < -1 || expectedSize > MAX_FILE) throw new IOException("Invalid download size in metadata");
        if (sha1 != null && !sha1.isBlank() && !sha1.matches("(?i)[0-9a-f]{40}")) throw new IOException("Invalid SHA-1 in download metadata");
        Path dest=destination.toAbsolutePath().normalize();
        LaunchProgress.file(dest.getFileName().toString());
        try {
            if (Files.isSymbolicLink(dest)) throw new IOException("Destination is a symbolic link");
            if (matches(dest,sha1,expectedSize)) {
                try {validator.validate(dest);return;} catch(IOException corrupt) { /* Redownload to staging; keep old file until verified. */ }
            }
            if (Files.exists(dest,LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(dest,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Destination is not a regular file");
            Files.createDirectories(dest.getParent());
        } catch (IOException e) { throw failed(uri,dest,0,new Problem(Kind.LOCAL_FILE,"Cannot prepare the destination: "+detail(e),false,0,e)); }
        boolean fallback=false;
        for (int attempt=1; attempt<=policy.attempts(); attempt++) {
            interrupted();
            Path partial;
            try { partial=Files.createTempFile(dest.getParent(),".kapkan-download-",".part"); }
            catch (IOException e) { throw failed(uri,dest,attempt,new Problem(Kind.LOCAL_FILE,"Cannot create the temporary download file: "+detail(e),false,0,e)); }
            try {
                try (OutputStream out=Files.newOutputStream(partial,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                    transfer(client,uri,out,expectedSize<0?MAX_FILE:expectedSize,expectedSize,fallback,policy);
                } catch (Problem e) { throw e; }
                  catch (IOException e) { throw new Problem(Kind.LOCAL_FILE,"Cannot write the temporary download: "+detail(e),false,0,e); }
                if (!matches(partial,sha1,expectedSize)) throw new Problem(Kind.INTEGRITY,"The downloaded file did not match the expected size or SHA-1",true,0,null);
                try {validator.validate(partial);} catch(IOException e) {throw new Problem(Kind.INTEGRITY,"Downloaded archive is invalid: "+detail(e),true,0,e);}
                try {
                    try { Files.move(partial,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException e) { Files.move(partial,dest,StandardCopyOption.REPLACE_EXISTING); }
                } catch(IOException e) { throw new Problem(Kind.LOCAL_FILE,"Cannot save the verified file: "+detail(e),false,0,e); }
                if (attempt>1) log("Recovered "+safe(uri)+" after "+attempt+" attempts; size and hash verified.",null);
                return;
            } catch (Problem e) {
                log("Attempt "+attempt+"/"+policy.attempts()+" "+safe(uri)+" -> "+dest.getFileName()+" ["+e.kind+"] "+e.getMessage(),e);
                if (!e.retry || attempt==policy.attempts()) throw failed(uri,dest,attempt,e);
                if (e.kind==Kind.CONNECTION) fallback=true;
                pause(policy,attempt,e.retryAfter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); log("Download cancelled: "+safe(uri),null); throw e;
            } finally {
                try { Files.deleteIfExists(partial); }
                catch(IOException e) { log("Could not remove temporary file "+partial.getFileName(),e); }
            }
        }
        throw new AssertionError("Unreachable retry state");
    }

    public static String text(HttpClient client, URI uri) throws IOException, InterruptedException { return text(client,uri,DEFAULT); }
    static String text(HttpClient client,URI uri,Policy policy)throws IOException,InterruptedException {
        validateUri(uri); boolean fallback=false;
        for(int attempt=1;attempt<=policy.attempts();attempt++) {
            interrupted();
            try {
                var out=new ByteArrayOutputStream();
                transfer(client,uri,out,MAX_TEXT,-1,fallback,policy);
                if(attempt>1)log("Recovered metadata "+safe(uri)+" after "+attempt+" attempts.",null);
                return out.toString(StandardCharsets.UTF_8);
            } catch(Problem e) {
                log("Metadata attempt "+attempt+"/"+policy.attempts()+" "+safe(uri)+" ["+e.kind+"] "+e.getMessage(),e);
                if(!e.retry || attempt==policy.attempts())throw failed(uri,null,attempt,e);
                if(e.kind==Kind.CONNECTION)fallback=true;
                pause(policy,attempt,e.retryAfter);
            } catch(InterruptedException e) {Thread.currentThread().interrupt();throw e;}
        }
        throw new AssertionError("Unreachable retry state");
    }

    private static void transfer(HttpClient client,URI uri,OutputStream out,long limit,long expected,boolean http1,Policy policy)throws Problem,InterruptedException {
        LaunchProgress.attempt();
        HttpRequest.Builder b=HttpRequest.newBuilder(uri).GET().timeout(Duration.ofMillis(policy.headerMillis()))
            .header("User-Agent","KapKan/1.0.6").header("Accept-Encoding","identity");
        if(http1)b.version(HttpClient.Version.HTTP_1_1);
        HttpResponse<InputStream> response;
        try { response=client.send(b.build(),HttpResponse.BodyHandlers.ofInputStream()); }
        catch(IOException e) { throw network(e); }
        InputStream body=response.body();
        try {
            try { validateUri(response.uri()); }
            catch(IOException e) { throw new Problem(Kind.METADATA,"The download redirected to an unsupported address",false,0,e); }
            int code=response.statusCode();
            if(code!=200)throw new Problem(Kind.HTTP,"HTTP "+code+" from "+response.uri().getHost(),Set.of(408,425,429,500,502,503,504).contains(code),retryAfter(response),null);
            if("https".equalsIgnoreCase(uri.getScheme())&&!"https".equalsIgnoreCase(response.uri().getScheme()))throw new Problem(Kind.TLS,"Refused HTTPS-to-HTTP redirect",false,0,null);
            OptionalLong length=response.headers().firstValueAsLong("Content-Length");
            if(length.isPresent() && (length.getAsLong()>limit || (expected>=0 && length.getAsLong()!=expected)))throw new Problem(Kind.INTEGRITY,"The response size differs from the expected download",true,0,null);
            long count=0;
            try(Guard guard=new Guard(body,policy,Thread.currentThread())) {
                byte[] buffer=new byte[65536];
                while(true) {
                    interrupted(); int n;
                    try {n=body.read(buffer);}
                    catch(IOException e) {
                        if(Thread.currentThread().isInterrupted()||guard.cancelled)throw new InterruptedException("Download cancelled");
                        if(guard.timedOut)throw new Problem(Kind.CONNECTION,"Download stopped receiving data or exceeded its time limit",true,0,e);
                        throw network(e);
                    }
                    if(n<0)break;
                    if(n==0)continue;
                    guard.progress(); count+=n;
                    if(count>limit)throw new Problem(Kind.INTEGRITY,"Response exceeded the expected download size",true,0,null);
                    try{out.write(buffer,0,n);}catch(IOException e){throw new Problem(Kind.LOCAL_FILE,"Cannot write to disk: "+detail(e),false,0,e);}
                    LaunchProgress.bytes(count,expected>=0?expected:length.orElse(-1));
                }
                if(guard.cancelled)throw new InterruptedException("Download cancelled");
                if(guard.timedOut)throw new Problem(Kind.CONNECTION,"Download timed out",true,0,null);
            }
            if((expected>=0&&count!=expected)||(length.isPresent()&&count!=length.getAsLong()))throw new Problem(Kind.CONNECTION,"Connection ended before the whole file arrived (received "+count+" bytes)",true,0,null);
        } finally { try{body.close();}catch(IOException ignored){} }
    }
    private static final class Guard implements AutoCloseable {
        final long started=System.nanoTime(); final AtomicLong last=new AtomicLong(started);
        volatile boolean timedOut, cancelled; final ScheduledFuture<?> timer;
        Guard(InputStream stream,Policy p,Thread worker) {
            long period=Math.max(25,Math.min(1000,p.idleMillis()/4));
            timer=WATCHDOG.scheduleAtFixedRate(()->{
                long now=System.nanoTime();
                if(worker.isInterrupted()){cancelled=true;}
                else if(now-last.get()>TimeUnit.MILLISECONDS.toNanos(p.idleMillis())||now-started>TimeUnit.MILLISECONDS.toNanos(p.totalMillis()))timedOut=true;
                if(cancelled||timedOut){try{stream.close();}catch(IOException ignored){}}
            },period,period,TimeUnit.MILLISECONDS);
        }
        void progress(){last.set(System.nanoTime());}
        public void close(){timer.cancel(false);}
    }
    private static Problem network(IOException e) {
        if((hasCause(e,javax.net.ssl.SSLHandshakeException.class)||hasCause(e,javax.net.ssl.SSLPeerUnverifiedException.class)||hasCause(e,java.security.cert.CertificateException.class)))return new Problem(Kind.TLS,"TLS connection could not be verified or established: "+detail(e),false,0,e);
        String message=hasCause(e,UnknownHostException.class)||hasCause(e,java.nio.channels.UnresolvedAddressException.class)?"Cannot resolve the download server":
            hasCause(e,HttpTimeoutException.class)||hasCause(e,SocketTimeoutException.class)?"Connection timed out":
            "Download connection interrupted";
        return new Problem(Kind.CONNECTION,message+": "+detail(e),true,0,e);
    }
    private static boolean hasCause(Throwable e,Class<?> kind){for(int n=0;e!=null&&n<12;n++,e=e.getCause())if(kind.isInstance(e))return true;return false;}
    private static void interrupted()throws InterruptedException {if(Thread.currentThread().isInterrupted())throw new InterruptedException("Download cancelled");}
    private static void pause(Policy p,int attempt,long retryAfter)throws InterruptedException {
        long exponential=p.backoffMillis()*(1L<<Math.min(attempt-1,4));
        long delay=Math.min(10_000,Math.max(exponential,retryAfter));
        try{Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
    }
    private static long retryAfter(HttpResponse<?> r) {
        String value=r.headers().firstValue("Retry-After").orElse("");
        try{return Math.min(10_000,Math.max(0,Long.parseLong(value))*1000);}catch(Exception ignored){}
        try{return Math.min(10_000,Math.max(0,Duration.between(Instant.now(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis()));}catch(Exception ignored){return 0;}
    }
    private static void validateUri(URI uri)throws IOException {
        if(uri==null||uri.getHost()==null||uri.getUserInfo()!=null)throw new IOException("Invalid download address");
        boolean https="https".equalsIgnoreCase(uri.getScheme());
        boolean loopback="http".equalsIgnoreCase(uri.getScheme())&&Set.of("localhost","127.0.0.1","[::1]","::1").contains(uri.getHost());
        if(!https&&!loopback)throw new IOException("Downloads require HTTPS");
    }
    static boolean matches(Path p,String hash,long size)throws IOException {
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))return false;
        long length=Files.size(p);if(size>=0&&length!=size)return false;
        if(hash==null||hash.isBlank())return length>0||size==0;
        try{MessageDigest d=MessageDigest.getInstance("SHA-1");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[65536];int n;while((n=in.read(b))>=0){if(n>0)d.update(b,0,n);}}return HexFormat.of().formatHex(d.digest()).equalsIgnoreCase(hash);}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static Path logPath(){return Path.of(System.getProperty("user.home"),".kapkan","logs","downloads.log");}
    private static DownloadFailure failed(URI uri,Path dest,int attempts,Problem p) {
        String target=dest==null?"launcher metadata":dest.getFileName().toString();
        String action=switch(p.kind){case LOCAL_FILE->"Check available disk space and folder access, then retry Play.";case HTTP->"The download server rejected the request. Retry later.";case TLS->"Check your system clock and trusted network. TLS verification was not disabled.";case INTEGRITY->"The incomplete or mismatched file was not installed. Retry Play.";case METADATA->"The download address is invalid.";default->"Check the connection, then retry Play. Already verified files are kept.";};
        String text="Could not download "+target+" from "+uri.getHost()+""+(attempts>0?" after "+attempts+" attempt"+(attempts==1?"":"s"):"")+".\n"+p.getMessage()+"\n"+action+"\nDetails: "+logPath();
        log(text,p);return new DownloadFailure(text,p);
    }
    /** Called by the original installer's catch block: preserve the full cause in the local log. */
    public static String installError(Throwable e) {
        if(e instanceof InterruptedException || Thread.currentThread().isInterrupted()){Thread.currentThread().interrupt();return "Download cancelled. Verified files were kept; press Play to retry.";}
        log("Managed installation failed",e);
        if(e instanceof DownloadFailure)return e.getMessage();
        return detail(e)+"\nDetails: "+logPath();
    }
    private static String safe(URI uri){try{return new URI(uri.getScheme(),null,uri.getHost(),uri.getPort(),uri.getPath(),null,null).toString();}catch(Exception e){return "download-server";}}
    private static String detail(Throwable e){StringBuilder b=new StringBuilder();for(int i=0;e!=null&&i<6;i++,e=e.getCause()){if(i>0)b.append("; caused by ");b.append(e.getClass().getSimpleName());if(e.getMessage()!=null)b.append(": ").append(e.getMessage());}return redact(b.toString());}
    private static String redact(String text){return text.replaceAll("(https?://[^\\s?#]+)[?#][^\\s]*","$1?[redacted]").replaceAll("(?i)(access_token|refresh_token|authorization)([=:]\\s*)[^\\s,]+","$1$2[redacted]");}
    private static synchronized void log(String message,Throwable error) {
        StringWriter sw=new StringWriter();if(error!=null)error.printStackTrace(new PrintWriter(sw));
        String text=Instant.now()+" "+redact(message)+"\n"+redact(sw.toString());
        System.err.print(text);
        try {Path p=logPath();Files.createDirectories(p.getParent());if(Files.exists(p)&&Files.size(p)>4L*1024*1024)Files.move(p,p.resolveSibling("downloads-previous.log"),StandardCopyOption.REPLACE_EXISTING);Files.writeString(p,text,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(IOException ignored) {/* Logging must not mask the actual installation error. */}
    }
}
