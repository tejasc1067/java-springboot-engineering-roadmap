public class SleepIsNotPrecise {

    public static void main(String[] args) throws InterruptedException {
        int requestedMs = 50;
        int samples = 5;

        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            Thread.sleep(requestedMs);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("requested " + requestedMs + "ms, actually slept " + elapsedMs + "ms");
        }

        System.out.println();
        System.out.println("Thread.sleep guarantees AT LEAST the requested time.");
        System.out.println("On a busy system or under GC pressure it can be noticeably longer.");
    }
}
