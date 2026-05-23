public class JsonByHandSimple {

    // A tiny JSON encoder/decoder for a flat record-shaped object.
    // Not meant to be a real JSON parser -- just enough to show that JSON
    // serialization is conceptually simple and DOESN'T reconstruct arbitrary
    // types from the bytes (which is why it doesn't have Java's RCE history).

    record Customer(String name, int age) {}

    static String toJson(Customer c) {
        return "{\"name\":\"" + escape(c.name()) + "\",\"age\":" + c.age() + "}";
    }

    static Customer fromJson(String json) {
        // Very small naive parser for THIS shape only.
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) throw new IllegalArgumentException("not an object");
        String body = trimmed.substring(1, trimmed.length() - 1);
        String name = "";
        int age = 0;
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":", 2);
            String key = kv[0].trim().replace("\"", "");
            String val = kv[1].trim();
            if (key.equals("name")) name = val.replace("\"", "");
            else if (key.equals("age")) age = Integer.parseInt(val);
        }
        return new Customer(name, age);
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void main(String[] args) {
        Customer original = new Customer("Alice", 25);
        String json = toJson(original);
        System.out.println("JSON: " + json);

        Customer back = fromJson(json);
        System.out.println("parsed: " + back);
        System.out.println("equal?  " + original.equals(back));

        System.out.println();
        System.out.println("In production, use Jackson (com.fasterxml.jackson.databind.ObjectMapper).");
        System.out.println("Crucially: JSON deserializers reconstruct a SPECIFIC target type the caller");
        System.out.println("passes in, not arbitrary classes from the byte stream. That's why JSON doesn't");
        System.out.println("have the RCE history Java native serialization does.");
    }
}
