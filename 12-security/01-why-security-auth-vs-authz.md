# Why security — authentication vs. authorization

Every API you have built in this roadmap so far trusts whoever calls it. This topic names what that trust actually costs, and splits "security" into the two separate questions the rest of the module answers: **authentication** — who is this caller? — and **authorization** — is this caller allowed to do this? They are different questions with different failure modes and different status codes, and conflating them is the first mistake. This topic has no demo; you'll wire the machinery starting in topic 02. Here we set the threat model and the goal.

---

## The problem this solves

Take the `Book` API from module 11. It has these endpoints:

```
GET    /api/books          list all books
GET    /api/books/{id}     one book
POST   /api/books          create a book
PUT    /api/books/{id}     update a book
DELETE /api/books/{id}     delete a book
```

Right now — with zero security — every one of these is reachable by anyone who can reach the server. If it's deployed to a public URL, "anyone" means the entire internet, including bots that scan every public IP for open endpoints within minutes of deployment. Concretely:

- Anyone can `GET /api/books` and scrape your whole catalog.
- Anyone can `POST /api/books` and fill your database with garbage.
- Anyone can `DELETE /api/books/42` and it's gone.

Nothing in modules 09–11 stops this. Validation (module 11) rejects a book with a blank title — but it happily deletes a valid record for a caller who should never have been allowed to delete anything. Validation checks *the request*; it never checks *the requester*. That is the gap this module closes.

There are actually **two** distinct gaps here, and it's worth separating them before you write any code:

1. **The API doesn't know who is calling.** There's no login, no token, no identity at all. Every request is anonymous. This is missing **authentication**.
2. **Even if it knew, it wouldn't restrict anything by identity.** If you bolted on a login but then let any logged-in user delete any book, you'd have authentication but no **authorization** — a normal user could do admin things.

A real breach usually exploits one specific gap, so you have to be able to name which one.

---

## How it works — the two questions

### Authentication: *who are you?*

Authentication is establishing identity — turning an anonymous request into "this request is from user `alice`." It's answered by a **credential**, something that proves you are who you claim. Credentials come in three classic factors:

- **Something you know** — a password, a PIN.
- **Something you have** — a phone that receives a code, a hardware key, a signed token.
- **Something you are** — a fingerprint, a face scan.

Combining two of these is multi-factor authentication (MFA). This module focuses on the first factor (passwords, topic 05) and the "something you have" factor in the form of tokens (JWT, topics 08–10).

The output of authentication is an **identity** the rest of the system can rely on. In Spring Security this is an `Authentication` object holding the user's name and their granted authorities. Everything downstream — every authorization decision — reads from it.

### Authorization: *what may you do?*

Authorization happens **after** authentication and depends on its result. It's the decision: given that we now know you are `alice`, are you permitted to perform *this specific action* on *this specific resource*? It's expressed with **roles** and **authorities** — `alice` has `ROLE_USER`, so she can read books; the delete endpoint requires `ROLE_ADMIN`, which she lacks, so she's refused.

The ordering is strict and it matters: **you cannot authorize an unknown caller.** Authentication first (who are you), authorization second (what may you do). A request that fails authentication never reaches the authorization check.

### The two failures map to two status codes

This is exactly the `401` vs. `403` distinction module 09 named but couldn't produce yet:

| Status | Name | Meaning | Cause |
|--------|------|---------|-------|
| **401** | Unauthorized *(misnamed — it means unauthenticated)* | "I don't know who you are." | No credentials, or invalid credentials. Authentication failed. |
| **403** | Forbidden | "I know who you are, and you may not do this." | Valid identity, but insufficient permission. Authorization failed. |

The name of `401` is one of HTTP's oldest mistakes: it says "Unauthorized" but it fires when *authentication* is missing, not authorization. Read it as "unauthenticated." A worked distinction:

- `DELETE /api/books/42` with **no token** → **401**. The server has no idea who you are. The fix on the client side is *log in*.
- `DELETE /api/books/42` as **`alice` (`ROLE_USER`)** → **403**. The server knows exactly who you are; you're simply not an admin. Logging in again changes nothing — `alice` will *never* be allowed. The fix is *be someone with permission*, not *authenticate harder*.

