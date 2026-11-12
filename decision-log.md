# Decision Log — PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix



- **Team ID:** T058

- **Team Members:** Baby V S (`1AT24CG012`) and Bhagyashree Rama Naik (`1AT24CG013`)

- **Language & Build:** Java 17, Apache Maven, JUnit 5



This document records the architectural decisions established in the Week 1 Design Document (`docs/design-document.pdf`) and the concrete implementation decisions made during Weeks 2, 3, and 4.



---



## Part 1: Baseline Decisions from Week 1 Design Document



| Decision ID | Topic | Decision & Rationale |

| :--- | :--- | :--- |

| **DEC-01** | `OUT_OF_BAND` Threshold (BR-16) | Compare original expected CTC against `bandMax * 1.10` before offer caps are applied. Exactly 10% above `bandMax` is not flagged `OUT_OF_BAND`; 10.01% above is flagged. |

| **DEC-02** | CTC Band Validation & Floor/Cap Precedence (BR-01, BR-15) | Requisition registration enforces `minCtc > 0`, `maxCtc > 0`, and `minCtc <= maxCtc`. Monetary values use `BigDecimal` scaled to 2 decimal places with `RoundingMode.HALF_UP`. |

| **DEC-03** | Interview Window Boundaries (BR-10, BR-13) | An interview slot ending exactly at an availability window boundary is treated as valid, provided overlap checks and the 15-minute inter-interview gap pass. |

| **DEC-04** | Offer Validity Cutoff (BR-29) | Offer acceptance through Day 7 at 23:59 is valid; at Day 8 00:00, an unaccepted offer becomes `WITHDRAWN`. |

| **DEC-05** | Approval SLA & Non-Skippable Approvers (BR-28) | Approval SLAs count working days, excluding weekends and holidays. VP and CFO are never automatically skipped on timeout. |

| **DEC-06** | Total Cost Definition (BR-23) | Total hiring cost is computed as `offeredCTC + applicableFee + joiningBonus` when evaluating the `bandMax * 1.15` CFO approval rule. |

| **DEC-07** | Deterministic Headcount Reservation (BR-31, BR-33) | Headcount reservations occur when entering `OFFER_DRAFT` and are released if an offer-holding application moves to `DECLINED`, `WITHDRAWN`, `REJECTED`, or `ON_HOLD`. |

| **DEC-08** | Separation of Candidate, Application, and Requisition State | `Candidate` stores person-level profile data. `Application` owns stage progression, interview scores, and audit timestamps for a specific `Requisition`, allowing independent applications to different requisitions. |



---



## Part 2: Week 2 Implementation Decisions & Assumptions



| Decision ID | Topic | Decision & Rationale |

| :--- | :--- | :--- |

| **DEC-09** | Pipe-Delimited Command File Syntax (BR-38) | Commands in `sample-data/happy-path.txt` use `|` as the field delimiter. This supports multi-word names and job roles without escape sequences or quotation marks. Blank lines and `#` comment lines are ignored. |

| **DEC-10** | Candidate Identifier Flexibility (BR-02) | `Candidate` stores both `candidateId` and `name`. Registration supports explicit `(candidateId, name, ...)` and single-identifier `(candidateIdOrName, ...)` conventions. |

| **DEC-11** | Candidate CTC & Fresher Support (BR-02) | `currentCtc >= 0` is allowed so fresher candidates can be registered. Negative current CTC is rejected. `expectedCtc` must be strictly greater than zero. |

| **DEC-12** | Duplicate Prevention Rules (BR-01, BR-02, BR-03) | In-memory `LinkedHashMap` stores preserve insertion order while rejecting duplicate requisition IDs, candidate IDs, application IDs, and duplicate applications by the same candidate to the same requisition. |

| **DEC-13** | Explicit 14-Stage State Machine & Terminal Locks (BR-04) | Application progression and controlled transitions are explicitly enumerated in `StageTransitionService`. Terminal stages include `JOINED`, `REJECTED`, `DECLINED`, and `WITHDRAWN`. Invalid skips and backward transitions are rejected. |

| **DEC-14** | Configurable Interview Score Gates (BR-05, BR-06, BR-07) | `StageTransitionService` enforces L1 score >= 3.0 and L2 score >= 3.5. Any round score below 2.0 causes immediate rejection when scores are recorded. Mandatory score enforcement can be enabled through `StageTransitionService(true)`. |

| **DEC-15** | Early Headcount Reservation Hook (BR-30, BR-31) | Moving into `OFFER_DRAFT` checks available requisition headcount. If positions are exhausted, the transition is rejected. Reserved headcount is released when an application moves to the applicable withdrawal or rejection stages. |



---






---

## Part 3: Week 3 and Week 4 Implementation Decisions

| Decision ID | Topic | Decision & Rationale |
| :--- | :--- | :--- |
| **DEC-16** | Interview Scheduling Rules (BR-10 to BR-13) | Interview scheduling validates interviewer availability, panel requirements, overlapping slots, and a minimum 15-minute gap between interviews. An interviewer can conduct a maximum of three interviews per working day. |
| **DEC-17** | Working-Day Calendar | Interview scheduling and approval deadlines exclude weekends and configured holidays. Interview scheduling searches for an available slot within the next 10 working days. |
| **DEC-18** | Offer Calculation and Cost Components (BR-15 to BR-23) | Monetary calculations use BigDecimal. Offer calculation considers the CTC hike cap, salary band, joining bonus, referral or agency fee, and total hiring cost. |
| **DEC-19** | Sequential Offer Approval Workflow (BR-24 to BR-28) | Approvals follow a sequential chain. The Hiring Manager is first. HR Head, VP, and CFO are included when applicable. The offer becomes APPROVED after all required approvals. |
| **DEC-20** | Approval Timeout Handling | Deadlines exclude weekends and configured holidays. Hiring Manager and HR Head timeouts can be automatically skipped. VP and CFO timeouts require manual action. |
| **DEC-21** | Separation of Service Components | Recruitment processing, interview scheduling, offer calculation, and offer approval are maintained as separate service components for easier testing and maintenance. |
| **DEC-22** | Command-Line Demonstration Scope | Main.java demonstrates recruitment through the command file. Interview scheduling, offer calculation, and offer approval are separate service-layer components verified through unit tests. |
| **DEC-23** | Automated Testing Strategy | JUnit 5 tests cover command parsing, recruitment, stage transitions, score validation, interview scheduling, offer calculations, and approval workflows. The final Maven test run completed with 56 tests, 0 failures, and 0 errors. |