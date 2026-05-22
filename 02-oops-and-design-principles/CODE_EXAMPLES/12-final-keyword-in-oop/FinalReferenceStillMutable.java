// `final` locks the variable, NOT the object it points to.
// This is the most common surprise in Java for newcomers.

import java.util.ArrayList;
import java.util.List;

class Team {
    private final List<String> members = new ArrayList<>();   // final reference

    void add(String name) {
        members.add(name);         // ← fine; mutating the LIST, not the reference
    }

    // members = new ArrayList<>();   // ← would not compile

    List<String> getMembers() {
        return members;
    }
}

public class FinalReferenceStillMutable {
    public static void main(String[] args) {
        Team t = new Team();
        t.add("Alice");
        t.add("Bob");
        System.out.println("Team: " + t.getMembers());

        // The list reference is final, but the list itself is mutable.
        // For true immutability you need:
        //   - final reference, AND
        //   - an immutable type (e.g. List.of(...) returns an immutable list)
        final List<String> frozen = List.of("Alice", "Bob");
        try {
            frozen.add("Mallory");
        } catch (UnsupportedOperationException e) {
            System.out.println("\nfrozen list rejected mutation: " + e.getMessage());
        }
    }
}
