# Java + Spring Boot Backend Engineering Roadmap

A free, structured curriculum for learning Java backend engineering — from "I've never written code" to "I can build a Spring Boot service."

This is a **learning roadmap**, not a course you buy and not a boot camp. It's a sequence of topics, real code examples you can run, common-mistake catalogs, and self-check questions. Work through it at your own pace.

---

## Honest scope

This repo teaches the **concepts, vocabulary, and patterns** of Java backend engineering. Working through it will:

- ✅ Give you the mental model of Java, OOP, JVM, JDBC, Spring Boot, REST APIs, microservices, observability.
- ✅ Let you read other people's Java backend code and understand what's happening.
- ✅ Prepare you for backend engineering interviews.
- ✅ Get you ready to build your own projects.

What it **won't** do on its own:

- ❌ Make you "hireable" without you also building real projects.
- ❌ Cover cloud (AWS, GCP, deployment) — see "What's deliberately not in this repo" below.
- ❌ Replace ~50–100 hours of debugging your own code in real apps.

Be skeptical of any free or paid resource that claims to do those last three. Real proficiency comes from this curriculum **plus** shipping things. The repo gives you the map; you still have to walk the territory.

---

## Start here

### If you're a total beginner (no coding experience)

Start at **`01-java-fundamentals/`**. Expect ~6–9 months at 10 hrs/week.

Before you start, install the JDK and IntelliJ — see [`SETUP.md`](SETUP.md).

### If you already code in another language (frontend, data, QA, devops...)

You can skim modules 01–02 quickly. Start seriously at **`03a-java-language-depth/`** and work through 03a → 03b → 03c → 03d to ground yourself in Java's type system, functional features, concurrency, and JVM model, then continue. Expect ~3–4 months at 10 hrs/week.

### If you know Java but not backend specifically

Start at **`04-jdbc-and-database/`**. This is where backend-specific thinking begins (persistence, transactions, connection pools). Expect ~2–3 months at 10 hrs/week.

---

## How to use a module

Every module has the same shape:

```
NN-module-name/
├── README.md              # what this module is, prerequisites, checkpoint
├── NN-topic-name.md       # one markdown per topic, in study order
├── COMMON_MISTAKES.md     # real bugs (not typos), with code
├── INTERVIEW_QNA.md       # interview questions with real answers
└── CODE_EXAMPLES/
    └── NN-topic-name/
        └── *.java         # runnable code examples
```

For each topic:

1. Read the markdown.
2. Run the first code example. Read it. Modify it.
3. Run the next example. Compare.
4. Do the "Try this yourself" exercise at the end of the markdown.
5. Answer the "Self-check" questions out loud, in your own words. If you can't, re-read the topic.

Each module ends with a **checkpoint** — a handful of questions you should be able to answer before moving on. If you can't, don't move on; the next module assumes you can.

---

## Roadmap

```
01-java-fundamentals                          ← ✓ done
02-oops-and-design-principles                 ← ✓ done
03a-java-language-depth                       ← ✓ done   (exceptions, generics, collections)
03b-modern-java-and-functional                ← ✓ done   (lambdas, streams, Optional)
03c-concurrency                               ← ✓ done   (threads, sync, executors, futures)
03d-jvm-and-runtime                           ← ✓ done   (memory, GC, reflection, networking, production)
04-jdbc-and-database                          ← ✓ done (reference template)
05-maven-and-gradle                           ← ✓ done
06-unit-testing                               ← ✓ done   (JUnit 5, AssertJ, Mockito, JaCoCo)
07-spring-core                                ← ✓ done   (IoC, DI, beans, scopes, AOP, events)
08-spring-boot                                ← ✓ done   (auto-config, properties, profiles, fat jar, embedded Tomcat, Actuator)
09-rest-api-development                       ← ✓ done   (controllers, JSON, status codes, MockMvc, OpenAPI, versioning)
10-spring-data-jpa                            ← ✓ done   (entities, repositories, queries, fetch, transactions, Flyway)
11-exception-handling-and-validation          ← ✓ done   (advice, ProblemDetail/RFC 7807, bean validation, custom constraints)
12-security                                   ← ✓ done   (auth vs authz, SecurityFilterChain, BCrypt, sessions vs JWT, OAuth2 resource server + login, CORS/CSRF, 401/403 ProblemDetail, spring-security-test)
13-microservices
14-docker-and-devops
15-system-design
16-design-patterns                            (applied patterns: the Spring/GoF idioms from 07–12, made explicit)
17-kafka-and-event-driven
18-redis-caching
19-observability-and-monitoring
20-projects                                   (capstone: build something end-to-end)
```