Getting these two backwards leaks information (a `403` on a resource that exists but you can't see tells an attacker the resource exists) and confuses clients (a `401` tells a client to re-authenticate; sending it when the real problem is permission sends them into a pointless login loop).

---

## The threat model — what "open" actually exposes

Security work starts by naming what you're protecting against. For a typical backend API, the concrete threats an unsecured endpoint invites:

- **Unauthorized data access.** Anyone reads data they shouldn't — other users' records, internal fields, the full dataset by scraping paginated endpoints.
- **Unauthorized modification.** Anyone writes, updates, or deletes. This is worse than reads because it corrupts state other users depend on.
- **Privilege escalation.** A low-privilege user reaches high-privilege actions — the exact failure of "authenticated but not authorized." The classic form is an admin endpoint that checks *that* you're logged in but not *whether* you're an admin.
- **Credential compromise.** If you store passwords badly (topic 05), a database leak hands the attacker not just your users' accounts but their accounts *elsewhere*, because people reuse passwords. Your breach becomes their bank's breach.

You will not defend against all threats — no system does. The goal is to raise the cost of an attack above the value of the target, and to fail *safe* (deny by default) rather than *open* (allow by default). Spring Security's defaults, which you'll meet in topic 02, are deny-by-default for exactly this reason: the moment you add it, *everything* locks, and you deliberately open only what should be public.

---

## The goal of this module — a secured API

By the end you'll take the open `Book` API above and put identity and permission in front of it: public routes stay open, reads require a logged-in user, and writes require the right role — enforced by a `SecurityFilterChain` you understand line by line, with credentials stored the way production systems store them, and security failures returning the same clean `ProblemDetail` shape as module 11's validation errors.

The pieces, in the order the module builds them:

| Topics | Concern | What you'll be able to do |
|--------|---------|---------------------------|
| 02–03 | The mechanism | See what the starter locks by default, then write the `SecurityFilterChain` that takes control. |
| 04–06 | Authentication | Model identity, store passwords safely, choose a login style (Basic / form / stateless). |
| 07 | Authorization | Restrict by URL and by method; roles vs. authorities. |
| 08–10 | Tokens & delegation | Self-signed JWT → validate an external IdP's JWT → full OAuth2/OIDC login. |
| 11–13 | Getting it right | CORS/CSRF, security errors as `ProblemDetail`, and tests that catch a broken rule. |

---

## Common pitfalls

- **Conflating authentication and authorization.** They fail differently (`401` vs. `403`), they're configured in different places, and a fix for one does nothing for the other. If you can't say which one a given bug is, you can't fix it. Practice naming it on every access failure you see.
- **Security by obscurity.** "Nobody knows the URL `/admin/delete-everything`, so it's fine." An unauthenticated endpoint is unsecured whether or not it's advertised — scanners and leaked logs find it. Obscurity can be one *layer*, never the control.
- **Adding security last.** Treating auth as a feature to bolt on before launch leads to endpoints that were never designed to carry an identity, and to the "authenticated but not authorized" gap. The deny-by-default posture (topic 02) exists so you can't forget an endpoint — it's locked until you say otherwise.
- **Trusting the client.** A hidden button in the UI, a role stored in the browser, a check done only in JavaScript — none of these secure anything. The client is fully under the attacker's control (they can call your API directly with `curl`). Every authorization decision must be enforced on the server, which is the entire point of this module.

---

## Self-check

1. A caller does `DELETE /api/books/42`. In one case they get a `401`, in another a `403`. Describe what's different about the two callers, and what advice you'd give each one to succeed.
2. Your API has a login but a normal user can still hit an admin-only endpoint successfully. Which of the two questions (authentication / authorization) is broken, and why is the other one *not* the problem?
3. Someone says "our internal admin API is secure because its URL isn't documented anywhere." Explain, in terms of the threat model, why that isn't security — and name what would make it secure.
</content>
