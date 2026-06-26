package com.example;

// A validation group is just a marker interface. It carries no methods.
// Constraints tagged with groups = OnCreate.class only run when OnCreate is
// requested, e.g. @Validated({Default.class, OnCreate.class}) on POST.
public interface OnCreate {
}
