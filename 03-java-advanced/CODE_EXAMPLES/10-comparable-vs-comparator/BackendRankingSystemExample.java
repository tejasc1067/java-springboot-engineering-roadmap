import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class Player {

    private final String name;

    private final int score;

    Player(String name,
           int score) {

        this.name = name;

        this.score = score;
    }

    public int getScore() {

        return score;
    }

    @Override
    public String toString() {

        return name + " - " + score;
    }
}

public class BackendRankingSystemExample {

    public static void main(String[] args) {

        List<Player> players =
                new ArrayList<>();

        players.add(
                new Player("Tejas", 950)
        );

        players.add(
                new Player("Rahul", 850)
        );

        players.add(
                new Player("Amit", 990)
        );

        players.sort(
                Comparator.comparing(
                        Player::getScore
                ).reversed()
        );

        System.out.println(players);
    }
}