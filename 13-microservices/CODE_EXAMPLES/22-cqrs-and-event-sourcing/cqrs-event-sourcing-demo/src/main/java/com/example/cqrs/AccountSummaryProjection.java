package com.example.cqrs;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The read side of CQRS: a view shaped for one screen ("my accounts"), built from events.
 * It is updated only when catchUp() runs - in production a background consumer - so it can lag behind.
 */
public class AccountSummaryProjection {

    public record AccountSummary(String accountId, String owner, long balanceCents, int transactions) { }

    private final Map<String, AccountSummary> summaries = new HashMap<>();
    private long lastPosition = 0;

    public void catchUp(EventStore store) {
        for (StoredEvent stored : store.readAllAfter(lastPosition)) {
            String id = stored.accountId();
            switch (stored.event()) {
                case AccountEvent.Opened o -> summaries.put(id, new AccountSummary(id, o.owner(), 0, 0));
                case AccountEvent.Deposited d -> summaries.computeIfPresent(id, (k, s) ->
                        new AccountSummary(k, s.owner(), s.balanceCents() + d.cents(), s.transactions() + 1));
                case AccountEvent.Withdrawn w -> summaries.computeIfPresent(id, (k, s) ->
                        new AccountSummary(k, s.owner(), s.balanceCents() - w.cents(), s.transactions() + 1));
            }
            lastPosition = stored.position();
        }
    }

    public Optional<AccountSummary> find(String accountId) { return Optional.ofNullable(summaries.get(accountId)); }

    /** Lets a caller wait until the view includes its own write (see CqrsReadModelTest). */
    public boolean hasProcessed(long position) { return lastPosition >= position; }
}
