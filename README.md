\# PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix



\## 1. Team Details



| Field | Details |

|---|---|

| Problem Statement | PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix |

| Team ID | T058 |

| Team Member 1 | Baby V S (1AT24CG012) |

| Team Member 2 | Bhagyashree Rama Naik (1AT24CG013) |

| Programming Language | Java 17 |

| Build Tool | Apache Maven |

| Testing Framework | JUnit 5 |

| Application Type | Command-line backend application |



\---



\## 2. Project Description



The Recruitment ATS (Applicant Tracking System) is a Java-based backend application designed to manage the recruitment process for an IT services organization.



It supports job requisition registration, candidate registration, application tracking, recruitment stage transitions, interview scheduling, offer calculation and multi-level offer approval.



The application uses an in-memory approach and processes recruitment commands from a text file. It does not require a database or graphical user interface.



\### Main Implemented Capabilities



1\. \*\*Job Requisition Management (BR-01):\*\* Registers job requisitions with role, grade, headcount, location, CTC range, hiring manager and opening date.



2\. \*\*Candidate Management (BR-02):\*\* Registers candidates with personal details, notice period, current CTC, expected CTC, experience and recruitment source.



3\. \*\*Candidate Application (BR-03):\*\* Links candidates to registered requisitions and creates applications with an initial recruitment stage and stage history.



4\. \*\*Recruitment Stage Management (BR-04):\*\* Supports 14 application stages and validates legal transitions between stages using reason codes.



5\. \*\*Interview Score Validation (BR-05, BR-06, BR-07):\*\* Records interview scores and applies score-based validation and rejection rules.



6\. \*\*Headcount Management (BR-30, BR-31):\*\* Tracks requisition headcount reservations and releases when applicable.



7\. \*\*Interview Scheduling:\*\* Provides interview scheduling with interviewer availability checks, panel requirements, working-day validation, minimum time gaps and daily interview limits.



8\. \*\*Offer Calculation:\*\* Calculates proposed CTC, joining bonus, buyout reimbursement, recruitment fees and total hiring cost using the applicable rules.



9\. \*\*Offer Approval Matrix:\*\* Supports sequential approval by Hiring Manager, HR Head, VP and CFO when required. It also handles approval deadlines, timeout records and manual approval actions for non-skippable approvers.



10\. \*\*Command-File Processing (BR-38):\*\* Reads pipe-delimited commands from a text file and displays successful operations and validation errors.



\*\*Implementation note:\*\* The recruitment workflow is demonstrated through the command-file application. Interview scheduling, offer calculation and approval services are implemented as separate service-layer components and covered by their respective automated tests.



\---



\## 3. Technologies Used



\- Java 17

\- Apache Maven

\- JUnit 5

\- BigDecimal for financial calculations

\- Java LocalDate and LocalDateTime for date and time handling

\- In-memory data management



\---



\## 4. Project Structure



```text

T058-PS-055/

|-- pom.xml

|-- README.md

|-- decision-log.md

|-- docs/

|   |-- design-document.pdf

|-- sample-data/

|   |-- happy-path.txt

|-- src/

|   |-- main/

|   |   |-- java/

|   |       |-- com/ats/

|   |           |-- Main.java

|   |           |-- model/

|   |           |   |-- Application.java

|   |           |   |-- ApprovalDecision.java

|   |           |   |-- ApprovalRecord.java

|   |           |   |-- ApproverRole.java

|   |           |   |-- AtsException.java

|   |           |   |-- Candidate.java

|   |           |   |-- CandidateSource.java

|   |           |   |-- CandidateStage.java

|   |           |   |-- GradeBand.java

|   |           |   |-- Interviewer.java

|   |           |   |-- InterviewRound.java

|   |           |   |-- InterviewSlot.java

|   |           |   |-- Offer.java

|   |           |   |-- OfferCalculationResult.java

|   |           |   |-- OfferStatus.java

|   |           |   |-- ReasonCode.java

|   |           |   |-- Requisition.java

|   |           |   |-- RequisitionStatus.java

|   |           |   |-- ScheduleResult.java

|   |           |   |-- StageTransitionRecord.java

|   |           |   |-- TransitionResult.java

|   |           |-- parser/

|   |           |   |-- CommandExecutionResult.java

|   |           |   |-- CommandFileParser.java

|   |           |-- service/

|   |               |-- InterviewScheduler.java

|   |               |-- OfferApprovalService.java

|   |               |-- OfferCalculator.java

|   |               |-- RecruitmentService.java

|   |               |-- StageTransitionService.java

|   |-- test/

|       |-- java/

|           |-- com/ats/

|               |-- CommandFileParserTest.java

|               |-- InterviewSchedulerTest.java

|               |-- OfferApprovalServiceTest.java

|               |-- OfferCalculatorTest.java

|               |-- RecruitmentServiceTest.java

|               |-- ScoreValidationTest.java

|               |-- StageTransitionServiceTest.java

```



