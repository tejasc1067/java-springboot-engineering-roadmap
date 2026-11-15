package com.example.cqrs;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CqrsReadModelTest {

    private final EventStore store = new EventStore();
    private final AccountCommands commands = new AccountCommands(store);
    private final AccountSummaryProjection summaries = new AccountSummaryProjection();

    @Test
    void readModel_isBuiltFromEvents_andShapedForTheScreen() {
        commands.open("acc-1", "Ann");
        commands.deposit("acc-1", 10_000);
        commands.withdraw("acc-1", 2_500);

        summaries.catchUp(store);

        assertThat(summaries.find("acc-1")).hasValue(
                new AccountSummaryProjection.AccountSummary("acc-1", "Ann", 7_500, 2));
    }

    @Test
    void projectionLag_userDoesNotSeeTheirOwnWrite() {
        commands.open("acc-1", "Ann");
        summaries.catchUp(store);

        commands.deposit("acc-1", 10_000);   // the command succeeded...

        // ...but the projection hasn't processed it yet. The user refreshes and their money "isn't there".
        assertThat(summaries.find("acc-1").orElseThrow().balanceCents()).isZero();
    }

    @Test
    void readYourOwnWrites_byWaitingForTheCommandsPosition() {
        commands.open("acc-1", "Ann");
        long position = commands.deposit("acc-1", 10_000);   // commands return WHERE their event landed

        assertThat(summaries.hasProcessed(position)).isFalse();  // the UI knows the view is stale: show "updating..."
        summaries.catchUp(store);                                // (in production: poll until processed, with a timeout)
        assertThat(summaries.hasProcessed(position)).isTrue();

        assertThat(summaries.find("acc-1").orElseThrow().balanceCents()).isEqualTo(10_000);
    }

    @Test
    void aNewReadModel_canBeBuiltLaterFromTheWholeHistory() {
        commands.open("acc-1", "Ann");
        commands.deposit("acc-1", 10_000);
        commands.withdraw("acc-1", 3_000);
        commands.withdraw("acc-1", 500);
        commands.open("acc-2", "Bob");
        commands.deposit("acc-2", 4_000);
        commands.withdraw("acc-2", 4_000);

        // Months later, fraud wants "largest single withdrawal per account". With only current balances
        // stored, that history would be gone. Here, replay the whole log into a brand-new view.
        Map<String, Long> largestWithdrawal = new HashMap<>();
        for (StoredEvent stored : store.readAllAfter(0)) {
            if (stored.event() instanceof AccountEvent.Withdrawn w) {
                largestWithdrawal.merge(stored.accountId(), w.cents(), Math::max);
            }
        }

        assertThat(largestWithdrawal).containsExactlyInAnyOrderEntriesOf(Map.of("acc-1", 3_000L, "acc-2", 4_000L));
    }
}
