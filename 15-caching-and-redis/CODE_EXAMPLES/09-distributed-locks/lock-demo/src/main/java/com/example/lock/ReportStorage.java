package com.example.lock;

/**
 * Where the lock holder writes its result. Stands in for a database table or a file store.
 * The fenced write is the important one: storage, not the lock, has the final say.
 */
public class ReportStorage {

    private String content = "";
    private long highestTokenSeen;

    public synchronized void write(String newContent) {
        content = newContent;
    }

    /** In SQL: UPDATE report SET content = ?, token = ? WHERE token < ?  (0 rows updated = rejected) */
    public synchronized boolean write(long fencingToken, String newContent) {
        if (fencingToken < highestTokenSeen) {
            return false;
        }
        highestTokenSeen = fencingToken;
        content = newContent;
        return true;
    }

    public synchronized String content() {
        return content;
    }
}
