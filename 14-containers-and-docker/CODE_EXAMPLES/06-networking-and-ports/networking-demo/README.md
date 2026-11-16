# networking-demo

How traffic reaches a container, and how containers reach each other. One small app with two endpoints:
`GET /hello` and `GET /call-other` (calls `${OTHER_URL}/hello` and reports success or the exception chain).
`demo.sh` runs it in seven situations.

From the host into a container:
- no `-p` — the app runs, the host gets "Couldn't connect";
- `-p 14006:8080` — host port 14006 forwards to container port 8080;
- a second container on the same host port — "port is already allocated";
- `SERVER_ADDRESS=127.0.0.1` — the app answers inside its container but the published port gets "Connection reset".

From one container to another:
- `OTHER_URL=http://localhost:14062` — fails: `localhost` is the calling container itself;
- container name on the default bridge network — fails: names don't resolve there;
- container name on a user-defined network — works, and the called container needs no `-p` at all.

Run (Docker running; Maven and JDK 21 on the host):

```
./demo.sh
```

Uses host ports 14006, 14061, 14062, 14063. Takes about 15 seconds after the first `eclipse-temurin:21-jre` pull.
