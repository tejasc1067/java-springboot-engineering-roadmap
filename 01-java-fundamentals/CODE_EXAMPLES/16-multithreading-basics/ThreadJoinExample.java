class DownloadTask extends Thread {

    @Override
    public void run() {

        System.out.println("Downloading File...");
    }
}

public class ThreadJoinExample {

    public static void main(String[] args)
            throws InterruptedException {

        DownloadTask task = new DownloadTask();

        task.start();

        // Wait for thread completion
        task.join();

        System.out.println(
                "File Processing Started"
        );
    }
}