package gg.kapkan.release;

import java.lang.reflect.Method;

/** Regression test for the Windows Fabric classpath false-negative. */
public final class WindowsFabricClasspathTest {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String version = "0.19.5";
        String jar = "fabric-loader-0.19.5.jar";
        String win = "C:\\Users\\tester\\.kapkan\\game\\libraries\\net\\fabricmc\\fabric-loader\\0.19.5\\" + jar;
        String unix = "/Users/tester/.kapkan/game/libraries/net/fabricmc/fabric-loader/0.19.5/" + jar;
        check(FabricClasspathSupport.matchesLoader(win, version, jar), "Windows backslash path must match");
        check(FabricClasspathSupport.matchesLoader(unix, version, jar), "Unix path must still match");
        check(!FabricClasspathSupport.matchesLoader(win.replace(jar,"fabric-loader-0.19.4.jar"), version, jar), "Wrong loader version must not match");
        check(!FabricClasspathSupport.matchesLoader("C:\\mods\\"+jar, version, jar), "Unrelated jar path must not match");

        Class<?> cls=Class.forName("gg.kapkan.launcher.launch.MinecraftProcessLauncher");
        Method m=cls.getDeclaredMethod("lambda$verifyAndRepairFabricCommand$4",String.class,String.class,String.class);
        m.setAccessible(true);
        check((Boolean)m.invoke(null,version,jar,win), "Patched packaged verifier must accept Windows path");
        check((Boolean)m.invoke(null,version,jar,unix), "Patched packaged verifier must accept Unix path");
        check(!(Boolean)m.invoke(null,version,jar,"C:\\Users\\tester\\.kapkan\\game\\libraries\\net\\fabricmc\\fabric-loader\\0.19.4\\fabric-loader-0.19.4.jar"), "Patched verifier must reject wrong loader");
        System.out.println("Windows Fabric classpath regression: PASS ("+assertions+" assertions)");
    }
}
