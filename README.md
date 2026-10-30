# LoungeAccessSystem

Airport Lounge Access Verification & Analytics System - Spring Boot 3.2, Java 17, H2, JPA, MapStruct, Lombok, OpenAPI.

## Run
```bash
mvn clean verify          # unit tests + context smoke test
mvn spring-boot:run
```
* Swagger UI: http://localhost:8080/swagger-ui.html
* H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:loungedb`, user `sa`, empty password)
* API auth (HTTP Basic): `reception` / `lounge@123` (see `lounge.security.*` in `application.yml`)

## Demo data (printed in the log at start-up)
| customerId | Card number      | Scenario |
|-----------:|------------------|----------|
| 1 | 4147200000000011 | Axis ACE, 62,000 spend -> eligible |
| 2 | 4147200000000029 | Axis ACE, new card exception -> eligible |
| 3 | 4147200000000037 | Axis ACE, only 20,000 qualifying -> INSUFFICIENT_SPEND |
| 4 | 4386100000000013 | HDFC Platinum Debit; voucher `482915037261` eligible, `719304286150` EXPIRED_VOUCHER |
| 5 | 4386100000000021 | HDFC Platinum Debit, voucher `530918274466`, INSUFFICIENT_SPEND |
| 6 | 4386200000000019 | HDFC Infinia -> unlimited (`visitsRemaining = -1`) |
| 7 | 5241900000000012 | HDFC Millennia -> QUOTA_EXCEEDED |
| 8 | 5241910000000010 | HDFC Regalia Gold -> eligible, 3 visits / quarter |
| 9 | 6521000000000012 | ICICI Coral (generic strategy) -> eligible, 1 visit left |
| 10 | 4000100000000018 | Kotak debit -> UNSUPPORTED_CARD |

## Try it
```bash
curl -u reception:lounge@123 -H 'Content-Type: application/json' \
  -X POST localhost:8080/api/v1/lounge/check-eligibility \
  -d '{"cardNumber":"4386100000000013","customerId":4,"loungeId":"HYD-T1-ENCORE","voucherCode":"482915037261"}'

curl -u reception:lounge@123 -H 'Content-Type: application/json' \
  -X POST localhost:8080/api/v1/lounge/redeem-pass \
  -d '{"passToken":"<loungePassToken from above>","loungeId":"HYD-T1-ENCORE","terminalId":"T-01"}'

curl -u reception:lounge@123 'localhost:8080/api/v1/analytics/summary?hours=24'
```

## Design notes
* **Strategy pattern**: `LoungeEligibilityEngine` picks the lowest-`priority()` `EligibilityStrategy` that `supports(card)`:
  Axis (10), HDFC debit / HDFC credit (20), Generic fallback (1000, banks listed in `lounge.generic.supported-banks`).
  No match -> `UNSUPPORTED_CARD`. Add a bank by adding one `@Component`.
* **Windows**: Axis = preceding 3 full calendar months; HDFC/generic = previous calendar quarter; quota = current calendar quarter.
  Cash withdrawals, wallet reloads and fuel never count.
* **Axis new-card exception**: card issued in month M is waived through the end of month M+3.
* **Quota**: a visit consumes quota when the pass is issued; unredeemed passes that expire (`lounge.pass.validity-minutes`)
  release it. Re-presenting a card with a live pass for the same lounge returns that pass (idempotent).
* **Concurrency**: the card row is locked (`PESSIMISTIC_WRITE`) during evaluation so parallel taps cannot over-consume quota.
* **PII**: PANs are never stored or logged - only an HMAC-SHA256 hash (`lounge.security.card-hash-pepper`), BIN and last 4.
* `visitsRemaining = -1` means unlimited.
* Before production: replace H2 with a real database + Flyway, externalise secrets, and swap Basic auth for your IdP.

## Postman
`postman/` contains an importable collection (43 requests with test scripts and step-by-step descriptions), a local
environment and `TESTING_GUIDE.md`. Start the app fresh, import both JSON files and run the collection in order.
