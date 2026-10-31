<div align="center">

# ✈️ LoungeAccessSystem

**Airport Lounge Access Verification & Analytics System**

Spend-linked eligibility at the reception desk · Bank-specific rule strategies · Digital voucher validation · 3-month statement analysis with gap-closing guidance

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=springboot&logoColor=white)
![Build](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)
![Database](https://img.shields.io/badge/Database-H2%20(in--memory)-1021FF)
![API Docs](https://img.shields.io/badge/API%20Docs-OpenAPI%203%20%2F%20Swagger%20UI-85EA2D?logo=swagger&logoColor=black)
![Postman](https://img.shields.io/badge/Postman-62%20requests-FF6C37?logo=postman&logoColor=white)

</div>

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Core Capabilities & Business Logic](#2-core-capabilities--business-logic)
   - [2.1 Desk-time eligibility: Strategy Pattern for bank rules](#21-desk-time-eligibility-strategy-pattern-for-bank-rules)
   - [2.2 Digital voucher validation](#22-digital-voucher-validation)
   - [2.3 Quota, idempotency and concurrency](#23-quota-idempotency-and-concurrency)
   - [2.4 Statement parsing & monthly eligibility analysis](#24-statement-parsing--monthly-eligibility-analysis)
   - [2.5 Gap analysis: how to qualify next month](#25-gap-analysis-how-to-qualify-next-month)
3. [System Architecture & Design](#3-system-architecture--design)
4. [REST API Documentation](#4-rest-api-documentation)
5. [Getting Started & Execution Guide](#5-getting-started--execution-guide)
6. [Postman Collection & Testing](#6-postman-collection--testing)
7. [Configuration Reference](#7-configuration-reference)
8. [Security & Production Readiness](#8-security--production-readiness)
9. [Known Limitations & Roadmap](#9-known-limitations--roadmap)

---

## 1. Executive Summary

**LoungeAccessSystem** decides, in real time, whether a cardholder may enter an airport lounge. It evaluates the card's bank and product rules, the customer's spend history, the quarterly visit quota and (for debit cards) a single-use digital voucher, then records every attempt - approved or denied - with a machine-readable reason code and issues a lounge pass on approval. A second capability lets a customer **upload their last three monthly card statements** and learn whether they will be eligible *next month* - and, if not, exactly how much qualifying spend is needed, and when, to become eligible.

Built with **Spring Boot 3.2 / Java 17**, Spring Data JPA on an in-memory H2 database, MapStruct, Lombok and OpenAPI.

### Key features

| | Feature | What it gives you |
|---|---|---|
| 🧮 | **Spend-Linked Eligibility Engine** | Rolling-window and quarterly spend thresholds evaluated from transaction history; cash, wallet reloads and fuel never count |
| 🏦 | **Bank Strategy Pattern** | One isolated, prioritised `EligibilityStrategy` per bank/product (Axis ACE, HDFC Debit, HDFC Millennia / Regalia Gold / Infinia / Diners Black, generic fallback). Add a bank by adding one `@Component` |
| 🎟️ | **Digital Voucher Validation** | 12-digit, single-use, card-bound vouchers with expiry; consumed only when entry is approved |
| 📄 | **3-Month Statement Analysis API** | Upload CSV / TXT / PDF statements → categorised spend analysis, eligibility verdict for the target month, and a step-by-step plan for the following months |
| 📊 | **Real-time Analytics** | Approval vs rejection rate, denials by reason, distribution by bank and card tier, peak entry hours |
| 🔐 | **Security by design** | PANs are never stored (HMAC-SHA256 hash + BIN + last 4), HTTP Basic on every API, RFC 7807 error bodies, row-level locking against quota races |

---

## 2. Core Capabilities & Business Logic

### 2.1 Desk-time eligibility: Strategy Pattern for bank rules

Rules are **decoupled per bank** behind one interface. The engine never contains `if (bank == AXIS)` logic; it asks each strategy whether it `supports(card)` and delegates to the first match in priority order.

```java
public interface EligibilityStrategy {
    String name();
    boolean supports(Card card);
    EligibilityDecision evaluate(EligibilityContext context);
    default int priority() { return 100; }   // lower = evaluated first
}
```

| Strategy (priority) | Applies to | Spend rule | Visits per quarter | Extra conditions |
|---|---|---|---|---|
| `AxisBankStrategy` (10) | Any Axis card, e.g. **ACE** | **≥ ₹50,000** qualifying spend in the **preceding 3 full calendar months** | 2 | **New-card exception**: spend requirement waived from issue until the end of *issue month + 3 months* |
| `HdfcDebitCardStrategy` (20) | HDFC debit | **≥ ₹10,000** in the **previous calendar quarter** | 2 | **Valid 12-digit voucher is mandatory** (generated before entry) |
| `HdfcCreditCardStrategy` (20) | HDFC credit - **Millennia** | **≥ ₹1,00,000** in the previous calendar quarter | **1** | - |
| | **Regalia Gold** | **≥ ₹60,000** in the previous calendar quarter | **3** | - |
| | **Infinia / Diners Black** | **₹0** (no threshold) | **Unlimited** | - |
| `GenericEligibilityStrategy` (1000) | Banks listed in `lounge.generic.supported-banks` (default ICICI, SBI) | **≥ ₹25,000** in the previous calendar quarter | 2 | Fallback strategy |
| *(no strategy matches)* | e.g. Kotak, unknown HDFC product | - | - | Denied with `UNSUPPORTED_CARD` |

#### Date windows (evaluated relative to the day of the visit)

```
Visit on 5 Nov 2026
                                                                     quota quarter
  Axis  (rolling 3 months)   [ Aug ][ Sep ][ Oct ] ──► Nov           Q4: Oct-Dec
  HDFC / generic (quarter)   [ Jul ][ Aug ][ Sep ]     Nov  (Q3 = previous quarter)

Visit on 5 Dec 2026
  Axis                              [ Sep ][ Oct ][ Nov ] ──► Dec
  HDFC / generic             [ Jul ][ Aug ][ Sep ]          Dec   (window unchanged)

Visit on 5 Jan 2027
  Axis                              [ Oct ][ Nov ][ Dec ] ──► Jan
  HDFC / generic             [ Oct ][ Nov ][ Dec ]          Jan   (new quarter's window opens)
```

#### Qualifying vs non-qualifying spend

Only **purchases** count. These categories are **always excluded** from every threshold:

| Excluded category | Examples |
|---|---|
| `CASH_WITHDRAWAL` | ATM withdrawals, cash advances |
| `WALLET_RELOAD` | Paytm / Mobikwik / Freecharge top-ups, "add money", gift-card loads |
| `FUEL` | HPCL, BPCL, Indian Oil, Shell, petrol/diesel/CNG |

(The statement-analysis feature additionally excludes `FEES_AND_CHARGES`; see [2.4](#24-statement-parsing--monthly-eligibility-analysis).)

#### Decision pipeline

```
 POST /check-eligibility
          │
          ▼
 resolve card (full PAN → HMAC lookup | 6-8 digit BIN within the customer's cards)
          │  not found ──────────────────────────────────────► DENY CARD_NOT_FOUND
          ▼
 card belongs to customer? ── no ─────────────────────────────► DENY CUSTOMER_MISMATCH
          │
 live pass already issued for this card + lounge? ── yes ─────► return the SAME pass (idempotent)
          │
          ▼
 card active & not expired? ── no ────────────────────────────► DENY CARD_INACTIVE
          │
 strategy supports card? ── no ───────────────────────────────► DENY UNSUPPORTED_CARD
          │
          ▼  strategy.evaluate()
   [voucher check, HDFC debit only] ──► INVALID_VOUCHER / EXPIRED_VOUCHER
          │
   spend in window ≥ threshold? ── no ──────────────────────────► DENY INSUFFICIENT_SPEND
          │  (waived for Axis new-card exception / zero-threshold tiers)
   quota left this quarter? ── no ──────────────────────────────► DENY QUOTA_EXCEEDED
          │
          ▼
 APPROVE → mark voucher used → create LoungeVisit(APPROVED) → return pass token (LP-…)
```

Every attempt (approved **and** denied) is persisted as a `LoungeVisit`, which feeds the analytics endpoints.

#### Denial reason codes

| Code | Meaning |
|---|---|
| `INSUFFICIENT_SPEND` | Qualifying spend in the rule window is below the threshold |
| `EXPIRED_VOUCHER` | Voucher exists, belongs to the card, but its validity has passed |
| `INVALID_VOUCHER` | Missing, malformed, unknown, used, not-yet-active, or issued for another card |
| `QUOTA_EXCEEDED` | All visits of the current calendar quarter are used |
| `UNSUPPORTED_CARD` | No lounge programme is configured for this bank/product |
| `CARD_NOT_FOUND` | PAN/BIN does not resolve to a card |
| `CARD_INACTIVE` | Card is blocked or past its expiry date |
| `CUSTOMER_MISMATCH` | Card exists but belongs to a different customer |

### 2.2 Digital voucher validation

HDFC debit entry requires a **12-digit voucher** that must be generated *before* arrival. Validation order (first failure wins):

1. Format is exactly 12 digits → else `INVALID_VOUCHER`
2. Code exists → else `INVALID_VOUCHER`
3. Issued **for this card** → else `INVALID_VOUCHER`
4. Not already used (**single-use**) → else `INVALID_VOUCHER`
5. Issue time is not in the future → else `INVALID_VOUCHER`
6. Not expired → else `EXPIRED_VOUCHER`

A voucher is **consumed only on approval**: if the attempt is later denied for low spend or exhausted quota, the voucher stays valid.

### 2.3 Quota, idempotency and concurrency

- **Quota is per card per calendar quarter** (Q1 Jan-Mar … Q4 Oct-Dec). A visit consumes quota when the pass is **issued**; an unredeemed pass that **expires** (`lounge.pass.validity-minutes`, default 120) automatically releases it.
- `visitsRemaining` is returned after the current visit; **`-1` means unlimited**.
- **Idempotency**: re-presenting a card that already holds a live pass for the same lounge returns that pass instead of consuming another visit (protects against double taps).
- **Concurrency**: the card row is read with `PESSIMISTIC_WRITE`, serialising parallel taps so quota can never be over-consumed.

### 2.4 Statement parsing & monthly eligibility analysis

`POST /api/v1/statements/analyze` answers: *"Based on my last three statements, will I get lounge access next month?"* 

Example: attach the **August, September and October 2026** statements of an **Axis ACE** credit card and ask about **November 2026**.

```
 statements (CSV / TXT / PDF) ─┐
 bank + productCode + month ───┤
                               ▼
  ┌────────────┐   ┌─────────────┐   ┌────────────────┐   ┌───────────────┐
  │ 1. Ingest  │──►│ 2. Parse    │──►│ 3. Categorise  │──►│ 4. Merge &    │
  │ validate   │   │ CSV / text  │   │ every purchase │   │    de-dupe    │
  │ type, size │   │ / PDF text  │   │ (keyword rules)│   │ across files  │
  └────────────┘   └─────────────┘   └────────────────┘   └───────┬───────┘
                                                                  ▼
  ┌──────────────────┐   ┌────────────────┐   ┌─────────────────────────────┐
  │ 7. Explain       │◄──│ 6. Evaluate    │◄──│ 5. Aggregate by calendar    │
  │ summary, insights│   │ rule for target│   │ month; sum qualifying spend │
  │ recommendations  │   │ month + quota  │   │ inside the rule's window    │
  └──────────────────┘   └───────┬────────┘   └─────────────────────────────┘
                                 ▼
                    8. Gap analysis → plan for upcoming months
```

#### Step 1-2 · Ingestion & parsing

| Input | How it is read |
|---|---|
| **CSV** | Header row is auto-detected (preamble lines such as card number / statement period are ignored). Needs a **date**, a **description / transaction details / narration** column and either **one amount column** (with `Dr/Cr` column, `Dr`/`Cr` suffix, or sign) **or separate Debit / Credit columns**. Delimiters `, ; tab |` are auto-detected; quoted cells and lakh grouping (`1,23,456.00`) are supported |
| **TXT** | A tabular `.txt` is parsed as CSV; free text is parsed line by line as `date description amount [Dr/Cr]` |
| **PDF** | Text is extracted with Apache PDFBox (optional `pdfPassword`), then parsed like TXT. Best-effort - layouts differ per bank |

Dates are **day-first** (`03/08/2026`, `03-Aug-2026`, `2026-08-03` …). Files are processed **in memory only**; contents and passwords are never stored or logged. Limits: up to **6 files**, **5 MB** each.

#### Step 3 · Spend categorisation

Credits are classified first: **payments** ("payment received", "thank you", NEFT…) and **refunds / reversals** ("refund", "reversal", "cashback", "waiver"…) are *never* spend. Every **debit** is then categorised by keywords in the description plus any category column (rule order matters - exclusions are tested first so `PAYTM ADD MONEY` is never counted as shopping):

| Order | Category | Counts toward threshold? | Detected by (examples) |
|:-:|---|:-:|---|
| 1 | `CASH_WITHDRAWAL` | ❌ **No** | `atm`, `cash withdrawal`, `cash advance` |
| 2 | `WALLET_RELOAD` | ❌ **No** | `wallet`, `add money`, `mobikwik`, `freecharge`, `gift card` |
| 3 | `FUEL` | ❌ **No** | `fuel`, `petrol`, `diesel`, `hpcl`, `bpcl`, `indian oil`, `cng`, `petroleum` |
| 4 | `FEES_AND_CHARGES` | ❌ **No** | `annual fee`, `joining fee`, `late fee`, `finance charge`, `interest`, `gst`, `igst`, `cgst`, `sgst` |
| 5 | `TRAVEL` | ✅ Yes | `indigo`, `makemytrip`, `irctc`, `uber`, `hotel`, `airline` |
| 6 | `DINING` | ✅ Yes | `restaurant`, `cafe`, `zomato`, `swiggy`, `starbucks` |
| 7 | `GROCERY` | ✅ Yes | `bigbasket`, `dmart`, `blinkit`, `zepto`, `grocery` |
| 8 | `ENTERTAINMENT` | ✅ Yes | `netflix`, `bookmyshow`, `pvr`, `spotify` |
| 9 | `UTILITIES` | ✅ Yes | `electricity`, `bescom`, `airtel`, `broadband`, `recharge`, `telecom` |
| 10 | `HEALTH` | ✅ Yes | `pharmacy`, `apollo`, `hospital`, `lenskart` |
| 11 | `EDUCATION` / `INSURANCE` | ✅ Yes | `school`, `udemy`, `insurance`, `policybazaar` |
| 12 | `SHOPPING` | ✅ Yes | `amazon`, `flipkart`, `myntra`, `croma`, `retail`, `mall` |
| - | `OTHER` | ✅ Yes | anything unrecognised |

> **Important:** the programme exclusions applied are exactly **cash, wallet reloads, fuel** (the three rules of the lounge programmes) plus **fees / interest / GST** (which are charges, not purchases). **EMI conversions are not detected as a distinct category** - see [Known Limitations](#9-known-limitations--roadmap).

#### Step 4 · Merge & de-duplicate

Statement cycles overlap and files get attached twice. A transaction with the **same date + description + amount + kind** that appears in **more than one file** is counted **once** (a warning reports how many were merged). Months are assigned by **transaction date**, not by file, so statements whose cycles do not align with calendar months still work.

#### Step 5-6 · Evaluating the target month (e.g. November 2026)

The rule for the target month uses the same window as a desk check on the 1st of that month:

| Bank rule | Window used for **November 2026** | Threshold |
|---|---|---|
| Axis (rolling 3 months) | **1 Aug 2026 → 31 Oct 2026** | ₹50,000 |
| HDFC Debit / Regalia Gold / Millennia / generic (previous quarter) | **1 Jul 2026 → 30 Sep 2026** | ₹10,000 / ₹60,000 / ₹1,00,000 / ₹25,000 |

```
qualifyingSpend = Σ  (debits whose category is qualifying and whose date ∈ window)
surplus/shortfall = qualifyingSpend − threshold
```

Verdict (`status`):

| Status | Condition |
|---|---|
| `ELIGIBLE` | `qualifyingSpend ≥ threshold` (or the tier has no threshold) **and** a visit is still available |
| `ELIGIBLE_NEW_CARD_EXCEPTION` | Threshold missed, but `cardIssueDate` places the target month inside *issue month + 3 months* (Axis) |
| `QUOTA_EXHAUSTED` | Spend criteria met but `visitsUsedInTargetQuarter` ≥ visits per quarter |
| `NOT_ELIGIBLE` | Threshold missed and no exception applies |
| `NOT_SUPPORTED` | No lounge programme is configured for the bank / product |

**Data-completeness guard** (`dataComplete`): a window month with no transactions, or statements that begin more than 5 days after the window start / end more than 5 days before the window end, flag the result as *potentially understated* and add warnings. Tiers with no spend requirement (Infinia / Diners Black) skip this check.

**Worked example - Axis ACE, target November 2026** (sample files in `samples/statements/`)

| Month | Qualifying | Fuel | Cash | Wallet | Fees |
|---|--:|--:|--:|--:|--:|
| Aug 2026 | ₹21,500 | ₹2,000 | ₹5,000 | ₹1,500 | - |
| Sep 2026 | ₹26,300 | ₹2,200 | - | ₹1,500 | - |
| Oct 2026 | ₹18,700 | ₹1,800 | ₹3,000 | - | ₹590 |
| **Window total** | **₹66,500** | ₹6,000 | ₹8,000 | ₹3,000 | ₹590 |

`₹66,500 ≥ ₹50,000` → **ELIGIBLE** for November, surplus **₹16,500**; ₹17,590 of spend was excluded.

### 2.5 Gap analysis: how to qualify next month

Because eligibility is a **rolling** window, spend made *in* November cannot change November (its window closed on 31 Oct) - it changes **later** months. The analyzer therefore finds the **first month whose window includes the target month** (the *plan month*, `P`) and computes what is still missing.

#### Algorithm

```
Inputs : T = target month (Nov 2026)      R = threshold (₹50,000)
         W(m) = window months of month m  q(m) = qualifying spend of month m (from statements)

1. Plan month      P = first month after T such that  T ∈ W(P)
                       Axis (rolling)        → Dec 2026   W = Sep, Oct, Nov
                       HDFC / generic (qtr)  → Jan 2027   W = Oct, Nov, Dec  (Dec & Nov windows are closed: Jul-Sep)

2. Completed months   C = { m ∈ W(P) : m < T }      (already in your statements)
   Months to come     F = { m ∈ W(P) : m ≥ T }      (T and beyond - still in your hands)

3. Banked             B = Σ q(m), m ∈ C
4. Still needed       N = max(0, R − B)
5. Per-month target   ⌈ N / |F| ⌉           Per-week target  ⌈ perMonth / 4.33 ⌉
6. Months leaving     L = W(T) \ W(P)       (spend you lose from the window, = Σ q(m), m ∈ L)
7. alreadySecured     = (N == 0)
```

#### Case A - Not eligible (Axis ACE, low-spend sample set)

| Step | Value |
|---|---|
| Window for Nov 2026 | Aug + Sep + Oct = 9,800 + 12,400 + 8,600 = **₹30,800** |
| Result | **NOT_ELIGIBLE** - shortfall **₹19,200** |
| Why | **₹36,000 (53.9 %)** of all card spend was fuel ₹18,000, wallet ₹10,000, cash ₹8,000 - none of it counts |
| Plan month | **Dec 2026** (window Sep + Oct + Nov) |
| Banked (C = Sep, Oct) | 12,400 + 8,600 = **₹21,000** |
| Needed (N) | 50,000 − 21,000 = **₹29,000** in **November** (≈ ₹6,698 / week) |

Generated guidance (abridged):

1. *Not eligible in November 2026: qualifying spend ₹30,800 is ₹19,200 short of ₹50,000. The window is closed, so new spend in November will not change November.*
2. **Step 1** - Make at least **₹29,000** of *qualifying* spend during **November 2026** (≈ ₹6,698 per week) → eligible in **December 2026**.
3. **Step 2** - Use categories that count (your strongest: shopping, dining, grocery); put recurring bills on the card.
4. **Step 3** - Stop wasting spend: ₹36,000 does not count (fuel, wallet, cash) - pay those another way.
5. **Step 4** - On 1 Dec 2026 re-upload statements with `targetMonth=2026-12` to confirm.

#### Case B - Eligible, but at risk (Axis ACE, eligible sample set)

| Step | Value |
|---|---|
| November result | **ELIGIBLE** (₹66,500) |
| Banked for Dec (Sep + Oct) | 26,300 + 18,700 = **₹45,000** |
| Needed in November to stay eligible in December | 50,000 − 45,000 = **₹5,000** |
| Leaving the window | **Aug 2026 (₹21,500)** - eligibility is rolling, not permanent |

#### Case C - Quarter-based card (HDFC Millennia, same statements)

| Step | Value |
|---|---|
| Window for Nov 2026 | **Jul-Sep** → only Aug + Sep present = ₹47,800 vs ₹1,00,000 → **NOT_ELIGIBLE** (July missing → `dataComplete=false`) |
| Dec 2026 | Same Jul-Sep window - **already closed**; spending in November cannot change it |
| Plan month | **Jan 2027** (window Oct + Nov + Dec) |
| Banked (Oct) | ₹18,700 |
| Needed | ₹1,00,000 − 18,700 = **₹81,300** across **Nov + Dec** → **₹40,650 / month** |

#### Other signals returned

- **Warnings**: missing window months, partial statement coverage, unreadable lines, refunds worth ₹X *(reported, not netted)*, merged duplicates.
- **Insights**: month-over-month trend, average monthly qualifying spend, share of spend that never counts, largest qualifying purchase (flags when one purchase makes up > 40 % of qualifying spend), top merchant.
- **Quota**: visits per quarter, visits available, and whether a voucher is required (HDFC debit).

---

## 3. System Architecture & Design

### 3.1 Technical stack

| Layer | Technology |
|---|---|
| Language / Runtime | **Java 17** |
| Framework | **Spring Boot 3.2.5** (Web, Data JPA, Validation, Security) |
| Build | **Maven** |
| Persistence | **Spring Data JPA / Hibernate** on **H2** (in-memory; swap for PostgreSQL/MySQL in production) |
| Mapping & boilerplate | **MapStruct 1.5**, **Lombok** |
| API docs | **springdoc-openapi 2.5** (Swagger UI) |
| Statement parsing | In-house CSV / text parsers, **Apache PDFBox 3.0** for PDF text |
| Testing | **JUnit 5**, **Mockito**, AssertJ, Spring MockMvc / Spring Security Test |

### 3.2 Logical architecture

```
                         ┌─────────────────────────────────────────────┐
  Reception terminal ───►│  Spring Security: stateless HTTP Basic      │
  Customer / web app ───►│                                             │
                         └──────────────────────┬──────────────────────┘
                                                ▼
 ┌─────────────────────────────  controller  ───────────────────────────────────┐
 │ LoungeController    AnalyticsController    StatementController                │
 └──────────┬───────────────────┬───────────────────────┬────────────────────────┘
            ▼                   ▼                       ▼
 ┌──────────────────┐  ┌────────────────┐  ┌──────────────────────────────────────┐
 │ LoungeAccess     │  │ Analytics      │  │ StatementAnalysisService             │
 │ Service          │  │ Service        │  │  ├ CsvStatementParser                │
 │  ├ resolve card  │  │ (aggregations) │  │  ├ TextStatementParser / PdfExtractor│
 │  ├ idempotency   │  └───────┬────────┘  │  ├ TransactionCategorizer            │
 │  ├ persist visit │          │           │  ├ SpendRuleResolver (rule factory)  │
 │  └ issue pass    │          │           │  └ StatementAnalyzer (+ gap analysis)│
 └───────┬──────────┘          │           └──────────────────────────────────────┘
         ▼                     │
 ┌─────────────────────────┐   │
 │ LoungeEligibilityEngine │   │
 │  └─ EligibilityStrategy │   │
 │     ├ AxisBankStrategy  │   │
 │     ├ HdfcDebitCard…    │   │
 │     ├ HdfcCreditCard…   │   │
 │     └ GenericEligib…    │   │
 └───────────┬─────────────┘   │
             ▼                 ▼
 ┌───────────────────────────────────────────────────────────────┐
 │ Spring Data JPA repositories  →  H2 (customers, cards,        │
 │ spend_history, vouchers, lounge_visits)                       │
 └───────────────────────────────────────────────────────────────┘
        Cross-cutting: GlobalExceptionHandler (RFC 7807) · Clock bean · LoungeProperties
```

### 3.3 Package structure

```
LoungeAccessSystem
├── pom.xml
├── README.md
├── postman/                                  # Postman collection, environment, TESTING_GUIDE.md
├── samples/statements/                       # Sample CSV/TXT statements (eligible, not-eligible, invalid)
└── src
    ├── main
    │   ├── java/com/lounge/access
    │   │   ├── LoungeAccessApplication.java
    │   │   ├── config/          # BeanConfig (Clock), SecurityConfig, OpenApiConfig, LoungeProperties
    │   │   ├── controller/      # LoungeController, AnalyticsController, StatementController
    │   │   ├── dto/             # Request/response records + enums (Bank, CardType, DenialReason, …)
    │   │   ├── entity/          # Customer, Card, SpendHistory, Voucher, LoungeVisit
    │   │   ├── exception/       # GlobalExceptionHandler + Pass* exceptions
    │   │   ├── mapper/          # LoungeVisitMapper (MapStruct)
    │   │   ├── repository/      # Spring Data JPA repositories (+ analytics queries)
    │   │   ├── service/
    │   │   │   ├── engine/      # LoungeEligibilityEngine, EligibilityStrategy, bank strategies, HdfcCreditTier
    │   │   │   ├── impl/        # LoungeAccessService, AnalyticsService, StatementAnalysisService
    │   │   │   └── statement/   # Parsers, TransactionCategorizer, SpendRule(+Resolver), StatementAnalyzer
    │   │   └── util/            # DataLoader (demo data), DateRangeUtil, CardNumberUtil/Hasher, InrFormat
    │   └── resources/application.yml
    └── test
        ├── java/…               # Strategy, engine, parser, analyzer, resolver unit tests + MockMvc IT
        └── resources/statements # Sample statements used by tests
```

### 3.4 Design patterns applied

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | `EligibilityStrategy` + `AxisBankStrategy`, `HdfcDebitCardStrategy`, `HdfcCreditCardStrategy`, `GenericEligibilityStrategy`; `LoungeEligibilityEngine` selects by `supports()` + `priority()` | Open/closed: a new bank is one new `@Component`, zero engine changes |
| **Template Method** | `AbstractSpendLinkedStrategy.applyPolicy(...)` | Spend-threshold, quota and message logic is written once; strategies only supply policy numbers |
| **Factory-style resolver** | `SpendRuleResolver.resolve(bank, cardType, product)` builds the right `SpendRule` (window kind, threshold, visits, waiver) from configuration; Spring injects all strategies into the engine | Statement analysis reuses the same rule parameters without instantiating desk strategies |
| **DTO (immutable records)** | `EligibilityRequest/Response`, `RedeemPass*`, `AnalyticsSummaryResponse`, `StatementAnalysisResponse` | Stable API contract, decoupled from JPA entities |
| **Mapper** | MapStruct `LoungeVisitMapper` | Compile-time, reflection-free entity→DTO mapping |
| **Repository** | Spring Data JPA interfaces | Persistence abstraction + locking / aggregation queries |
| **Global exception handling** | `@RestControllerAdvice` extending `ResponseEntityExceptionHandler` → RFC 7807 `ProblemDetail` | Uniform error bodies, no stack-trace leakage |
| **Externalised configuration** | `@ConfigurationProperties` (`LoungeProperties`) | Thresholds, windows and supported banks change without code changes |
| **Dependency injection of a `Clock`** | `BeanConfig` | Deterministic, testable date-window logic |

### 3.5 Domain model

```
Customer 1 ──── * Card 1 ──── * SpendHistory (date, amount, category, merchant)
                   │ 1
                   ├──── * Voucher (code[12], issuedAt, expiresAt, used)
                   └──── * LoungeVisit (status APPROVED|REDEEMED|DENIED, denialReason, passToken,
                                         passExpiresAt, attemptHour, bank/product snapshot)
```

---

## 4. REST API Documentation

**Base URL:** `http://localhost:8080` · **Auth:** HTTP Basic on every `/api/**` call (default `reception` / `lounge@123`) · **Errors:** RFC 7807 `application/problem+json`.

| Method | URI | Purpose |
|---|---|---|
| `POST` | `/api/v1/lounge/check-eligibility` | Real-time card check at the desk; issues a lounge pass |
| `POST` | `/api/v1/lounge/redeem-pass` | Mark a pass as used at the reception terminal |
| `POST` | `/api/v1/statements/analyze` | Upload 3-month statements → eligibility forecast + gap analysis |
| `GET` | `/api/v1/analytics/summary` | Real-time statistics (also `/denials`, `/peak-hours`) |

> **Note on the statement endpoint path.** The implemented route is **`POST /api/v1/statements/analyze`**.

Common headers: `Authorization: Basic <base64(user:password)>`; `Content-Type: application/json` (or `multipart/form-data` for statements); `Accept: application/json`.

---

### 4.1 `POST /api/v1/lounge/check-eligibility`

Evaluates eligibility, **records the attempt** and, on approval, consumes quota/voucher and issues a pass. A processed attempt **always returns HTTP 200** - inspect `eligible` / `denialReason`.

**Request body**

| Field | Type | Required | Notes |
|---|---|:-:|---|
| `cardNumber` | string | ✅ | Full card number **or** a 6-8 digit BIN (resolved among the customer's cards). 6-25 digits; spaces / hyphens allowed |
| `customerId` | long | ✅ | Must own the card |
| `loungeId` | string | ✅ | ≤ 32 chars, normalised to upper case |
| `voucherCode` | string | ⛔ / ✅ | 12-digit voucher - **mandatory for HDFC debit** |

```json
{
  "cardNumber": "4386100000000013",
  "customerId": 4,
  "loungeId": "HYD-T1-ENCORE",
  "voucherCode": "482915037261"
}
```

**200 - approved**

```json
{
  "visitId": 15,
  "eligible": true,
  "reason": "Eligible: qualifying spend ₹14500.00 meets the ₹10000.00 threshold",
  "denialReason": null,
  "visitsRemaining": 1,
  "loungePassToken": "LP-9F3A7C21B8D04E65A1C3",
  "passExpiresAt": "2026-10-29T12:00:00"
}
```

**200 - denied**

```json
{
  "visitId": 16,
  "eligible": false,
  "reason": "Qualifying spend ₹20000.00 between 2026-07-01 and 2026-09-30 is below the required ₹50000.00",
  "denialReason": "INSUFFICIENT_SPEND",
  "visitsRemaining": 2,
  "loungePassToken": null,
  "passExpiresAt": null
}
```

**Other responses:** `400` validation error (`errors` map per field) · `401` unauthenticated.

---

### 4.2 `POST /api/v1/lounge/redeem-pass`

**Request body**

| Field | Type | Required |
|---|---|:-:|
| `passToken` | string (≤ 40) | ✅ |
| `loungeId` | string (≤ 32) | ✅ - must match the lounge the pass was issued for |
| `terminalId` | string (≤ 32) | ⛔ (logged only) |

```json
{ "passToken": "LP-9F3A7C21B8D04E65A1C3", "loungeId": "HYD-T1-ENCORE", "terminalId": "T-01" }
```

**200 - redeemed**

```json
{
  "visitId": 15,
  "passToken": "LP-9F3A7C21B8D04E65A1C3",
  "loungeId": "HYD-T1-ENCORE",
  "status": "REDEEMED",
  "redeemedAt": "2026-10-29T10:12:44.318",
  "message": "Entry completed. Pass redeemed."
}
```

| Status | Condition |
|---|---|
| `404 Not Found` | Unknown token |
| `409 Conflict` | Already redeemed, or pass issued for a different lounge |
| `410 Gone` | Pass expired (quota is released automatically) |
| `400 Bad Request` | Validation failure |

---

### 4.3 `POST /api/v1/statements/analyze`

Upload the last three monthly statements and forecast eligibility for a target month. **`multipart/form-data`.** Nothing is stored.

**Form fields**

| Field | Type | Required | Notes |
|---|---|:-:|---|
| `statements` | file(s) | ✅ | CSV, TXT or PDF; **repeat the field** per file; ≤ 6 files, ≤ 5 MB each |
| `bank` | enum | ✅ | `AXIS`, `HDFC`, `ICICI`, `SBI`, `KOTAK` |
| `productCode` | string | ✅ | `ACE`, `MILLENNIA`, `REGALIA_GOLD`, `INFINIA`, `DINERS_BLACK`, `PLATINUM_DEBIT`, … |
| `cardType` | enum | ⛔ | `CREDIT` (default) / `DEBIT` |
| `targetMonth` | `yyyy-MM` | ⛔ | Default: next month (server clock, `Asia/Kolkata`) |
| `cardIssueDate` | `yyyy-MM-dd` | ⛔ | Enables the Axis new-card exception |
| `visitsUsedInTargetQuarter` | int ≥ 0 | ⛔ | Visits already used in the target month's calendar quarter |
| `pdfPassword` | string | ⛔ | For protected PDFs; never stored or logged |

```bash
curl -u reception:lounge@123 http://localhost:8080/api/v1/statements/analyze \
  -F statements=@samples/statements/axis_ace_eligible_aug_2026.csv \
  -F statements=@samples/statements/axis_ace_eligible_sep_2026.csv \
  -F statements=@samples/statements/axis_ace_eligible_oct_2026.csv \
  -F bank=AXIS -F cardType=CREDIT -F productCode=ACE -F targetMonth=2026-11
```

**200 - eligible (abridged; arrays shortened)**

```json
{
  "bank": "AXIS", "cardType": "CREDIT", "productCode": "ACE", "targetMonth": "2026-11",
  "ruleApplied": "AXIS_BANK",
  "status": "ELIGIBLE",
  "eligibleForTargetMonth": true,
  "summary": "You are eligible for lounge access in November 2026. Qualifying spend between 2026-08-01 and 2026-10-31 is ₹66,500 against the required ₹50,000. 2 visit(s) available this quarter. To stay eligible in December 2026 you need ₹5,000 more qualifying spend.",
  "window": { "start": "2026-08-01", "end": "2026-10-31", "months": ["2026-08", "2026-09", "2026-10"] },
  "qualifyingSpendInWindow": 66500.00,
  "requiredSpend": 50000.00,
  "surplusOrShortfall": 16500.00,
  "progressPercent": 100.0,
  "dataComplete": true,
  "quota": { "visitsPerQuarter": 2, "quarterStart": "2026-10-01", "quarterEnd": "2026-12-31",
             "visitsUsed": 0, "visitsAvailable": 2, "voucherRequired": false,
             "note": "2 of 2 visit(s) left in Q4 2026." },
  "monthlyBreakdown": [
    { "month": "2026-08", "label": "August 2026", "hasData": true, "includedInWindow": true,
      "transactionCount": 8, "totalPurchases": 28450.00, "qualifyingSpend": 21500.00,
      "nonQualifyingSpend": 8500.00,
      "byCategory": { "TRAVEL": 7800.00, "SHOPPING": 6200.00, "CASH_WITHDRAWAL": 5000.00, "...": 0 } }
  ],
  "categoryBreakdown": [
    { "category": "SHOPPING", "qualifying": true, "amount": 28300.00, "percentOfAllSpend": 33.5, "transactionCount": 5 }
  ],
  "topMerchants": [
    { "merchant": "FLIPKART INTERNET BANGALORE", "amount": 9500.00, "transactionCount": 1, "percentOfQualifyingSpend": 14.3 }
  ],
  "excludedSpend": {
    "total": 17590.00, "percentOfAllSpend": 20.9,
    "byCategory": { "CASH_WITHDRAWAL": 8000.00, "FUEL": 6000.00, "WALLET_RELOAD": 3000.00, "FEES_AND_CHARGES": 590.00 },
    "largest": [ { "date": "2026-08-12", "description": "ATM WDL AXIS BANK DELHI", "amount": 5000.00, "category": "CASH_WITHDRAWAL" } ]
  },
  "planForUpcomingMonths": {
    "planMonth": "2026-12", "planMonthIsFollowingMonth": true,
    "windowStart": "2026-09-01", "windowEnd": "2026-11-30",
    "windowMonths": ["2026-09", "2026-10", "2026-11"],
    "monthsAlreadyCompleted": ["2026-09", "2026-10"], "monthsStillToCome": ["2026-11"],
    "spendAlreadyBanked": 45000.00, "requiredSpend": 50000.00,
    "additionalQualifyingSpendNeeded": 5000.00, "perMonthTarget": 5000.00, "perWeekTarget": 1155.00,
    "alreadySecured": false,
    "monthsLeavingWindow": ["2026-08"], "spendLeavingWindow": 21500.00,
    "followingMonthOutlook": "December 2026 uses September 2026 - November 2026, which includes November 2026, so what you spend in November 2026 directly decides December 2026."
  },
  "insights": ["Average qualifying spend is ₹22,167 per month across 3 month(s) with data.", "..."],
  "recommendations": ["You already meet the criteria: ₹66,500 qualifying spend vs ₹50,000 required (surplus ₹16,500).", "..."],
  "warnings": ["Refunds/reversals worth ₹1,200 were found. They are not netted off; ..."],
  "assumptions": ["Spend is grouped by transaction date into calendar months, regardless of statement cycle dates.", "..."],
  "files": [ { "fileName": "axis_ace_eligible_aug_2026.csv", "format": "CSV", "transactionsParsed": 9,
               "linesSkipped": 0, "firstTransactionDate": "2026-08-03", "lastTransactionDate": "2026-08-25" } ]
}
```

**200 - not eligible (key fields; low-spend sample set)**

```json
{
  "status": "NOT_ELIGIBLE",
  "eligibleForTargetMonth": false,
  "summary": "You are NOT eligible for lounge access in November 2026. Qualifying spend between 2026-08-01 and 2026-10-31 is ₹30,800 against the required ₹50,000. Shortfall: ₹19,200. To qualify in December 2026 you need ₹29,000 of qualifying spend across November 2026.",
  "qualifyingSpendInWindow": 30800.00,
  "requiredSpend": 50000.00,
  "surplusOrShortfall": -19200.00,
  "progressPercent": 61.6,
  "excludedSpend": { "total": 36000.00, "percentOfAllSpend": 53.9,
                     "byCategory": { "FUEL": 18000.00, "WALLET_RELOAD": 10000.00, "CASH_WITHDRAWAL": 8000.00 } },
  "planForUpcomingMonths": { "planMonth": "2026-12", "spendAlreadyBanked": 21000.00,
                             "additionalQualifyingSpendNeeded": 29000.00, "perMonthTarget": 29000.00,
                             "perWeekTarget": 6698.00, "alreadySecured": false },
  "recommendations": [
    "Not eligible in November 2026: qualifying spend ₹30,800 is ₹19,200 short of ₹50,000. The window (August 2026 - October 2026) is closed, so new spend in November 2026 will not change November 2026.",
    "Step 1 - To be eligible in December 2026: make at least ₹29,000 of QUALIFYING spend during November 2026 (about ₹29,000 per month, roughly ₹6,698 per week). Already banked for that window: ₹21,000 (September 2026 - October 2026).",
    "Step 2 - Spend on categories that count (your strongest so far: shopping, dining, grocery). Pay recurring bills, groceries, dining, travel and shopping on this card instead of UPI/other cards.",
    "Step 3 - Stop wasting spend: ₹36,000 (53.9% of everything you charged) does not count: fuel ₹18,000, wallet reload ₹10,000, cash withdrawal ₹8,000. Pay for fuel, wallet top-ups and cash needs with another instrument and keep this card for qualifying merchants.",
    "Step 4 - On the 1st of December 2026, re-upload your statements with targetMonth=2026-12 (including the newest month) to confirm; then check eligibility at the desk with POST /api/v1/lounge/check-eligibility."
  ]
}
```

**Error responses**

| HTTP | Cause |
|---|---|
| `400` | No file attached · unsupported file type · more than 6 files · file > 5 MB · unknown `bank` / bad `targetMonth` format / negative `visitsUsedInTargetQuarter` / missing `productCode` · PDF is password protected |
| `401` | Missing / wrong credentials |
| `422` | File parsed but no transaction table / no recognisable transactions |

---

### 4.4 `GET /api/v1/analytics/summary`

| Query param | Default | Range | Meaning |
|---|---|---|---|
| `hours` | `24` | 1-8760 | Look-back window |

```bash
curl -u reception:lounge@123 'http://localhost:8080/api/v1/analytics/summary?hours=24'
```

**200 (sample values on the demo data set; illustrative)**

```json
{
  "windowStart": "2026-10-28T10:00:00",
  "windowEnd": "2026-10-29T10:00:00",
  "totalAttempts": 11,
  "approved": 5,
  "redeemed": 5,
  "denied": 6,
  "approvalRatePercent": 45.45,
  "rejectionRatePercent": 54.55,
  "denialsByReason": {
    "INSUFFICIENT_SPEND": 2, "EXPIRED_VOUCHER": 1, "INVALID_VOUCHER": 0, "QUOTA_EXCEEDED": 1,
    "UNSUPPORTED_CARD": 1, "CARD_NOT_FOUND": 1, "CARD_INACTIVE": 0, "CUSTOMER_MISMATCH": 0
  },
  "byBank": [
    { "bank": "HDFC",    "attempts": 7, "approved": 4, "denied": 3 },
    { "bank": "AXIS",    "attempts": 1, "approved": 0, "denied": 1 },
    { "bank": "ICICI",   "attempts": 1, "approved": 1, "denied": 0 },
    { "bank": "KOTAK",   "attempts": 1, "approved": 0, "denied": 1 },
    { "bank": "UNKNOWN", "attempts": 1, "approved": 0, "denied": 1 }
  ],
  "topCardTiers": [
    { "bank": "HDFC", "cardTier": "INFINIA", "attempts": 3 },
    { "bank": "HDFC", "cardTier": "MILLENNIA", "attempts": 2 }
  ],
  "peakEntryHours": [ { "hour": 6, "label": "06:00-06:59", "attempts": 2 } ],
  "hourlyDistribution": [ { "hour": 0, "label": "00:00-00:59", "attempts": 0 }, "… 24 buckets …" ]
}
```

*"approved" includes redeemed passes.* Convenience views: `GET /api/v1/analytics/denials` (reason → count) and `GET /api/v1/analytics/peak-hours` (top 3 busiest hours). Invalid `hours` → `400`.

---

## 5. Getting Started & Execution Guide

### 5.1 Prerequisites

| Requirement | Version |
|---|---|
| JDK | **17 or newer** |
| Maven | **3.8+** |
| Git | any recent version |
| (optional) Postman / Newman | for the bundled collection |

### 5.2 Clone, build, run

```bash
# 1. Clone
git clone https://github.com/coder-stat920715/LoungeAccessSystem.git
cd LoungeAccessSystem

# 2. Build + run all tests
mvn clean package

# 3a. Run with Maven
mvn spring-boot:run

# 3b. …or run the packaged jar
java -jar target/lounge-access-system-1.0.0.jar
```

The application listens on **http://localhost:8080** and seeds demo data at start-up (printed in the console).

Override settings at launch, for example:

```bash
java -jar target/lounge-access-system-1.0.0.jar \
  --lounge.security.password='S3cret!' \
  --lounge.pass.validity-minutes=1 \
  --lounge.bootstrap.enabled=false
```

### 5.3 Developer tooling

| Tool | URL | Notes |
|---|---|---|
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Click **Authorize** → *basicAuth* (`reception` / `lounge@123`) |
| **OpenAPI JSON** | http://localhost:8080/v3/api-docs | Public |
| **H2 console** | http://localhost:8080/h2-console | JDBC URL `jdbc:h2:mem:loungedb` · User `sa` · Password *(empty)* |

Handy H2 queries:

```sql
SELECT id, bank, product_code, last4, active FROM cards;
SELECT status, denial_reason, COUNT(*) FROM lounge_visits GROUP BY status, denial_reason;
UPDATE cards SET active = FALSE WHERE last4 = '0011';   -- simulate a blocked card
```

### 5.4 Demo data

Created by `DataLoader` (dates are relative to *today*, so the rules always have data in their windows).

| customerId | Card number | Scenario |
|--:|---|---|
| 1 | `4147200000000011` | Axis ACE, ₹62,000 in window → eligible |
| 2 | `4147200000000029` | Axis ACE, issued 20 days ago → new-card exception |
| 3 | `4147200000000037` | Axis ACE, only ₹20,000 qualifying → `INSUFFICIENT_SPEND` |
| 4 | `4386100000000013` | HDFC Platinum Debit · voucher `482915037261` valid · `719304286150` expired · `260481937502` used |
| 5 | `4386100000000021` | HDFC Platinum Debit, low spend · voucher `530918274466` |
| 6 | `4386200000000019` | HDFC Infinia → unlimited |
| 7 | `5241900000000012` | HDFC Millennia → `QUOTA_EXCEEDED` |
| 8 | `5241910000000010` | HDFC Regalia Gold → 3 visits / quarter |
| 9 | `6521000000000012` | ICICI Coral (generic strategy) → 1 visit left |
| 10 | `4000100000000018` | Kotak debit → `UNSUPPORTED_CARD` |

### 5.5 Quick smoke test

```bash
# Approve an HDFC debit card with a voucher
curl -u reception:lounge@123 -H 'Content-Type: application/json' \
  -X POST localhost:8080/api/v1/lounge/check-eligibility \
  -d '{"cardNumber":"4386100000000013","customerId":4,"loungeId":"HYD-T1-ENCORE","voucherCode":"482915037261"}'

# Redeem the returned pass
curl -u reception:lounge@123 -H 'Content-Type: application/json' \
  -X POST localhost:8080/api/v1/lounge/redeem-pass \
  -d '{"passToken":"<loungePassToken>","loungeId":"HYD-T1-ENCORE","terminalId":"T-01"}'

# Statement forecast for November 2026
curl -u reception:lounge@123 localhost:8080/api/v1/statements/analyze \
  -F statements=@samples/statements/axis_ace_not_eligible_aug_2026.csv \
  -F statements=@samples/statements/axis_ace_not_eligible_sep_2026.csv \
  -F statements=@samples/statements/axis_ace_not_eligible_oct_2026.csv \
  -F bank=AXIS -F productCode=ACE -F targetMonth=2026-11
```

### 5.6 Running tests

```bash
mvn test                                   # all unit + integration tests
mvn -Dtest='Axis*Test,Hdfc*Test' test      # only the strategy tests
```

| Suite | Covers |
|---|---|
| `AxisBankStrategyTest`, `HdfcDebitCardStrategyTest`, `HdfcCreditCardStrategyTest`, `GenericEligibilityStrategyTest` | Mockito-based qualifying / non-qualifying spend, thresholds, exclusions, voucher failures, quota, new-card exception |
| `LoungeEligibilityEngineTest` | Strategy selection, `UNSUPPORTED_CARD`, blocked / expired cards |
| `DateRangeUtilTest`, `InrFormatTest` | Calendar-month / quarter windows, Indian digit grouping |
| `CsvStatementParserTest`, `TextStatementParserTest`, `ParsingUtilTest`, `TransactionCategorizerTest` | Statement parsing, date/amount formats, categorisation |
| `SpendRuleResolverTest`, `StatementAnalyzerTest` | Rule windows, verdicts, gap analysis (Axis rolling + HDFC quarterly) |
| `StatementControllerIT`, `LoungeAccessApplicationTests` | Multipart endpoint + security, Spring context wiring |

---

## 6. Postman Collection & Testing

The `postman/` folder contains:

| File | Purpose |
|---|---|
| `LoungeAccessSystem.postman_collection.json` | **62 requests** in 10 folders; every request has test scripts and a description (purpose · preconditions · steps · expected result) |
| `LoungeAccessSystem.postman_environment.json` | `baseUrl`, `username`, `password` |
| `TESTING_GUIDE.md` | Step-by-step guide and manual-only scenarios |

### 6.1 Import & run

1. **Start a fresh application instance** (`mvn spring-boot:run`) - H2 is in-memory, so a restart resets data.
2. In Postman: **Import** → select both JSON files from `postman/`; choose the **"LoungeAccessSystem - Local"** environment.
3. **Statement-analysis requests upload files** from `samples/statements/`. Set **Settings → General → Working directory** to the repository root *(or re-select the files in each request's Body → form-data tab)*.
4. Run the whole collection with the **Collection Runner** (folders in order, delay 0 ms) - every request should be green.

```bash
# Headless with Newman
npm i -g newman
newman run postman/LoungeAccessSystem.postman_collection.json \
  -e postman/LoungeAccessSystem.postman_environment.json --working-dir .
```

> Folders **00-08 are order-dependent** (pass tokens are carried in collection variables; approvals consume quota/vouchers) - restart the app before running them again. **Folder 09 is stateless** and can be run on its own, in any order, any number of times.

### 6.2 What each folder verifies

| Folder | Verifies |
|---|---|
| **00 Smoke & Security** | App up, OpenAPI public, `401` without / with wrong credentials |
| **01 Axis Bank** | ₹50,000 / 3-month rule, fuel-cash-wallet exclusions, new-card exception, idempotent re-issue |
| **02 HDFC Debit** | ₹10,000 / quarter + voucher: missing, malformed, unknown, other-card, used, expired, valid; single-use enforcement |
| **03 HDFC Credit** | Infinia unlimited, Millennia quota, Regalia Gold approval |
| **04 Generic & Unsupported** | ICICI fallback strategy, Kotak `UNSUPPORTED_CARD` |
| **05 Lookup & Validation** | BIN lookup, `CARD_NOT_FOUND`, `CUSTOMER_MISMATCH`, bean validation, malformed JSON |
| **06 Quota Exhaustion** | Regalia Gold 3-visit walk-through to `QUOTA_EXCEEDED` |
| **07 Redeem Pass** | Redeem, double redeem `409`, wrong lounge `409`, unknown `404`, validation `400` |
| **08 Analytics** | Totals, rates, denial breakdown, bank / tier / peak-hour views, parameter validation |
| **09 Statement Analysis** | Eligible & not-eligible forecasts, gap-analysis plan, new-card exception, quota exhausted, missing month, duplicate uploads, TXT/PDF-style file, HDFC quarterly rule, Infinia, unsupported bank, validation & `401` |

### 6.3 Statement-analysis scenarios (folder 09)

| Request | Files + parameters | Expected |
|---|---|---|
| 9.1 Eligible | `axis_ace_eligible_{aug,sep,oct}_2026.csv`, Axis ACE, `2026-11` | `ELIGIBLE`, ₹66,500 vs ₹50,000; Dec needs **₹5,000** more |
| 9.2 Not eligible | `axis_ace_not_eligible_*.csv` | `NOT_ELIGIBLE`, shortfall ₹19,200; Dec needs **₹29,000** in Nov |
| 9.3 New card | not-eligible files + `cardIssueDate=2026-09-10` | `ELIGIBLE_NEW_CARD_EXCEPTION` |
| 9.4 Exception expired | `cardIssueDate=2026-06-10` | `NOT_ELIGIBLE` |
| 9.5 Quota used | eligible files + `visitsUsedInTargetQuarter=2` | `QUOTA_EXHAUSTED` |
| 9.6 Missing October | Aug + Sep only | `NOT_ELIGIBLE`, `dataComplete=false`, October warning |
| 9.7 Text statement | `axis_ace_eligible_aug_oct_2026_statement.txt` | Same result as 9.1, 30 transactions, `format=TEXT` |
| 9.8 Overlapping uploads | Aug attached twice | Totals unchanged + "counted once" warning |
| 9.9 HDFC Millennia | eligible files, `bank=HDFC` | Window Jul-Sep, plan month **Jan 2027**, ₹81,300 needed |
| 9.10 HDFC Infinia | `productCode=INFINIA` | `ELIGIBLE`, no requirement, unlimited |
| 9.11 – 9.19 | Unsupported bank, no file, wrong file type, unreadable CSV, bad enum / month, missing `productCode`, negative visits, no credentials | `NOT_SUPPORTED`, `400`, `422`, `401` |

**Sample statement files** (`samples/statements/`)

| File | Content |
|---|---|
| `axis_ace_eligible_{aug,sep,oct}_2026.csv` | Qualifying ₹21,500 / ₹26,300 / ₹18,700 (+ fuel, cash, wallet, fees, payments, one refund) |
| `axis_ace_not_eligible_{aug,sep,oct}_2026.csv` | Qualifying ₹9,800 / ₹12,400 / ₹8,600; heavy fuel / wallet / cash |
| `axis_ace_eligible_aug_oct_2026_statement.txt` | Same data as the eligible set, in PDF-text layout |
| `invalid_statement.csv` | Not a statement → `422` |

### 6.4 Manual-only scenarios

| Scenario | How |
|---|---|
| **Expired pass (`410`)** | Start with `--lounge.pass.validity-minutes=1`, issue a pass (1.1), wait 61 s, redeem it (7.4) → `410`; quota is released, re-checking issues a new pass |
| **Blocked card (`CARD_INACTIVE`)** | H2 console: `UPDATE cards SET active = FALSE WHERE last4 = '0011';` then run 1.1 |
| **Password-protected PDF** | Attach the PDF as `statements` and add the `pdfPassword` text field; wrong/missing password → `400` |
| **Default target month** | Omit `targetMonth` → evaluated for *next month* on the server clock |
| **Concurrency** | Fire the same check twice in parallel (`curl … &`) - row locking guarantees quota is never over-consumed |

---

## 7. Configuration Reference

All business rules live in `src/main/resources/application.yml` under the `lounge` prefix.

| Property | Default | Description |
|---|---|---|
| `lounge.timezone` | `Asia/Kolkata` | Zone of the application `Clock` (drives every date window) |
| `lounge.bootstrap.enabled` | `true` | Seed demo customers, cards, spend, vouchers, visits (disable in production) |
| `lounge.security.username` / `password` | `reception` / `lounge@123` | HTTP Basic credentials - **override outside dev** |
| `lounge.security.card-hash-pepper` | *(dev value)* | HMAC key for card-number hashing - **use a KMS-managed secret** |
| `lounge.pass.validity-minutes` | `120` | Lifetime of an issued pass |
| `lounge.axis.minimum-spend` / `lookback-months` / `new-card-waiver-months` / `visits-per-quarter` | `50000` / `3` / `3` / `2` | Axis rule |
| `lounge.hdfc-debit.minimum-spend` / `visits-per-quarter` | `10000` / `2` | HDFC debit rule |
| `lounge.generic.minimum-spend` / `visits-per-quarter` / `supported-banks` | `25000` / `2` / `ICICI,SBI` | Generic fallback rule |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `5MB` / `20MB` | Statement upload limits |

Environment variables work through Spring's relaxed binding, e.g. `LOUNGE_SECURITY_PASSWORD`, `LOUNGE_AXIS_MINIMUM_SPEND=75000`.

> HDFC credit tiers (Millennia ₹1,00,000 / 1, Regalia Gold ₹60,000 / 3, Infinia & Diners Black unlimited) are defined in the `HdfcCreditTier` enum.

---

## 8. Security & Production Readiness

| Concern | Implementation |
|---|---|
| **Card data (PII/PCI)** | Full PANs are never persisted or logged - only `HMAC-SHA256(pepper, PAN)`, 8-digit BIN and last 4; logs use first-6/last-4 masking |
| **Authentication** | Stateless HTTP Basic for all `/api/**`; Swagger UI, OpenAPI docs and H2 console are open for local development only |
| **Race conditions** | `PESSIMISTIC_WRITE` on the card row during evaluation; pass redemption locks the visit row |
| **Statement uploads** | Processed in memory, never stored or logged; type allow-list (CSV/TXT/PDF), size and count limits, sanitised file names, PDF password never logged |
| **Error handling** | RFC 7807 problem documents; no stack traces or internal messages leaked |
| **Auditability** | Every attempt (approved/denied) is a `LoungeVisit` row with reason, pass token and timestamps |

**Before going to production:** replace H2 with PostgreSQL/MySQL and add Flyway migrations · externalise all secrets (credentials, pepper) · replace Basic auth with your IdP (OAuth2/OIDC, mTLS for terminals) · disable `lounge.bootstrap.enabled` and the H2 console · put the API behind TLS · add rate limiting.

---

## 9. Known Limitations & Roadmap

**Limitations of the statement forecast**

- Only knows the statements you upload; missing months and partial coverage are flagged, never guessed.
- **EMI conversions / loan-on-card transactions are *not* detected as a separate exclusion.** They are categorised by merchant keywords and otherwise counted as qualifying (`OTHER`). If your bank excludes EMI conversions, remove those rows from the CSV or treat the result as an upper bound.
- Refunds and reversals are reported as a warning but **not netted** off qualifying spend.
- Categories are keyword based; unknown merchants count as qualifying.
- PDF extraction is best-effort (bank layouts differ); check `files[].transactionsParsed`, `linesSkipped` and `warnings`. CSV is the most reliable input.
- The forecast is advisory - the bank's ledger and the desk-time check are authoritative.
- Bank rules reflect the thresholds in this repository's configuration, not live bank policy.

**Roadmap ideas**

- [ ] Dedicated `EMI_CONVERSION` and `RENT / GOVT / INSURANCE` exclusion categories (configurable per bank)
- [ ] Net refunds against the originating purchase category
- [ ] Bank-specific PDF templates and OCR for scanned statements
- [ ] Persistent storage (PostgreSQL) + Flyway, OAuth2 resource server
- [ ] Per-lounge capacity and guest-pass rules; webhook / event stream for lounge operators
- [ ] Additional bank strategies (ICICI, SBI, Kotak dedicated rules)

---

<div align="center">

**LoungeAccessSystem** · Spring Boot 3 · Java 17

*No licence file is included yet - add one (e.g. MIT / Apache-2.0) before distributing.*

</div>
