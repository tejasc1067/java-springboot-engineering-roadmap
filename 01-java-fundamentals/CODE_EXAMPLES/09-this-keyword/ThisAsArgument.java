// Sometimes you need to hand the current object to someone else. `this` is
// how. Also: returning `this` enables fluent chaining (the builder pattern).

public class ThisAsArgument {
    public static void main(String[] args) {

        // Passing `this` to another object.
        Logger log = new Logger();
        new Order(101, log);
        new Order(102, log);

        // Returning `this` to enable chaining.
        String result = new Greeter()
                .say("hello")
                .say("there")
                .say("alice")
                .done();
        System.out.println("\nchained: " + result);
    }
}

class Logger {
    void log(Object source, String message) {
        System.out.println("[" + source.getClass().getSimpleName() + "] " + message);
    }
}

class Order {
    int id;
    Logger logger;

    Order(int id, Logger logger) {
        this.id = id;
        this.logger = logger;
        logger.log(this, "order " + id + " created");   // hand `this` to the logger
    }
}

class Greeter {
    String message = "";

    Greeter say(String s) {
        message += s + " ";
        return this;            // returning `this` lets the caller chain calls
    }

    String done() {
        return message.trim();
    }
}
