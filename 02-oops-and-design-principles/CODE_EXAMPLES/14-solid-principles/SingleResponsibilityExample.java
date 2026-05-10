class UserRepository {

    void saveUser() {

        System.out.println(
                "User Saved in Database"
        );
    }
}

class EmailService {

    void sendEmail() {

        System.out.println(
                "Email Sent"
        );
    }
}

public class SingleResponsibilityExample {

    public static void main(String[] args) {

        UserRepository repository =
                new UserRepository();

        EmailService emailService =
                new EmailService();

        repository.saveUser();

        emailService.sendEmail();
    }
}