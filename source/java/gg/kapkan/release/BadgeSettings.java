package gg.kapkan.release;

import java.nio.file.*;
import java.net.URI;
import java.util.Properties;
import java.io.*;

/** Separate preferences: never rewrites the existing launcher or game configuration. */
public final class BadgeSettings {
    public static final Path FILE = Path.of(System.getProperty("user.home"), ".kapkan", "presence.properties");
    public final Properties values = new Properties();
    public BadgeSettings() { try(var in=Files.newInputStream(FILE)){values.load(in);} catch(IOException ignored){} }
    public boolean enabled(){return Boolean.parseBoolean(values.getProperty("enabled","true"));}
    public boolean own(){return Boolean.parseBoolean(values.getProperty("showOwn","true"));}
    public String endpoint(){return values.getProperty("endpoint","").trim();}
    public static String validateEndpoint(String value) {
        value=value.trim().replaceAll("/+$", "");
        if(value.isEmpty())return value;
        URI u=URI.create(value);
        boolean loopback="localhost".equalsIgnoreCase(u.getHost())||"127.0.0.1".equals(u.getHost())||"[::1]".equals(u.getHost());
        if(u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||
           !("https".equals(u.getScheme())||loopback&&"http".equals(u.getScheme())))
            throw new IllegalArgumentException("Use an HTTPS service URL. HTTP is allowed only for localhost testing.");
        return value;
    }
    public void save(boolean enabled,boolean own,String endpoint)throws IOException {
        values.setProperty("endpoint",validateEndpoint(endpoint));values.setProperty("enabled",Boolean.toString(enabled));
        values.setProperty("showOwn",Boolean.toString(own));Files.createDirectories(FILE.getParent());
        Path tmp=Files.createTempFile(FILE.getParent(),"presence-",".tmp");
        try {try(var out=Files.newOutputStream(tmp)){values.store(out,"KapKan cosmetic presence");}
            try{Files.move(tmp,FILE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}
