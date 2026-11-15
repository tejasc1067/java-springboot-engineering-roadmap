package com.example.cqrs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrencyTest {

    private final EventStore store = new EventStore();
    private final AccountCommands commands = new AccountCommands(store);

    @BeforeEach
    void accountWith100() {
        commands.open("acc-1", "Ann");
        commands.deposit("acc-1", 10_000);
    }

    @Test
    void withoutAVersionCheck_twoWithdrawalsBothPass_andTheAccountGoesNegative() {
        // Two requests load the account at the same moment. Each sees 100.00 and approves 80.00.
        Account seenByA = commands.load("acc-1");
        Account seenByB = commands.load("acc-1");

        store.appendWithoutVersionCheck("acc-1", seenByA.withdraw(8_000));
        store.appendWithoutVersionCheck("acc-1", seenByB.withdraw(8_000));

        assertThat(commands.load("acc-1").balanceCents()).isEqualTo(-6_000);
    }

    @Test
    void withAnExpectedVersion_theSecondWithdrawalIsRejected() {
        Account seenByA = commands.load("acc-1");
        Account seenByB = commands.load("acc-1");

        store.append("acc-1", seenByA.version(), seenByA.withdraw(8_000));

        // B decided based on version 2, but the stream is now at version 3.
        assertThatThrownBy(() -> store.append("acc-1", seenByB.version(), seenByB.withdraw(8_000)))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessage("account acc-1 is at version 3, expected 2");

        // B's caller reloads and decides again - and the rule now refuses it.
        assertThatThrownBy(() -> commands.withdraw("acc-1", 8_000)).hasMessageContaining("insufficient funds");
        assertThat(commands.load("acc-1").balanceCents()).isEqualTo(2_000);
    }
}
