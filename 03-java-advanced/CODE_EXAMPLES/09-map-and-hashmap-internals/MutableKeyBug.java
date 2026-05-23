import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MutableKeyBug {

    static class CompositeKey {
        int tenantId;
        String name;
        CompositeKey(int tenantId, String name) {
            this.tenantId = tenantId;
            this.name = name;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CompositeKey other)) return false;
            return tenantId == other.tenantId && Objects.equals(name, other.name);
        }
        @Override
        public int hashCode() {
            return Objects.hash(tenantId, name);
        }
    }

    public static void main(String[] args) {
        Map<CompositeKey, String> map = new HashMap<>();
        CompositeKey k = new CompositeKey(1, "users");
        map.put(k, "user-data");

        System.out.println("before mutation: get → " + map.get(k));

        k.tenantId = 2;

        System.out.println("after mutation:  get → " + map.get(k)
                + "   ← null! same reference, can't find itself");
        System.out.println("map still claims size: " + map.size());

        System.out.println();
        System.out.println("the entry is still in the bucket the original hashCode pointed to,");
        System.out.println("but the lookup now computes a different bucket. Lost.");
        System.out.println("fix: make key fields final, or use a record.");
    }
}
