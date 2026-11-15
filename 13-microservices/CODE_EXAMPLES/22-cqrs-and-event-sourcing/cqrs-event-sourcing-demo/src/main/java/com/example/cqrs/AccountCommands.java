package com.example.cqrs;

import java.util.List;

/** The write side of CQRS: load, decide, append. Returns the event's global position, never a view. */
public class AccountCommands {

    private final EventStore store;

    public AccountCommands(EventStore store) { this.store = store; }

    public long open(String accountId, String owner) {
        return store.append(accountId, 0, List.of(new AccountEvent.Opened(owner)));
    }

    public long deposit(String accountId, long cents) {
        Account account = load(accountId);
        return store.append(accountId, account.version(), account.deposit(cents));
    }

    public long withdraw(String accountId, long cents) {
        Account account = load(accountId);
        return store.append(accountId, account.version(), account.withdraw(cents));
    }

    public Account load(String accountId) {
        return Account.replay(store.streamOf(accountId));
    }
}
