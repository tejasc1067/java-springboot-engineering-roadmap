class ApiResponse<T> {

    private final T data;

    ApiResponse(T data) {

        this.data = data;
    }

    public T getData() {

        return data;
    }
}

public class BackendGenericResponseExample {

    public static void main(String[] args) {

        ApiResponse<String> response =
                new ApiResponse<>(
                        "User Created Successfully"
                );

        System.out.println(
                response.getData()
        );
    }
}