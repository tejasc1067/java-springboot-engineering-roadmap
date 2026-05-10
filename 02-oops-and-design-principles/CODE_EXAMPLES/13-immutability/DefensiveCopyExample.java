import java.util.ArrayList;
import java.util.List;

final class Employee {

    private final List<String> skills;

    Employee(List<String> skills) {

        this.skills =
                new ArrayList<>(skills);
    }

    public List<String> getSkills() {

        return new ArrayList<>(skills);
    }
}

public class DefensiveCopyExample {

    public static void main(String[] args) {

        List<String> skills =
                new ArrayList<>();

        skills.add("Java");

        Employee employee =
                new Employee(skills);

        List<String> employeeSkills =
                employee.getSkills();

        employeeSkills.add("AWS");

        System.out.println(
                employee.getSkills()
        );
    }
}