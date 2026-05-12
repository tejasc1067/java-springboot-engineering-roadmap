class DatabaseServiceException
        extends Exception {

    DatabaseServiceException(
            String message,
            Throwable cause
    ) {

        super(message, cause);
    }
}

public class ExceptionChainingExample {

    static void databaseOperation()
            throws DatabaseServiceException {

        try {

            int result =
                    10 / 0;

            System.out.println(result);

        } catch (ArithmeticException exception) {

            throw new DatabaseServiceException(
                    "Database Operation Failed",
                    exception
            );
        }
    }

    public static void main(String[] args) {

        try {

            databaseOperation();

        } catch (
                DatabaseServiceException exception
        ) {

            System.out.println(
                    exception.getMessage()
            );

            exception.printStackTrace();
        }
    }
}