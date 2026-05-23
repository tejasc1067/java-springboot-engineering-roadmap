import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class TransientField {

    static class Session implements Serializable {
        private static final long serialVersionUID = 1L;
        private String user;
        private transient String password;     // skipped during serialization
        private transient long lastActivityNanos; // also skipped
        public Session(String user, String password) {
            this.user = user;
            this.password = password;
            this.lastActivityNanos = System.nanoTime();
        }
        public String toString() {
            return "Session(user=" + user + ", password=" + password + ", lastActivityNanos=" + lastActivityNanos + ")";
        }
    }

    public static void main(String[] args) throws Exception {
        Session original = new Session("alice", "s3cr3t");
        System.out.println("before: " + original);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Session restored = (Session) in.readObject();
            System.out.println("after:  " + restored);
            System.out.println();
            System.out.println("Notice: password and lastActivityNanos are null/0 after deserialization.");
            System.out.println("transient excludes the field from the byte stream entirely.");
        }
    }
}
