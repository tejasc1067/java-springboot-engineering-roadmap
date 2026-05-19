public class CorrelatedSubquerySimulation {

    public static void main(String[] args) {

        int[] salaries =
                {40000, 60000, 80000};

        int averageSalary =
                (40000 + 60000 + 80000) / 3;

        for (int salary : salaries) {

            if (salary > averageSalary) {

                System.out.println(
                        "Above Average Salary: "
                                + salary
                );
            }
        }
    }
}