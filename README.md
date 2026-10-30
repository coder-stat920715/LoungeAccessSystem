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
`postman/` contains an importable collection (62 requests with test scripts and step-by-step descriptions, including the statement-analysis scenarios), a local
environment and `TESTING_GUIDE.md`. Start the app fresh, import both JSON files and run the collection in order.

## Statement analysis API - "Will I get lounge access next month?"
`POST /api/v1/statements/analyze` (multipart/form-data, HTTP Basic). Attach your last 3 months of card statements
(CSV, TXT or PDF; up to 6 files, 5 MB each) and get a forecast for a target month (default: next month).

| Form field | Required | Notes |
|---|---|---|
| `statements` | yes | repeat the field per file |
| `bank` | yes | `AXIS`, `HDFC`, `ICICI`, `SBI`, `KOTAK` |
| `productCode` | yes | e.g. `ACE`, `MILLENNIA`, `REGALIA_GOLD`, `INFINIA`, `PLATINUM_DEBIT` |
| `cardType` | no | `CREDIT` (default) / `DEBIT` |
| `targetMonth` | no | `yyyy-MM`, default = next month (e.g. `2026-11`) |
| `cardIssueDate` | no | `yyyy-MM-dd`, enables the Axis new-card exception |
| `visitsUsedInTargetQuarter` | no | visits already used in that calendar quarter (default 0) |
| `pdfPassword` | no | for protected PDFs; never stored or logged |

```bash
curl -u reception:lounge@123 http://localhost:8080/api/v1/statements/analyze \
  -F statements=@samples/statements/axis_ace_eligible_aug_2026.csv \
  -F statements=@samples/statements/axis_ace_eligible_sep_2026.csv \
  -F statements=@samples/statements/axis_ace_eligible_oct_2026.csv \
  -F bank=AXIS -F productCode=ACE -F targetMonth=2026-11
```

**What it does**
1. Parses the files in memory (nothing is stored), groups transactions by calendar month, de-duplicates transactions that appear in overlapping files, and categorises each one (shopping, dining, travel, grocery, utilities, ...).
2. Excludes cash withdrawals, wallet reloads, fuel and fees/interest/GST; payments and refunds are credits, never spend.
3. Applies the bank rule for the target month - Axis: preceding 3 calendar months; HDFC / generic: previous calendar quarter - plus the quota and the Axis new-card exception.
4. Returns `status` (`ELIGIBLE`, `ELIGIBLE_NEW_CARD_EXCEPTION`, `QUOTA_EXHAUSTED`, `NOT_ELIGIBLE`, `NOT_SUPPORTED`), the spend vs threshold, month / category / merchant analysis, what was excluded and why, warnings (missing months, partial coverage, refunds, unreadable lines) and `recommendations`.
5. `planForUpcomingMonths` answers "what do I do now": the first month whose window includes the target month's spend (December for Axis; January for HDFC quarterly rules), spend already banked, the additional qualifying spend still needed, a per-month / per-week target, and which month drops out of the window.

Worked example (Axis ACE, `targetMonth=2026-11`): the *eligible* sample set has 66,500 qualifying spend vs 50,000 -> eligible, and 5,000 more in November keeps December eligible (August's 21,500 drops out). The *not_eligible* set has 30,800 (36,000 went on fuel/wallet/cash) -> not eligible in November; 29,000 of qualifying spend in November makes December eligible (Sep 12,400 + Oct 8,600 + Nov 29,000 = 50,000).

**Statement formats**: CSV needs a header row with a date, a description/transaction-details column and either one amount column (with `Dr/Cr`, a sign, or separate type column) or separate Debit/Credit columns; preamble lines before the table are ignored. TXT / PDF text lines are read as `date description amount [Dr|Cr]`. PDF text extraction is best-effort because every bank lays PDFs out differently - check `files[].transactionsParsed` / `linesSkipped` and the `warnings`, and prefer the CSV export when in doubt.

**Limits of the forecast**: it only knows the statements you upload (missing months are flagged), refunds are reported but not netted off, category detection is keyword based (unknown merchants count as qualifying), and the bank's ledger / the desk check remain authoritative.
