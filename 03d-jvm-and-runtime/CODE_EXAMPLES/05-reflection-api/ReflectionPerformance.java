import java.lang.reflect.Method;

public class ReflectionPerformance {

    static class Adder {
        public int add(int a, int b) { return a + b; }
    }

    public static void main(String[] args) throws Exception {
        Adder target = new Adder();
        int n = 5_000_000;

        // Warmup so JIT compiles everything before we measure.
        for (int i = 0; i < 50_000; i++) target.add(i, i);

        // 1) Direct call.
        long t1 = System.nanoTime();
        long sum1 = 0;
        for (int i = 0; i < n; i++) sum1 += target.add(i, i);
        long directMs = (System.nanoTime() - t1) / 1_000_000;

        // 2) Reflective call, looking up the method each time (worst case).
        long t2 = System.nanoTime();
        long sum2 = 0;
        for (int i = 0; i < n; i++) {
            Method m = Adder.class.getDeclaredMethod("add", int.class, int.class);
            sum2 += (int) m.invoke(target, i, i);
        }
        long lookupEachMs = (System.nanoTime() - t2) / 1_000_000;

        // 3) Reflective call with cached Method (what frameworks actually do).
        Method cached = Adder.class.getDeclaredMethod("add", int.class, int.class);
        long t3 = System.nanoTime();
        long sum3 = 0;
        for (int i = 0; i < n; i++) sum3 += (int) cached.invoke(target, i, i);
        long cachedMs = (System.nanoTime() - t3) / 1_000_000;

        System.out.println("sums equal? " + (sum1 == sum2 && sum2 == sum3));
        System.out.println("direct call:          " + directMs + " ms");
        System.out.println("reflect (lookup each):" + lookupEachMs + " ms");
        System.out.println("reflect (cached):     " + cachedMs + " ms");
        System.out.println();
        System.out.println("Direct is ~free under JIT. Reflective calls are real work, even cached.");
    }
}
