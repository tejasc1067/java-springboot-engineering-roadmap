package com.example;

public class RetryPolicy {

    private final ExceptionClassifier classifier;
    private final int maxAttempts;

    public RetryPolicy(ExceptionClassifier classifier, int maxAttempts) {
        this.classifier = classifier;
        this.maxAttempts = maxAttempts;
    }

    public boolean shouldRetry(int attempt, Throwable cause) {
        if (attempt >= maxAttempts) {
            return false;
        }
        return classifier.isTransient(cause);
    }
}
