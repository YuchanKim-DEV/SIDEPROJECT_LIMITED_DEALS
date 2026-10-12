# Architecture

Expected to change. If the code or the user's decision differs from this file, tell the user and ask before updating.

## Layers
`controller` → `service` → `repository` (Spring Data JPA) → `domain` (entities), with `dto` classes at the API boundary.
Do not expose entities in controller responses.

## Current code
- A `Member` vertical slice: `MemberController`, `MemberService`, `MemberRepository`, `Member`, `MemberCreateRequest`, `MemberCreateResponse`.
- Tests: `MemberRepositoryTest`, `LimitedDealProductsApplicationTests`.
- Packages today are by layer under `com.limiteddealproducts`.

## Not decided yet
- Package structure: by layer vs by feature.
- Exception handling approach (planned next topic).
- First domain beyond `Member`: `Product` and `Deal` are candidates.
- Database (H2 for local first, PostgreSQL later).
- Test strategy: kinds of tests and tools.

## Planned learning order
layered CRUD → exception handling → JPA associations/N+1 → transactions → Spring Security/JWT → tests → query/index tuning.
