package com.example;

// Sibling marker to OnCreate. Tagging a constraint with groups = OnUpdate.class
// makes it run only on the update path, e.g. @Validated({Default.class, OnUpdate.class}).
public interface OnUpdate {
}
