package com.example.async;

import org.springframework.stereotype.Service;

// The fix for self-invocation: call the @Async method from ANOTHER bean, so the call goes through the proxy.
@Service
public class ReportCaller {

    private final ReportService reports;

    public ReportCaller(ReportService reports) { this.reports = reports; }

    public String whoRunsTheReport() {
        return reports.whoRunsMe().join();
    }
}
