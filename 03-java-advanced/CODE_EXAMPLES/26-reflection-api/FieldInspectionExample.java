import java.lang.reflect.Field;

class Employee {

    private String name;

    private int age;
}

public class FieldInspectionExample {

    public static void main(String[] args) {

        Class<Employee> employeeClass =
                Employee.class;

        Field[] fields =
                employeeClass.getDeclaredFields();

        for (Field field : fields) {

            System.out.println(
                    field.getName()
            );
        }
    }
}