public class PairTwoParams {

    static class Pair<K, V> {
        private final K key;
        private final V value;
        public Pair(K key, V value) { this.key = key; this.value = value; }
        public K getKey()   { return key; }
        public V getValue() { return value; }
        @Override public String toString() { return key + " -> " + value; }
    }

    public static void main(String[] args) {
        Pair<String, Integer> age   = new Pair<>("age", 30);
        Pair<String, String>  city  = new Pair<>("city", "Pune");
        Pair<Long, Boolean>   ready = new Pair<>(42L, true);

        System.out.println(age);
        System.out.println(city);
        System.out.println(ready);

        System.out.println();
        System.out.println("key types and value types vary independently.");
    }
}
