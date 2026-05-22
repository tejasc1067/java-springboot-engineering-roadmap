// When a field is a mutable type (List, Map, array, etc.), private final is
// not enough. The caller can mutate the underlying object through the reference
// they handed in or the one we hand back.
//
// Two defenses: copy on the way IN (so the caller's later changes don't affect us)
//               copy on the way OUT (so what we return can't mutate our insides)

import java.util.ArrayList;
import java.util.List;

public final class Team {

    private final List<String> members;

    public Team(List<String> members) {
        // Copy on the way in. `List.copyOf` returns an unmodifiable copy.
        this.members = List.copyOf(members);
    }

    public List<String> getMembers() {
        // Already unmodifiable; safe to return directly.
        return members;
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("Alice", "Bob"));
        Team t = new Team(input);

        // Mutating the input doesn't affect Team.
        input.add("Mallory");
        System.out.println("Team after caller mutated their input: " + t.getMembers());

        // The returned list can't be mutated either.
        try {
            t.getMembers().add("Eve");
        } catch (UnsupportedOperationException e) {
            System.out.println("Caller's attempt to mutate returned list: rejected.");
        }

        System.out.println("\nTeam is truly immutable. Its internal list is decoupled from any caller's references.");
    }
}
