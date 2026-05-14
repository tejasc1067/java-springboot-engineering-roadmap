import java.lang.ref.WeakReference;

class Employee {

    String name =
            "Rahul";
}

public class WeakReferenceGcExample {

    public static void main(String[] args) {

        Employee employee =
                new Employee();

        WeakReference<Employee> weakReference =
                new WeakReference<>(employee);

        System.out.println(
                weakReference.get()
        );

        employee = null;

        System.gc();

        System.out.println(
                weakReference.get()
        );
    }
}