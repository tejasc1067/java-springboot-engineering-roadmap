import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;

class Employee implements Serializable {

    private String name;

    public Employee(String name) {

        this.name = name;
    }

    @Override
    public String toString() {

        return name;
    }
}

public class BasicDeserializationExample {

    public static void main(String[] args)
            throws Exception {

        FileInputStream fileInputStream =
                new FileInputStream("employee.ser");

        ObjectInputStream objectInputStream =
                new ObjectInputStream(
                        fileInputStream
                );

        Employee employee =
                (Employee) objectInputStream.readObject();

        objectInputStream.close();

        System.out.println(employee);
    }
}