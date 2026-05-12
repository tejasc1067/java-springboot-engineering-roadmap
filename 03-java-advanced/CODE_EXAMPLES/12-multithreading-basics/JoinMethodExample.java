class DownloadTask
        extends Thread {

    @Override
    public void run() {

        System.out.println(
                "Downloading file..."
        );
    }
}

public class JoinMethodExample {

    public static void main(String[] args)
            throws InterruptedException {

        DownloadTask task =
                new DownloadTask();

        task.start();

        task.join();

        System.out.println(
                "Download completed"
        );
    }
}