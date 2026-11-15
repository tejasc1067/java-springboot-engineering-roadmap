package com.example.cqrs;

import java.util.List;

/**
 * Event-sourced aggregate. Its state is never saved; it is rebuilt by replaying the stream.
 * Command methods check the rules against that state and RETURN new events - they change nothing themselves.
 */
public class Account {

    private long balanceCents;
    private int version;      // how many events have been applied

    public static Account replay(List<StoredEvent> stream) {
        Account account = new Account();
        stream.forEach(stored -> account.apply(stored.event()));
        return account;
    }

    private void apply(AccountEvent event) {
        switch (event) {
            case AccountEvent.Opened opened -> balanceCents = 0;
            case AccountEvent.Deposited d -> balanceCents += d.cents();
            case AccountEvent.Withdrawn w -> balanceCents -= w.cents();
        }
        version++;
    }

    public List<AccountEvent> deposit(long cents) {
        if (cents <= 0) throw new IllegalArgumentException("deposit must be positive");
        return List.of(new AccountEvent.Deposited(cents));
    }

    public List<AccountEvent> withdraw(long cents) {
        if (cents > balanceCents) {
            throw new IllegalStateException("insufficient funds: balance " + balanceCents + ", requested " + cents);
        }
        return List.of(new AccountEvent.Withdrawn(cents));
    }

    public long balanceCents() { return balanceCents; }
    public int version() { return version; }
}
