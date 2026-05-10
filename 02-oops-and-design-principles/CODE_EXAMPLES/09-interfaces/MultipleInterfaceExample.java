interface Camera {

    void takePhoto();
}

interface MusicPlayer {

    void playMusic();
}

class SmartPhone
        implements Camera, MusicPlayer {

    @Override
    public void takePhoto() {

        System.out.println(
                "Photo Captured"
        );
    }

    @Override
    public void playMusic() {

        System.out.println(
                "Music Playing"
        );
    }
}

public class MultipleInterfaceExample {

    public static void main(String[] args) {

        SmartPhone phone = new SmartPhone();

        phone.takePhoto();

        phone.playMusic();
    }
}