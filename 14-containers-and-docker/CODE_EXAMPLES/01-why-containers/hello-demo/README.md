# hello-demo

Topic 14-01: why containers exist. Run:

```bash
./demo.sh
```

What each check proves:

1. `Hello.java` is compiled with JDK 21 — inside a container, so you don't need JDK 21 installed.
2. The compiled class is run on a "server" with Java 17 (`eclipse-temurin:17-jre`) and fails with
   `UnsupportedClassVersionError`. Same code, different machine, broken: "works on my machine".
3. `Dockerfile` packages the class with a Java 21 runtime into one image. The image runs anywhere Docker runs,
   and inside it the OS is Linux whatever your host is.
4. Starting, running, and stopping the container takes about a second: there is no operating system to boot.

First run downloads `eclipse-temurin:21-jdk`, `eclipse-temurin:21-jre`, and `eclipse-temurin:17-jre`
(`docker images` lists them at roughly 450–750 MB each on this machine). After that the demo takes a few seconds.
The script removes the image and the compiled classes when it finishes.
