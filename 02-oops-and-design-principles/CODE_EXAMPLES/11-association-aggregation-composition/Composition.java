// Composition: the House creates and owns its Rooms. Rooms are not created
// outside the House and cannot be transferred. Destroy the House → the Rooms
// go with it.

import java.util.Collections;
import java.util.List;

class Room {
    private final String name;
    Room(String name) { this.name = name; }
    @Override public String toString() { return "Room[" + name + "]"; }
}

class House {
    private final List<Room> rooms;       // private — owned, no leaking

    House() {
        // Rooms are constructed INSIDE the House — outside code never sees them.
        this.rooms = List.of(
                new Room("kitchen"),
                new Room("bedroom"),
                new Room("bathroom")
        );
    }

    // Read-only view; no add/remove from outside.
    public List<Room> getRooms() {
        return Collections.unmodifiableList(rooms);
    }
}

public class Composition {
    public static void main(String[] args) {
        House h = new House();
        System.out.println("Rooms: " + h.getRooms());

        // Can't construct a Room and "give" it to the house — there's no API for it.
        // The house controls its parts entirely.
    }
}
