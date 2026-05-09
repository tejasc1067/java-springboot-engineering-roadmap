class InvalidSalaryException extends Exception {

    InvalidSalaryException(String message) {

        super(message);
    }
}

public class CustomExceptionExample {

    public static void validateSalary(double salary)
            throws InvalidSalaryException {

        if (salary < 10000) {

            throw new InvalidSalaryException(
                    "Salary Below Minimum Requirement"
            );
        }

        System.out.println("Salary Validation Successful");
    }

    public static void main(String[] args) {

        try {

            validateSalary(5000);

        } catch (InvalidSalaryException exception) {

            System.out.println(exception.getMessage());
        }
    }
}