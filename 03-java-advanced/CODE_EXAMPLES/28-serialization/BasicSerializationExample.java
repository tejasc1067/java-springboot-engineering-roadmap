import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

class User implements Serializable {

    private String name;

    public User(String name) {

        this.name = name;
    }

    @Override
    public String toString() {

        return name;
    }
}

public class BasicSerializationExample {

    public static void main(String[] args)
            throws Exception {

        User user =
                new User("Tejas");

        FileOutputStream fileOutputStream =
                new FileOutputStream("user.ser");

        ObjectOutputStream objectOutputStream =
                new ObjectOutputStream(
                        fileOutputStream
                );

        objectOutputStream.writeObject(user);

        objectOutputStream.close();

        System.out.println(
                "Object Serialized"
        );
    }
}