package gg.kapkan.release;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Preserve macOS Keychain; protect Windows refresh tokens with CurrentUser DPAPI. */
public final class PlatformCredentials {
    private PlatformCredentials() {}
    private static boolean windows(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows");}
    public static void save(String account,String value) {
        try {
            if(!windows()) {
                run(null,"/usr/bin/security","add-generic-password","-U","-s","KapKan Microsoft","-a",account,"-w",value);
                return;
            }
            byte[] encrypted=run(value.getBytes(StandardCharsets.UTF_8),"powershell.exe","-NoProfile","-NonInteractive","-Command",
                "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd(); $b=[Text.Encoding]::UTF8.GetBytes($s); $p=[Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); [Console]::Write([Convert]::ToBase64String($p))");
            Path path=path(account); Files.createDirectories(path.getParent());
            Path temp=Files.createTempFile(path.getParent(),"credential-",".tmp");
            try {Files.write(temp,encrypted);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            finally {Files.deleteIfExists(temp);}
        }catch(Exception e){System.err.println("KapKan: could not save the sign-in token in the OS credential store ("+e.getClass().getSimpleName()+").");}
    }
    public static String load(String account) {
        try {
            if(!windows())return new String(run(null,"/usr/bin/security","find-generic-password","-s","KapKan Microsoft","-a",account,"-w"),StandardCharsets.UTF_8).trim();
            Path p=path(account);if(!Files.isRegularFile(p))return "";
            byte[] bytes=run(Files.readAllBytes(p),"powershell.exe","-NoProfile","-NonInteractive","-Command",
                "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd(); $b=[Convert]::FromBase64String($s); $p=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); [Console]::Write([Text.Encoding]::UTF8.GetString($p))");
            return new String(bytes,StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }
    private static Path path(String account)throws Exception{
        String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8)));
        return Path.of(System.getProperty("user.home"),".kapkan","credentials",id+".dpapi");
    }
    private static byte[] run(byte[] input,String...command)throws Exception {
        Process p=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(var out=p.getOutputStream()){if(input!=null)out.write(input);}
        var data=new ByteArrayOutputStream();
        Thread reader=Thread.ofPlatform().daemon().start(()->{try(var stream=p.getInputStream()){stream.transferTo(data);}catch(IOException ignored){}});
        if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("credential-store timeout");}
        reader.join(1000);if(p.exitValue()!=0)throw new IOException("credential-store failed");return data.toByteArray();
    }
}
