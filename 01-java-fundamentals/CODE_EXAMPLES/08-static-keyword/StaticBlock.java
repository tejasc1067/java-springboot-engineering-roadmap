// A static block runs ONCE, when the class is first loaded by the JVM.
// Use it for one-time initialization that's too complex for a field initializer.
//
// In practice you'll rarely need this in modern code — most initialization
// fits in `static T name = ...` directly. You'll meet static blocks more
// often in older codebases.

import java.util.HashMap;
import java.util.Map;

public class StaticBlock {
    public static void main(String[] args) {
        System.out.println("main starts");

        // The block doesn't run until the first time we TOUCH the Config class.
        System.out.println("about to access Config...");
        System.out.println("timeout = " + Config.defaults.get("timeout"));

        // Touching it again does NOT re-run the block.
        System.out.println("retries = " + Config.defaults.get("retries"));
    }
}

class Config {
    static Map<String, String> defaults;

    static {
        System.out.println("(Config static block running)");
        defaults = new HashMap<>();
        defaults.put("timeout", "30");
        defaults.put("retries", "3");
    }
}
