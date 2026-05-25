import java.util.Optional;

public class OptionalMapAndFlatMap {

    record User(long id, String name) {}
    record Profile(String displayName, String city) {}

    static Optional<User> findUser(long id) {
        return id > 0 ? Optional.of(new User(id, "user-" + id)) : Optional.empty();
    }

    static Optional<Profile> loadProfile(User u) {
        // Only odd-id users have a profile.
        return (u.id() % 2 == 1) ? Optional.of(new Profile(u.name() + "-display", "Helsinki")) : Optional.empty();
    }

    public static void main(String[] args) {
        // map: User -> String  ===>  Optional<String>.
        Optional<String> name = findUser(1).map(User::name);
        System.out.println("map result:    " + name);

        // Chain maps.
        Optional<Integer> len = findUser(2).map(User::name).map(String::length);
        System.out.println("chained map:   " + len);

        // flatMap: when the function ITSELF returns Optional.
        // findUser(...).flatMap(loadProfile) flattens Optional<Optional<Profile>> to Optional<Profile>.
        Optional<Profile> p1 = findUser(1).flatMap(OptionalMapAndFlatMap::loadProfile);
        Optional<Profile> p2 = findUser(2).flatMap(OptionalMapAndFlatMap::loadProfile);
        Optional<Profile> p3 = findUser(-1).flatMap(OptionalMapAndFlatMap::loadProfile);
        System.out.println("p1 (odd id):   " + p1);
        System.out.println("p2 (even id):  " + p2);
        System.out.println("p3 (no user):  " + p3);

        // filter -- empty if predicate is false.
        Optional<User> adultOnly = findUser(1).filter(u -> u.name().length() > 10);
        System.out.println("filter result: " + adultOnly);
    }
}