\---



\## 5. Prerequisites



Install the following:



\- JDK 17 or a compatible newer JDK.

\- Apache Maven 3.8 or later.

\- Windows Command Prompt or another terminal.



Verify the installations:



```cmd

java -version

javac -version

mvn -version

```



\---



\## 6. Build and Run Instructions



Open Windows Command Prompt and navigate to the project directory:



```cmd

cd C:\\Users\\Dell\\Downloads\\T058-PS-055\\T058-PS-055

```



\### Step 1: Compile the project



```cmd

mvn clean compile

```



\### Step 2: Run automated tests



```cmd

mvn test

```



\### Step 3: Package the application



```cmd

mvn clean package

```



\### Step 4: Run the application



Run using the packaged JAR:



```cmd

java -jar target\\recruitment-ats-1.0.0.jar

```



The application automatically uses:



```text

sample-data\\happy-path.txt

```



To provide a custom command file:



```cmd

java -jar target\\recruitment-ats-1.0.0.jar sample-data\\happy-path.txt

```



\---



\## 7. Command File Syntax



The application reads pipe-delimited commands from the input file.



Blank lines and lines beginning with `#` are ignored.



| Command | Format |

|---|---|

| REGISTER\_REQUISITION | `REGISTER\_REQUISITION \\| reqId \\| role \\| grade \\| headcount \\| location \\| minCtc \\| maxCtc \\| manager \\| date` |

| REGISTER\_CANDIDATE | `REGISTER\_CANDIDATE \\| candidateId \\| name \\| noticeDays \\| currentCtc \\| expectedCtc \\| experience \\| source` |

| APPLY | `APPLY \\| applicationId \\| candidateId \\| requisitionId \\| date` |

| RECORD\_SCORE | `RECORD\_SCORE \\| applicationId \\| round \\| score` |

| TRANSITION\_STAGE | `TRANSITION\_STAGE \\| applicationId \\| targetStage` |

| SUMMARY | `SUMMARY` |



Supported recruitment sources include REFERRAL, PORTAL, AGENCY and INTERNAL.



\---



\## 8. Sample Input



Example commands from `sample-data/happy-path.txt`:



```text

REGISTER\_REQUISITION | REQ-101 | Senior Java Backend Engineer | G4 | 2 | Bengaluru | 1400000 | 2200000 | Vikram Desai | 2026-03-01

REGISTER\_CANDIDATE | CAN-201 | Aarav Kulkarni | 30 | 1350000 | 1800000 | 5.5 | REFERRAL

APPLY | APP-301 | CAN-201 | REQ-101 | 2026-03-05

TRANSITION\_STAGE | APP-301 | L1

RECORD\_SCORE | APP-301 | L1 | 4.2

TRANSITION\_STAGE | APP-301 | L2

RECORD\_SCORE | APP-301 | L2 | 4.0

TRANSITION\_STAGE | APP-301 | HR

RECORD\_SCORE | APP-301 | HR | 4.5

TRANSITION\_STAGE | APP-301 | OFFER\_DRAFT

TRANSITION\_STAGE | APP-301 | OFFER\_APPROVED

TRANSITION\_STAGE | APP-301 | OFFERED

TRANSITION\_STAGE | APP-301 | ACCEPTED

TRANSITION\_STAGE | APP-301 | JOINED

SUMMARY

```



\---



\## 9. Testing and Verification



The project contains seven JUnit 5 test classes covering the recruitment workflow and supporting services.



| Test Class | Tests |

|---|---:|

| CommandFileParserTest | 2 |

| InterviewSchedulerTest | 9 |

| OfferApprovalServiceTest | 8 |

| OfferCalculatorTest | 14 |

| RecruitmentServiceTest | 6 |

| ScoreValidationTest | 11 |

| StageTransitionServiceTest | 6 |

| \*\*Total\*\* | \*\*56\*\* |



\### Final Test Results



\- Total tests executed: 56

\- Passed: 56

\- Failed: 0

\- Errors: 0

\- Skipped: 0



The following Maven command completed successfully:



```cmd

mvn clean package

```



The packaged JAR was also executed successfully.



\### Sample Execution Result



```text

ATS PIPELINE SNAPSHOT

Registered Requisitions : 2

Registered Candidates   : 4

Active/Total Applications: 4



Execution Complete

Commands Processed: 43

Succeeded: 43

Rejected/Errors: 0

```



\---



\## 10. Design Document and Decision Log



The repository includes:



\- `docs/design-document.pdf` – Project design documentation.

\- `decision-log.md` – Records important technical and implementation decisions.



These documents provide additional details about the project design and development choices.



\---



\## 11. Conclusion



The PS-055 Recruitment ATS project provides a Java-based backend solution for managing important recruitment activities.



It includes recruitment workflow management, interview scheduling, offer calculation and approval services. The application has been built and tested using Maven and JUnit 5, with all 56 automated tests passing successfully.



