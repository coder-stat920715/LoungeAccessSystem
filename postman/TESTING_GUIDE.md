# Airport Lounge Access System - API test collection

## Setup
1. Start the app from a **fresh JVM**: `mvn spring-boot:run` (H2 is in-memory, demo data is re-seeded on every start).
2. Postman -> **Import** `LoungeAccessSystem.postman_collection.json` and `LoungeAccessSystem.postman_environment.json`; select the *LoungeAccessSystem - Local* environment.
3. Credentials are HTTP Basic `reception` / `lounge@123` (collection-level auth, change in the environment if you changed `lounge.security.*`).

## Running
* **Collection Runner** (recommended): run all folders in order, delay 0 ms, *Persist variables* off. Every request has tests; the whole run should be green.
* **Manual**: open a request, read its *Description* (purpose, preconditions, steps, expected result), press **Send**, check **Test Results**.
* **Folder 09 uploads files** from `samples/statements/`. Set Postman *Settings -> General -> Working directory* to the project root (or re-select the files in each request's Body tab). Newman: `newman run postman/LoungeAccessSystem.postman_collection.json -e postman/LoungeAccessSystem.postman_environment.json --working-dir .`. Folder 09 is stateless and can run on its own, in any order, any number of times.
* Folders 00-08 are order-dependent: tests store pass tokens in collection variables (`passToken_*`) and later folders consume them, and approvals consume quota/vouchers.
* **Re-running** the whole collection needs an app restart (quota, vouchers and passes are real state). Before restarting, you can also reset the collection variables `passToken_*`.

## Coverage map
| Folder | Verifies |
|---|---|
| 00 | App up, OpenAPI public, 401 without/with wrong credentials |
| 01 | Axis 3-month spend rule, exclusions (fuel/cash/wallet), new-card exception, idempotent re-issue |
| 02 | HDFC debit: spend + voucher (missing / malformed / unknown / other card / used / expired / valid), single-use voucher |
| 03 | HDFC credit tiers: Infinia unlimited, Millennia quota, Regalia Gold approve |
| 04 | Generic fallback (ICICI) and UNSUPPORTED_CARD (Kotak) |
| 05 | BIN lookup, CARD_NOT_FOUND, CUSTOMER_MISMATCH, bean validation, malformed JSON |
| 06 | Full quota exhaustion on a 3-visit card |
| 07 | Redeem happy path, double redeem, wrong lounge, unknown token, validation |
| 08 | Analytics: totals, rates, denial breakdown, banks, tiers, peak hours, parameter validation |
| 09 | **Statement analysis**: upload Aug/Sep/Oct statements -> eligibility for Nov, why / why not, and the spend needed in Nov to qualify for Dec (Axis rolling window, HDFC quarterly window, new-card exception, quota, missing months, duplicates, text/PDF, validation, security) |

## Manual-only scenarios (not automatable in one run)
* **Password-protected PDF statement**: attach the PDF as a `statements` file and add the text field `pdfPassword`. A wrong/missing password returns 400 `...is password protected`. PDF text extraction is best-effort (layouts differ per bank); the response lists `files[].transactionsParsed` and `linesSkipped` so you can verify the parse, and CSV is always the most reliable input.
* **Default target month**: omit `targetMonth` and the API evaluates *next month* relative to the server clock (Asia/Kolkata).
* **Expired pass (HTTP 410)**: start with `--lounge.pass.validity-minutes=1`, run 1.1, wait 61 seconds, run 7.4 -> 410 Gone with *Pass expired*. The quota is released automatically (re-run 1.1 -> new pass, `visitsRemaining` back to 1).
* **Blocked / expired card (CARD_INACTIVE)**: in the H2 console (`/h2-console`, JDBC URL `jdbc:h2:mem:loungedb`, user `sa`) run `UPDATE cards SET active = FALSE WHERE last4 = '0011';` then run 1.1 -> `CARD_INACTIVE`.
* **Quarter / month boundaries**: demo data is generated relative to today. To test boundaries, insert rows into `spend_history` with dates on the first/last day of the window and re-run the relevant request.
* **Concurrency**: fire request 6.1 twice in parallel (Runner with iterations is sequential; use two Postman windows or `curl &`) - the card row lock guarantees quota is never over-consumed.

## Test-data cheat sheet
| customerId | PAN | Scenario |
|---:|---|---|
| 1 | 4147200000000011 | Axis ACE qualifying |
| 2 | 4147200000000029 | Axis ACE new card |
| 3 | 4147200000000037 | Axis ACE low spend |
| 4 | 4386100000000013 | HDFC Platinum Debit (vouchers 482915037261 valid, 719304286150 expired, 260481937502 used) |
| 5 | 4386100000000021 | HDFC Platinum Debit low spend (voucher 530918274466) |
| 6 | 4386200000000019 | HDFC Infinia |
| 7 | 5241900000000012 | HDFC Millennia, quota used |
| 8 | 5241910000000010 | HDFC Regalia Gold |
| 9 | 6521000000000012 | ICICI Coral |
| 10 | 4000100000000018 | Kotak (unsupported) |
