// `private` fields aren't enough if the getter hands out a reference to a
// mutable internal object. The caller can then mutate your insides.
//
// Two fixes shown: unmodifiable view, and defensive copy.

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Team {
    private final List<String> members = new ArrayList<>();

    public void addMember(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException();
        members.add(name);
    }

    // LEAKY — returns a reference to the internal list itself.
    public List<String> getMembersLeaky() {
        return members;
    }

    // FIXED option A — unmodifiable view. Cheap (no copy); attempts to mutate throw.
    public List<String> getMembersUnmodifiable() {
        return Collections.unmodifiableList(members);
    }

    // FIXED option B — defensive copy. Caller can do whatever; original is safe.
    public List<String> getMembersCopy() {
        return new ArrayList<>(members);
    }
}

public class DefensiveCopying {
    public static void main(String[] args) {
        Team t = new Team();
        t.addMember("Alice");
        t.addMember("Bob");

        // Leak in action — the caller mutates Team's internal list.
        t.getMembersLeaky().add("Mallory");
        System.out.println("After leak, internal members: " + t.getMembersLeaky());

        // Unmodifiable view rejects mutation.
        try {
            t.getMembersUnmodifiable().add("Eve");
        } catch (UnsupportedOperationException e) {
            System.out.println("Unmodifiable view rejected the mutation.");
        }

        // Copy lets caller do anything to the returned list; the original is untouched.
        List<String> copy = t.getMembersCopy();
        copy.add("Eve");
        System.out.println("Copy: " + copy);
        System.out.println("Original: " + t.getMembersLeaky());
    }
}