**Where the core ends.** Modules 01–12 are the spine: finish them and you can build, secure, and test a real Spring Boot REST service — the stated goal of this roadmap. Modules 13–19 add breadth for working on larger systems (distributed services, infrastructure, event streaming, caching, observability); they're worth doing but sit *beyond* the core, and module 20 is a capstone where you build something end-to-end. Design patterns land at 16 deliberately — by then you've used the patterns Spring applies everywhere (dependency injection, template methods, proxies, strategies), so the module names and generalizes what you've already met rather than teaching it in the abstract.

**Module 04 is the current reference implementation** of the curriculum's style and depth: runnable code examples (H2 in-memory database), vulnerable-then-safe contrasts for security topics, broken-then-fixed contrasts for resource management, real exception handling. Module 03 (split into 03a-03d to keep each sub-module walkable) is the newest application of that template.

**About the 03a-03d split.** The original "Java Advanced" module grew to 32 topics — too much to walk as one. It's split into four sub-modules along concept boundaries: 03a (language depth), 03b (modern Java), 03c (concurrency), 03d (JVM & runtime). Read in order — 03c uses lambdas from 03b, 03d assumes concurrency vocabulary from 03c.

---

## What's deliberately *not* in this repo

So you don't have to wonder. These are out of scope on purpose, so the roadmap stays finishable for one person:

- **Cloud (AWS, GCP, Azure).** Deployment, infrastructure, IAM, managed services. Real backend jobs expect this — plan to learn it from a different resource after (or alongside) this one.
- **Real deployed projects.** This repo teaches concepts. Building and shipping side projects is your job, in parallel. Pick something small (URL shortener, todo API, expense tracker) by the time you finish module 09 (REST APIs).
- **Frontend, mobile, CSS, JavaScript, React.** This is a backend roadmap. Frontend is a different career path with its own curriculum.
- **Machine learning, data engineering.** Different fields.
- **CI/CD pipelines, Kubernetes, advanced DevOps.** Touched on in module 14 but not deep. Real DevOps is its own roadmap.

You don't need any of these to *follow this roadmap*. You'll need most of them to *be a complete backend engineer*. Knowing the boundary is freeing — you can focus on what's in this repo without feeling like you're missing something secret.

---

## Setup

One time, when you start: [`SETUP.md`](SETUP.md) — JDK, IDE, and how to run the code examples.

You don't need anything else installed until you reach module 04 (which adds an H2 database jar, instructions in `SETUP.md`).

---

## Philosophy

A few principles that shape every page:

- **Honest depth over apparent breadth.** A topic that genuinely teaches you `PreparedStatement` matters more than 20 topics that just list its name.
- **Show the failure mode, not just the fix.** Real understanding comes from "I saw what goes wrong" not "I memorized the rule." Security and concurrency topics deliberately include broken code so you can run it, see the failure, then run the fixed version.
- **Code that runs.** Every Java file under `CODE_EXAMPLES/` is meant to execute. No `println` simulations pretending to be JDBC.
- **No filler.** No "Very important backend engineering concept" lines. If a sentence doesn't carry information, it's deleted.
- **Calmly bounded.** The roadmap is big but finite. The boundary makes it walkable.

---

## Contributing / feedback

This is a personal project. If you find an error or have a suggestion, open an issue. If you're walking the roadmap and have feedback on what's confusing, that's the most useful thing to report — content quality matters more than coverage.

---

## What's next after this roadmap

Once you finish, you'll know enough Java backend engineering to:

- Build a Spring Boot REST API end-to-end.
- Understand a typical Java backend codebase well enough to contribute.
- Pass the technical-screen of most junior-to-mid backend interviews.

You'll **still** need to:

- Learn one cloud provider (separate roadmap).
- Ship 1–2 real projects, deployed publicly.
- Read other people's production code (open-source Spring Boot apps on GitHub are good for this).
- Get used to debugging the unfamiliar — incidents, slow queries, mysterious null pointer exceptions in code you didn't write.

This roadmap is the **structured part**. The unstructured part — building, breaking, fixing — is the rest of your career.

Onwards.
