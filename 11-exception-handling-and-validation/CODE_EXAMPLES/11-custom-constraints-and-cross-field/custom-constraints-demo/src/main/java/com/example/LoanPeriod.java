package com.example;

import java.time.LocalDate;

// The class-level constraint sits on the type. The validator receives the whole record,
// so it can compare two fields against each other — something a field constraint cannot do.
@ValidDateRange
public record LoanPeriod(LocalDate startDate, LocalDate endDate) {}
