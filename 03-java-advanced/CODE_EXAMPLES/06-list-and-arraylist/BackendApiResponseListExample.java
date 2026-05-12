import java.util.ArrayList;
import java.util.List;

class UserDto {

    private final int id;

    private final String name;

    UserDto(int id,
            String name) {

        this.id = id;

        this.name = name;
    }

    @Override
    public String toString() {

        return id + " - " + name;
    }
}

public class BackendApiResponseListExample {

    public static void main(String[] args) {

        List<UserDto> users =
                new ArrayList<>();

        users.add(
                new UserDto(101, "Tejas")
        );

        users.add(
                new UserDto(102, "Rahul")
        );

        System.out.println(users);
    }
}