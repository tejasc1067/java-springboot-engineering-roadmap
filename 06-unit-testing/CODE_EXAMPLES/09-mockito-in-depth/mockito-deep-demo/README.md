# mockito-deep-demo

The Mockito features beyond stub-and-verify: argument matchers (`any`/`eq`), call counts (`times`/`never`), `ArgumentCaptor`, `thenThrow`, and a `spy`.

Run the tests:

```
mvn test
```

Each test method is one feature. Read the comments — especially the matcher rule ("if one argument is a matcher, all must be") and why `spy` uses `doReturn(...).when(...)` instead of `when(...).thenReturn(...)`.
