import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerialVersionUidMismatch {

    // Class definition the running JVM knows: SUID = 1L.
    static class Customer implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        public Customer(String name) { this.name = name; }
    }

    public static void main(String[] args) throws Exception {
        // Step 1: serialize an instance with the current (SUID = 1L) definition.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Customer("Alice"));
        }
        byte[] payload = bytes.toByteArray();

        // Step 2: simulate "an older payload from when the class had a DIFFERENT SUID"
        // by flipping the bytes that encode serialVersionUID inside the stream.
        // ObjectOutputStream embeds the SUID right after the class name; we find it by scan.
        byte[] tampered = withTamperedSuid(payload, "SerialVersionUidMismatch$Customer");

        // Step 3: try to read the tampered payload with the CURRENT class definition.
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(tampered))) {
            Object o = in.readObject();
            System.out.println("(unexpectedly succeeded) " + o);
        } catch (InvalidClassException e) {
            System.out.println("got InvalidClassException as expected:");
            System.out.println("  " + e.getMessage());
            System.out.println();
            System.out.println("Local SUID does not match the SUID embedded in the byte stream.");
            System.out.println("That's why every Serializable class should declare serialVersionUID explicitly.");
        }
    }

    // Find the 8-byte SUID immediately following the class name in the stream and flip it.
    static byte[] withTamperedSuid(byte[] in, String className) {
        byte[] nameBytes = className.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // search for the class name in the bytes
        int idx = -1;
        outer: for (int i = 0; i < in.length - nameBytes.length; i++) {
            for (int j = 0; j < nameBytes.length; j++) {
                if (in[i + j] != nameBytes[j]) continue outer;
            }
            idx = i;
            break;
        }
        if (idx < 0) throw new IllegalStateException("class name not found in stream");
        // The 8 bytes immediately after the name are the SUID.
        byte[] out = in.clone();
        int suidStart = idx + nameBytes.length;
        // overwrite SUID with all 0xFF -- very different from 1L
        for (int i = 0; i < 8; i++) out[suidStart + i] = (byte) 0xFF;
        return out;
    }
}
