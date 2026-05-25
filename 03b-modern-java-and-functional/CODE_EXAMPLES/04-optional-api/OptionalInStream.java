import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OptionalInStream {

    record Address(String city) {}

    static Optional<Address> loadAddress(long userId) {
        // Half the users have no address on file.
        return (userId % 2 == 0) ? Optional.of(new Address("City-" + userId)) : Optional.empty();
    }

    public static void main(String[] args) {
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);

        // Stream<Optional<Address>>  ->  flatMap(Optional::stream)  ->  Stream<Address>
        // Empty Optionals naturally drop out.
        List<String> cities = ids.stream()
            .map(OptionalInStream::loadAddress)        // Stream<Optional<Address>>
            .flatMap(Optional::stream)                  // Stream<Address>  (empties skipped)
            .map(Address::city)
            .collect(Collectors.toList());
        System.out.println("cities (only present): " + cities);

        // What NOT to do: Optional::get blows up the moment a value is missing.
        try {
            List<Address> bad = ids.stream()
                .map(OptionalInStream::loadAddress)
                .map(Optional::get)
                .collect(Collectors.toList());
        } catch (java.util.NoSuchElementException e) {
            System.out.println("Optional::get without filtering -> NoSuchElementException");
        }
    }
}
