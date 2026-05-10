final class UserDTO {

    private final int userId;

    private final String email;

    UserDTO(
            int userId,
            String email
    ) {

        this.userId = userId;

        this.email = email;
    }

    public int getUserId() {

        return userId;
    }

    public String getEmail() {

        return email;
    }
}

public class BackendImmutableDTOExample {

    public static void main(String[] args) {

        UserDTO userDTO =
                new UserDTO(
                        101,
                        "tejas@gmail.com"
                );

        System.out.println(
                userDTO.getUserId()
        );

        System.out.println(
                userDTO.getEmail()
        );
    }
}