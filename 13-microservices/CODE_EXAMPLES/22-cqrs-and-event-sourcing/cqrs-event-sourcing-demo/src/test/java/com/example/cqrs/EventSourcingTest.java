package com.example.cqrs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSourcingTest {

    private final EventStore store = new EventStore();
    private final AccountCommands commands = new AccountCommands(store);

    @BeforeEach
    void history() {
        commands.open("acc-1", "Ann");
        commands.deposit("acc-1", 10_000);
        commands.withdraw("acc-1", 2_500);
        commands.deposit("acc-1", 500);
    }

    @Test
    void currentState_isTheReplayOfEveryEvent() {
        assertThat(store.streamOf("acc-1")).extracting(StoredEvent::event).containsExactly(
                new AccountEvent.Opened("Ann"),
                new AccountEvent.Deposited(10_000),
                new AccountEvent.Withdrawn(2_500),
                new AccountEvent.Deposited(500));

        assertThat(commands.load("acc-1").balanceCents()).isEqualTo(8_000);
    }

    @Test
    void anyPastState_canBeRebuilt() {
        // "What was the balance after the withdrawal?" Replay only the first 3 events.
        Account asOfThen = Account.replay(store.streamOf("acc-1").subList(0, 3));

        assertThat(asOfThen.balanceCents()).isEqualTo(7_500);
    }

    @Test
    void rulesAreCheckedAgainstReplayedState_andRejectedCommandsLeaveNoEvent() {
        assertThatThrownBy(() -> commands.withdraw("acc-1", 1_000_000))
                .hasMessageContaining("insufficient funds");

        assertThat(store.streamOf("acc-1")).hasSize(4);
    }
}
