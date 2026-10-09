const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, AlignmentType, BorderStyle, PageBreak,
  Header, Footer, PageNumber, NumberFormat, TableOfContents, LevelFormat,
  convertInchesToTwip, ShadingType, HeadingLevel,
} = require('docx');

const H = require('./helpers.js');
const TASKS = require('./tasks.js');
const { p, rp, h1, h2, h3, bullet, numbered, code, callout, table, spacer, pageBreak,
        CONTENT_W, BODY, MONO, INK, MUTED, ACCENT, RULE, HEAD_BG } = H;

const children = [];
const push = (...xs) => xs.forEach(x => Array.isArray(x) ? children.push(...x) : children.push(x));

// ===========================================================================
// TITLE PAGE
// ===========================================================================
const titleLine = (text, opts = {}) => new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { after: opts.after ?? 120 },
  children: [new TextRun({
    text, font: BODY, size: opts.size ?? 24,
    bold: opts.bold, color: opts.color ?? INK, allCaps: opts.caps,
    characterSpacing: opts.spacing,
  })],
});

push(spacer(500));
push(titleLine('Department of Computer Engineering', { size: 26, bold: true, color: MUTED, after: 60 }));
push(titleLine('Academic Year 2026-27  ·  ODD Semester', { size: 20, color: MUTED, after: 700 }));

push(new Paragraph({
  alignment: AlignmentType.CENTER, spacing: { after: 40 },
  children: [new TextRun({ text: 'DEVOPS PROJECT REPORT', font: BODY, size: 22, bold: true, color: ACCENT, characterSpacing: 60 })],
}));
push(new Paragraph({
  alignment: AlignmentType.CENTER, spacing: { after: 240 },
  border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: ACCENT, space: 10 } },
  children: [],
}));

push(titleLine('Containerized Blood Bank', { size: 44, bold: true, after: 20 }));
push(titleLine('Donor Matching Portal', { size: 44, bold: true, after: 220 }));
push(titleLine('A complete DevOps workflow from commit to a provisioned,', { size: 21, color: MUTED, after: 20 }));
push(titleLine('rollback-capable container', { size: 21, color: MUTED, after: 700 }));

push(table(null, [
  ['Submitted by', 'AYUSH MAYEKAR'],
  ['Roll Number', '23102A0018'],
  ['Class', 'BE Computer Engineering  ·  Division A'],
  ['Subject', 'DevOps'],
  ['Project Title', 'Containerized Blood Bank Donor Matching Portal'],
  ['Tasks Covered', 'All 15 subject tasks'],
  ['Final Release', 'v1.3.0'],
], [2900, 6460], { labelColumn: true }));

push(spacer(900));
push(titleLine('Submitted in partial fulfilment of the requirements for the DevOps subject', { size: 18, italics: true, color: MUTED, after: 0 }));
push(pageBreak());

// ===========================================================================
// DECLARATION
// ===========================================================================
push(h1('Declaration'));
push(p('I declare that this project report, titled "Containerized Blood Bank Donor Matching Portal", is my own work, carried out by me for the DevOps subject of the Department of Computer Engineering during the ODD semester of the academic year 2026-27.'));
push(p('All fifteen tasks specified in the subject task sheet were implemented and verified on a working system. Every result quoted in this report — build logs, test counts, pipeline stage timings, Ansible play recaps and container output — is captured output from a real run, retained in the project repository under the evidence/ directory, and can be reproduced using the commands given at the end of each task section.'));
push(p('The eleven defects described in this report were encountered during development and fixed; each is recorded in the repository with its cause rather than only its remedy. Where the project falls short of production readiness, this is stated explicitly in Section 6.'));
push(spacer(600));
push(table(null, [
  ['Name', 'Ayush Mayekar'],
  ['Roll Number', '23102A0018'],
  ['Class', 'BE Computer Engineering, Division A'],
  ['Date', '9 October 2026'],
  ['Signature', ' '],
], [2900, 6460], { labelColumn: true }));
push(pageBreak());

// ===========================================================================
// TABLE OF CONTENTS
// ===========================================================================
push(h1('Contents'));
push(new TableOfContents('Contents', { hyperlink: true, headingStyleRange: '1-2' }));
push(pageBreak());

// ===========================================================================
// 1. ABSTRACT
// ===========================================================================
push(h1('1.  Abstract'));
push(p('This project delivers a web portal that matches blood requests to donors, and — more importantly for this subject — the automated DevOps pipeline that builds, tests, containerizes, deploys and provisions it.'));
push(p('The application maintains a searchable donor registry, records blood requests, and ranks the donors who can actually be contacted for each request by applying the ABO/Rh compatibility matrix, the ninety-day donation interval and the donor\'s availability flag. A role-based workflow governs the lifecycle of every request, so that only an administrator may confirm that a donation took place.'));
push(p('The pipeline runs 179 unit tests and 16 Selenium user journeys on every commit, with the browser suite acting as a blocking quality gate: a broken user journey cannot reach deployment. A passing build produces a versioned Docker image, publishes it to a private registry, and deploys a fresh container pulled back from that registry — 101 seconds from commit to a healthy container. Ansible describes the target node as code, is demonstrably idempotent, and rolls back to any previous release in under a minute without touching the data.'));
push(p('Eleven defects were found and fixed during development. Four of them were found by the Selenium suite after all unit tests were already passing, which is the clearest available measurement of what the testing strategy was worth.'));

push(h2('1.1  Headline results'));
push(table(['Measure', 'Result'], [
  ['Subject tasks completed', { text: '15 of 15, each a tagged commit', bold: true }],
  ['Automated tests', '179 unit tests + 16 Selenium user journeys'],
  ['Blood-group compatibility pairs asserted', '64 of 64 (27 compatible)'],
  ['Commit to running, health-checked container', { text: '101 seconds', bold: true }],
  ['Pipeline stages', '11'],
  ['Ansible second run', { text: 'changed = 0  (idempotent)', bold: true, mono: true }],
  ['Rollback to a previous release', 'Under 1 minute, data intact'],
  ['Defects found and fixed', '11, each with its cause recorded'],
  ['Release tags', 'v1.0.0, v1.1.0, v1.2.0, v1.3.0'],
], [4600, 4760]));
push(spacer());

push(callout('The single result worth pointing at',
  'Pipeline build #9: Checkout, Build, Unit Test (179 green), Package, Deploy and the behavioural Health Check all passed — and the build still went red, because a browser found the request page offering an administrator-only action to a staff user. The Promote stage was never entered; no image was published and no container was replaced. That is the difference between having tests and having a gate.'));
push(pageBreak());

// ===========================================================================
// 2. INTRODUCTION
// ===========================================================================
push(h1('2.  Introduction'));

push(h2('2.1  The problem'));
push(p('Blood cannot be manufactured. Every unit must come from a human donor, and whole blood has a shelf life of roughly 35 to 42 days, so stock cannot simply be built up in advance. A donor who has just given whole blood is ineligible for approximately ninety days. And red-cell compatibility is considerably wider than "same group only" — a B+ patient can receive from B+, B−, O+ or O−.'));
push(p('Most blood banks still match requests to donors by telephone and paper register. The consequences are measurable:'));
push(bullet('Time-to-match — from a request being raised to a compatible, eligible, reachable donor being identified — runs to hours when it should be seconds.'));
push(bullet('Compatible donors in the same city go uncontacted, because nobody computes the compatibility matrix from memory under pressure.'));
push(bullet('Ineligible donors are called back, because the ninety-day interval is tracked mentally or not at all.'));
push(bullet('Nobody can answer "how many eligible O− donors do we have in this city right now?" without reading a register.'));
push(spacer(120));

push(h2('2.2  Problem statement'));
push(new Paragraph({
  spacing: { before: 100, after: 200, line: 276 },
  indent: { left: 340, right: 340 },
  border: { left: { style: BorderStyle.SINGLE, size: 14, color: ACCENT, space: 10 } },
  children: [new TextRun({
    text: 'Blood banks currently match blood requests to donors through manual, memory-driven, register-based processes. There is no single searchable donor registry, no automatic enforcement of blood-group compatibility or the ninety-day donation-eligibility rule, no tracked lifecycle for a blood request, and no role-based accountability for who may advance a request. This project delivers a containerized web portal that addresses each of those, built, tested, packaged and deployed through an automated DevOps pipeline.',
    font: BODY, size: 21, italics: true, color: INK,
  })],
}));

push(h2('2.3  Objectives'));
push(h3('Functional'));
push(bullet('Maintain a single, searchable donor registry supporting create, view, update and search.'));
push(bullet('Record blood requests with patient, blood group, units, hospital, city and urgency.'));
push(bullet('Produce a ranked list of compatible, eligible and available donors for any request.'));
push(bullet('Enforce a request status workflow in which only the permitted role may perform each transition.'));
push(bullet('Present a summary dashboard of supply against demand, with shortage flags.'));
push(h3('DevOps — the assessed objectives'));
push(bullet('Version-control the project with a documented branching policy, feature branches, pull-request review, a resolved merge conflict and semantic version tags.'));
push(bullet('Build and package the application reproducibly with Maven, producing an archivable artefact.'));
push(bullet('Automate build, test, package and deploy in Jenkins, defined as code in a Jenkinsfile.'));
push(bullet('Automate functional verification with Selenium and make it a quality gate that stops deployment on failure.'));
push(bullet('Containerize the application, publish versioned images to a registry, and deploy automatically after a successful pipeline run.'));
push(bullet('Describe the target environment as code with Ansible, proved idempotent, with a health check and a tested rollback.'));
push(spacer(120));

push(h2('2.4  Users and roles'));
push(table(['Role', 'Who', 'May do'], [
  ['STAFF', 'Blood bank technician', 'Register and update donors, record requests, run the matching engine, assign a matched donor'],
  ['ADMIN', 'Medical officer', 'Everything STAFF may do, plus confirm a donation (fulfil) and reject a request; view the dashboard'],
  ['DONOR', 'Registered donor', 'View own profile and donation history; control own availability only'],
], [1500, 2400, 5460]));
push(pageBreak());

// ===========================================================================
// 3. TECHNOLOGY AND ARCHITECTURE
// ===========================================================================
push(h1('3.  Technology Stack and Architecture'));

push(h2('3.1  Stack, with justification'));
push(p('Each selection answers a requirement in the subject task sheet, and the reason is recorded so that it can be defended rather than merely stated.'));
push(table(['Layer', 'Selection', 'Why this'], [
  ['Language', 'Java 21 (LTS)', 'Spring Boot 3 requires Java 17+; the same JVM is used on the build host and inside the runtime image'],
  ['Framework', 'Spring Boot 3.3, Spring MVC + Thymeleaf', 'One artefact serves both the UI and the REST API; Actuator supplies the health endpoint consumed by the container, Ansible and Jenkins'],
  ['Build tool', 'Maven 3.10', 'Named in the task sheet. Surefire and Failsafe separate fast unit tests from slow browser tests'],
  ['Packaging', 'WAR — bloodbank.war', 'The task sheet names Tomcat as the deployment target. A Spring Boot WAR is also executable, so one artefact runs three ways with no second build profile'],
  ['Deployment target', 'Tomcat 10.1 (jdk21-temurin)', 'Jakarta EE 10, matching Spring Boot 3. Nginx cannot host a WAR'],
  ['Database', 'H2 2.x in file mode, on a Docker volume', 'Real SQL persistence with no external service, so the pipeline runs on one laptop and offline; the volume proves data survives container replacement'],
  ['Security', 'Spring Security, BCrypt, three roles', 'Required to demonstrate the role-based workflow; authorisation is enforced in the service layer'],
  ['Containerisation', 'Docker, multi-stage build', 'The build toolchain never reaches the runtime image'],
  ['Registry', 'registry:2 on localhost:5001', 'Versioned images with no credentials and no internet dependency at demonstration time'],
  ['CI/CD', 'Jenkins LTS 2.568.2', 'Named in the task sheet. A freestyle job for Task 7 and a declarative Jenkinsfile from Task 8'],
  ['UI testing', 'Selenium 4 + JUnit 5, headless Chrome', 'Selenium Manager resolves the matching driver, so no binary is committed and nothing rots when Chrome updates'],
  ['Configuration management', 'Ansible with community.docker', 'Agentless; idempotency falls out of writing ordinary tasks'],
], [1700, 2500, 5160]));
push(spacer());

push(h2('3.2  The rule that makes the system testable'));
push(p('Every business rule lives in the service layer or on a domain enumeration. Controllers bind input, call exactly one service method, and choose a view. That is why 64 compatibility pairs and eight eligibility boundaries can be proved in milliseconds without a browser or a servlet container, and why the Selenium suite only has to cover five journeys.'));
push(p('It is also what makes the authorisation claim true: because the role check sits in RequestService rather than in the controller or the template, the REST API cannot bypass a restriction that the screens appear to apply. Hiding a button is presentation, not security.'));

push(h2('3.3  The matching engine'));
push(p('A donor matches a request when three independent predicates all hold, and the matches are then ranked by four rules. Each ranking rule is a decision, not an accident:'));
push(table(['Rule', 'Decision', 'Reason'], [
  ['1', 'Exact blood-group match first', 'A universal O− donor should not be spent on an O+ patient while an O+ donor is available; O− is the scarcest and most widely demanded stock'],
  ['2', 'Same city next', 'Travel time is the dominant component of time-to-match'],
  ['3', 'Longest rested next', 'Rotates the load across the register instead of repeatedly calling the same willing people'],
  ['4', 'Name last', 'Purely so that ordering is deterministic and the unit tests cannot be flaky'],
], [800, 2900, 5660]));
push(spacer());
push(p('Urgency deliberately does not affect matching. Relaxing compatibility or eligibility under time pressure is precisely the shortcut the portal exists to prevent, so a CRITICAL request draws the same shortlist as a LOW one; urgency affects display order only.'));

push(h2('3.4  Environment and ports'));
push(p('Every port is defined once, in scripts/env.sh, and neither default is used.'));
push(table(['Service', 'Port', 'Why not the default'], [
  ['Application (host → container)', '9090 → 8080', 'Host port 8080 is already occupied by an unrelated service on the development machine'],
  ['Jenkins', '8090', 'Same reason'],
  ['Private Docker registry', '5001', 'macOS reserves port 5000 for the AirPlay Receiver'],
], [3200, 1700, 4460]));
push(pageBreak());

// ===========================================================================
// 4. THE 15 TASKS
// ===========================================================================
push(h1('4.  Implementation of the Fifteen Tasks'));
push(p('Each task below records the task-sheet statement, the required deliverable, the work carried out, the decisions taken with their reasoning, the captured output, the evidence retained in the repository, and the commands that reproduce it.'));
push(p('Every task was completed as a single tagged commit, so the state of the project at the end of any task can be recovered with a checkout of that tag.'));
push(spacer(200));

TASKS.forEach((t, idx) => {
  if (idx > 0) push(pageBreak());

  push(h2(`Task ${t.n}  ·  ${t.title}`));

  push(table(null, [
    ['Task statement', t.statement],
    ['Required deliverable', t.deliverable],
    ['Completed', `${t.date}    (tag: ${t.tag})`],
  ], [2300, 7060], { labelColumn: true }));
  push(spacer(200));

  push(h3('Work carried out'));
  t.did.forEach(d => push(bullet(d)));
  push(spacer(120));

  push(h3('Decisions taken, and why'));
  push(table(['Decision', 'Reasoning'], t.decisions.map(([d, r]) => [{ text: d, bold: true }, r]),
    [3500, 5860]));
  push(spacer(200));

  if (t.output) {
    push(h3('Captured output'));
    push(code(t.output, { barColor: ACCENT, after: 60 }));
    push(new Paragraph({
      spacing: { after: 200 },
      alignment: AlignmentType.LEFT,
      children: [new TextRun({ text: t.outputCap, font: BODY, size: 17, italics: true, color: MUTED })],
    }));
  }

  if (t.callout) push(callout(t.callout[0], t.callout[1]));

  if (t.bugs) {
    push(h3('Defects found in this task'));
    push(table(['ID', 'Defect and cause'], t.bugs.map(([id, d]) => [{ text: id, bold: true }, d]),
      [1200, 8160]));
    push(spacer(200));
  }

  push(h3('Evidence retained'));
  push(table(['Location in the repository', 'What it shows'],
    t.evidence.map(([f, w]) => [{ text: f, mono: true }, w]), [3900, 5460]));
  push(spacer(200));

  push(h3('Verification'));
  push(code(t.verify.map(v => '$ ' + v)));
});
push(pageBreak());

// ===========================================================================
// 5. RESULTS
// ===========================================================================
push(h1('5.  Results'));

push(h2('5.1  Success criteria frozen in Task 1'));
push(p('Ten measurable criteria were fixed before any code was written, each with a target and a stated method of measurement. All ten were met.'));
push(table(['ID', 'Criterion', 'Target', 'Achieved'], [
  ['S1', 'Time to produce a ranked donor shortlist', '< 2 s for 500 donors', 'Met'],
  ['S2', 'Compatibility correctness', '64/64 pairs', { text: '64/64', bold: true }],
  ['S3', 'Eligibility correctness', '0 ineligible donors shortlisted', { text: '0', bold: true }],
  ['S4', 'Workflow safety', '0 illegal transitions accepted', { text: '0', bold: true }],
  ['S5', 'Build reproducibility', 'Green from a clean clone', 'Met'],
  ['S6', 'Commit to deployed container', '< 6 min', { text: '101 s', bold: true }],
  ['S7', 'Quality gate effectiveness', 'A failing test stops deployment', { text: 'Demonstrated', bold: true }],
  ['S8', 'Provisioning idempotency', 'Second run reports changed=0', { text: 'changed=0', bold: true }],
  ['S9', 'Recovery', 'Rollback healthy in < 2 min', { text: '< 1 min', bold: true }],
  ['S10', 'Manual deployment steps', '0', { text: '0', bold: true }],
], [700, 3900, 2600, 2160]));
push(spacer());

push(h2('5.2  Testing summary'));
push(table(['Level', 'Count', 'Tool', 'Covers'], [
  ['Unit', '179', 'JUnit 5 + Surefire', 'The 64-pair compatibility matrix, eligibility at 0/1/45/89/90/91/120/400 days, every ranking rule, every legal and illegal transition, every role guard, dashboard aggregation'],
  ['HTTP contract', 'within the 179', 'MockMvc over the real filter chain', '401, 403, 404, 409 and 422 asserted over HTTP, because the claim is that the API cannot bypass the restrictions the screens apply'],
  ['UI journey', '16', 'Selenium 4 + Failsafe', 'Five critical flows: registry, search and ranking, request-to-dashboard, the role guard, and fulfil-to-eligibility'],
], [1700, 1500, 2400, 3760]));
push(spacer());

push(h2('5.3  The complete pipeline'));
push(code([
  'pipeline build #15   status = SUCCESS   total = 101.3 s',
  '',
  'STAGE                                STATUS        TIME',
  'Checkout                             SUCCESS        1.1 s',
  'Build                                SUCCESS        3.2 s',
  'Unit Test                            SUCCESS       11.0 s      179 tests',
  'Package                              SUCCESS        4.4 s',
  'Deploy to Tomcat                     SUCCESS        7.9 s',
  'Health Check                         SUCCESS        1.4 s',
  'UI Test (quality gate)               SUCCESS       43.7 s      16 journeys',
  'Promote: image, registry, deploy     SUCCESS       24.9 s',
  'Post-deploy verification             SUCCESS        1.4 s',
], { barColor: ACCENT }));

push(h2('5.4  The eleven defects'));
push(p('Each defect is recorded in the repository with its cause rather than only its remedy. Four were found by the Selenium suite after the unit tests were already passing.'));
push(table(['ID', 'Defect', 'Found by'], [
  ['BUG-01', 'A donor who last donated exactly 90 days ago was excluded as ineligible — an off-by-one at the boundary', 'Unit test'],
  ['BUG-02', 'The dashboard shortage flag counted unavailable donors as supply', 'Selenium journey'],
  ['BUG-03', 'The dashboard reported 8 callable donors with only 1 registered — overlapping sets were summed', 'Manual HTTP check'],
  ['BUG-04', 'The request table was sometimes empty at startup — two unordered ApplicationRunner beans', 'Manual check'],
  ['BUG-05', 'The worklist showed MEDIUM above HIGH above CRITICAL — an @Enumerated(STRING) column ordered in SQL', 'Manual HTTP check'],
  ['BUG-06', 'The request page offered an ADMIN-only action to a STAFF user (the deliberate Task 10 defect)', { text: 'Selenium — stopped the deployment', bold: true }],
  ['BUG-07', 'A DONOR signing in with correct credentials landed on HTTP 403', 'Selenium journey'],
  ['BUG-08', 'mvn clean failed whenever a container was running, because the WAR was bind-mounted from target/', 'Running the UI suite'],
  ['BUG-09', 'GET /logout in the test teardown silently did not sign the user out', 'Selenium journey'],
  ['BUG-10', 'A native ChromeDriver click returned successfully without submitting the form', 'Selenium journey'],
  ['BUG-11', 'The hardened image could not open its own database — volume ownership', 'Running the image'],
  ['BUG-12', 'An authenticated request to /actuator/info returned the HTML login page with HTTP 200', 'Writing the Ansible health check'],
], [1100, 6200, 2060]));
push(spacer());

push(h2('5.5  Two lessons that generalise'));
push(callout('Waiting for a condition that is already true is not waiting at all',
  'One Selenium journey failed three times for three different reasons. The third is the instructive one: the test waited for the results table to be visible after clicking Search, but the unfiltered list shown before the click had the same table — so the wait was satisfied instantly against the old page and the read raced the navigation. After an action that replaces the page, wait for something about the old page to become false (staleness) before waiting for anything about the new one.'));
push(callout('Verify a deployment using facts the deployer owns, not features of the artefact being deployed',
  'The first rollback test failed its verification even though the rollback itself had worked. The playbook asked the application what version it was, and the older image predated the endpoint that answers. A rollback that can only verify versions newer than the bug it is escaping is useless precisely when it is needed. Verification now reads the container\'s image reference from the Docker API, which works for every version, past and future.'));
push(pageBreak());

// ===========================================================================
// 6. LIMITATIONS AND FUTURE WORK
// ===========================================================================
push(h1('6.  Limitations and Future Enhancements'));

push(h2('6.1  Known limitations'));
push(p('These are stated deliberately. A project that knows what it is not is more credible than one that has not looked.'));
push(numbered('H2 is a single-node embedded database. It is adequate for demonstrating persistence across container replacement, but PostgreSQL would be the first change required for a multi-user deployment.'));
push(numbered('Donor personal data — names, phone numbers, email addresses — is stored unencrypted at rest, with no access audit log. Both are prerequisites for any real deployment handling identifiable medical data.'));
push(numbered('Only the ninety-day donation interval is modelled as eligibility. Real deferral criteria also include weight, haemoglobin, medication, recent travel and illness.'));
push(numbered('"Same city" is a case-insensitive string comparison, not a distance calculation. A donor one kilometre away in a neighbouring municipality ranks below one twenty kilometres away in the same city.'));
push(numbered('Ansible manages a Docker node on the development machine. Switching to a remote host over SSH is a one-line inventory change, because nothing in the plays refers to localhost — but that path has not been exercised against a real remote server.'));
push(numbered('The registry is local and unauthenticated. Correct for an offline demonstration; a real deployment requires TLS and credentials.'));
push(numbered('There is no static-analysis stage. It was omitted to keep the pipeline inside the six-minute budget.'));
push(numbered('There is a single environment. The pipeline deploys to one place, with no separate staging tier.'));
push(spacer(120));

push(h2('6.2  Future enhancements, in the order they would be done'));
push(table(['Priority', 'Enhancement', 'Why it comes where it does'], [
  ['1', 'PostgreSQL with backups, replacing H2', 'Everything else is blocked by single-node storage once there is more than one concurrent user'],
  ['2', 'Encryption at rest and an access audit log', 'Required before the system may hold real donor data'],
  ['3', 'Static analysis in the pipeline', 'Cheap to add, and catches a class of defect the current gates do not'],
  ['4', 'SMS or WhatsApp notification to matched donors', 'The obvious next functional step; needs a paid gateway and consent capture'],
  ['5', 'Geolocation and distance-based ranking', 'Replaces the city string comparison with real proximity'],
  ['6', 'Blood unit inventory with component split and expiry', 'Doubles the data model; the MVP matches donors rather than managing stock'],
  ['7', 'A staging environment and a blue-green release strategy', 'Meaningful only once there is more than one environment to promote between'],
  ['8', 'Kubernetes with horizontal scaling', 'Beyond the scope of this subject, which specifies Docker and Ansible'],
], [1100, 3600, 4660]));
push(pageBreak());

// ===========================================================================
// 7. CONCLUSION
// ===========================================================================
push(h1('7.  Conclusion'));
push(p('The application works and its business rules are correct, but the result worth stating is about the pipeline.'));
push(p('Pipeline build #9 is the whole argument in a single artefact. Checkout, Build, Unit Test with 179 tests green, Package, Deploy and the behavioural Health Check all passed — and the build still went red, because a browser found the request detail page offering an action to a user who was not permitted to take it. The Promote stage was never entered, no image was published, and no container was replaced.'));
push(p('That is the difference between having tests and having a gate. A test suite tells you that something is wrong. A gate makes it impossible for the wrong thing to reach production. Arranging the pipeline so that every promotion stage sits after the browser suite means that no human has to remember to check, and no flag has to be read correctly.'));
push(p('The same principle recurs throughout the project. Images are tagged immutably so that a rollback has something to name. The deployment pulls from the registry so that a broken push cannot hide until the moment it matters most. Ansible is idempotent so that running it during an incident is safe. The rollback uses the same code path as a deployment, so the recovery path is exercised every day rather than only during incidents. In each case the design choice removes a way for a human to be wrong, rather than adding a step for a human to remember.'));
push(p('Eleven defects were found during development, four of them by a browser after the unit suite was already green. That number is the most honest measure of what this pipeline is worth: not that it ran, but that it caught things.'));
push(spacer(200));
push(callout('What the project demonstrates in one sentence',
  'A complete DevOps workflow in which a commit is built, unit-tested, packaged, deployed, functionally tested in a real browser, containerized, published as an immutable version, deployed again from the registry, provisioned by idempotent configuration management, and reversible in under a minute — with every one of those steps automated, asserted and reproducible from the repository.'));
push(pageBreak());

// ===========================================================================
// 8. APPENDIX
// ===========================================================================
push(h1('Appendix A  ·  Repository Map'));
push(table(['Path', 'Contents'], [
  [{ text: 'pom.xml', mono: true }, 'Maven build producing target/bloodbank.war'],
  [{ text: 'Dockerfile, docker/', mono: true }, 'Multi-stage image build and the privilege-dropping entrypoint'],
  [{ text: 'Jenkinsfile', mono: true }, 'The declarative 11-stage CI/CD pipeline'],
  [{ text: 'src/main/java/com/bloodbank/', mono: true }, 'domain, repo, service, web, config'],
  [{ text: 'src/test/java/', mono: true }, '179 unit tests (Surefire)'],
  [{ text: 'src/it/java/com/bloodbank/ui/', mono: true }, '16 Selenium journeys (Failsafe)'],
  [{ text: 'jenkins/', mono: true }, 'Job XML, plugin list, bootstrap Groovy, setup guide'],
  [{ text: 'ansible/', mono: true }, 'Inventory, group_vars specification, three roles, site and rollback playbooks'],
  [{ text: 'scripts/', mono: true }, 'env, preflight, deploy, health-check, docker-lifecycle, release, run-ui-tests, verify-reliability, demo-all'],
  [{ text: 'docs/', mono: true }, 'All 15 task documents, SRS, architecture, API, report, runbook, viva guide, troubleshooting'],
  [{ text: 'evidence/', mono: true }, 'Real captured output: build logs, test reports, stage views, command transcripts, screenshots'],
], [3200, 6160]));
push(spacer());

push(h1('Appendix B  ·  Reproducing the Results'));
push(p('All commands are run from the repository root. The toolchain required is Java 21, Maven, Docker, Ansible and Google Chrome; scripts/preflight.sh verifies each of them.'));
push(h3('The complete workflow in one command'));
push(code(['$ bash scripts/demo-all.sh']));
push(h3('Individual stages'));
push(code([
  '$ source scripts/env.sh',
  '$ bash scripts/preflight.sh            # toolchain, daemon, ports, base images',
  '$ mvn clean verify                     # 179 unit tests',
  '$ bash scripts/run-ui-tests.sh         # build, deploy, 16 Selenium journeys',
  '$ bash scripts/docker-lifecycle.sh all # the full container lifecycle, logged',
  '$ bash scripts/release.sh 99           # versioned image via the registry',
  '$ bash scripts/verify-reliability.sh   # provisioning, idempotency, health, rollback',
]));
push(h3('Jenkins'));
push(code([
  '$ bash scripts/jenkins-setup.sh start',
  '$ bash scripts/jenkins-job.sh build bloodbank-pipeline --wait',
]));
push(h3('Recovering the state at any task'));
push(code([
  '$ git tag -l | sort -V',
  '$ git checkout task-07      # exactly the state at which Jenkins CI was completed',
  '$ git checkout v1.0.0       # the functional MVP',
]));
push(spacer());

push(h1('Appendix C  ·  Release History'));
push(table(['Tag', 'Marks'], [
  [{ text: 'task-01 … task-15', mono: true }, 'The exact state at which each subject task was completed'],
  [{ text: 'v1.0.0', mono: true }, 'Functional MVP — donor registry, matching engine, role-based workflow, dashboard'],
  [{ text: 'v1.1.0', mono: true }, 'Containerized continuous deployment through a private registry'],
  [{ text: 'v1.2.0', mono: true }, 'Automated provisioning with proven idempotency and tested rollback'],
  [{ text: 'v1.3.0', mono: true }, 'Final documented release'],
], [2600, 6760]));
push(spacer(300));
push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 400 },
  border: { top: { style: BorderStyle.SINGLE, size: 6, color: RULE, space: 10 } },
  children: [new TextRun({
    text: 'End of report  ·  Ayush Mayekar  ·  23102A0018  ·  BE CMPN A  ·  DevOps',
    font: BODY, size: 18, color: MUTED, italics: true,
  })],
}));

// ===========================================================================
// DOCUMENT
// ===========================================================================
const doc = new Document({
  // Word fills a TOC field only when the field is updated. Without this the
  // table of contents opens empty and looks broken, which is worse than not
  // having one. updateFields makes Word refresh it on open.
  features: { updateFields: true },
  creator: 'Ayush Mayekar',
  title: 'Containerized Blood Bank Donor Matching Portal — DevOps Project Report',
  description: 'DevOps project report covering all 15 subject tasks. Ayush Mayekar, 23102A0018, BE CMPN A.',
  styles: {
    default: {
      document: { run: { font: BODY, size: 21, color: INK } },
    },
  },
  numbering: {
    config: [
      {
        reference: 'bullets',
        levels: [
          { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 460, hanging: 240 } } } },
          { level: 1, format: LevelFormat.BULLET, text: '◦', alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 900, hanging: 240 } } } },
        ],
      },
      {
        reference: 'numbers',
        levels: [
          { level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 460, hanging: 240 } } } },
        ],
      },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },          // A4
        margin: { top: 1250, right: 1134, bottom: 1250, left: 1134, header: 600, footer: 600 },
        pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL },
      },
      titlePage: true,
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          alignment: AlignmentType.RIGHT,
          spacing: { after: 60 },
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 4 } },
          children: [new TextRun({
            text: 'Containerized Blood Bank Donor Matching Portal  ·  Ayush Mayekar  ·  23102A0018',
            font: BODY, size: 16, color: MUTED,
          })],
        })],
      }),
      first: new Header({ children: [new Paragraph({ children: [] })] }),
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          border: { top: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 6 } },
          children: [new TextRun({ text: 'Page ', font: BODY, size: 16, color: MUTED }),
                     new TextRun({ children: [PageNumber.CURRENT], font: BODY, size: 16, color: MUTED }),
                     new TextRun({ text: ' of ', font: BODY, size: 16, color: MUTED }),
                     new TextRun({ children: [PageNumber.TOTAL_PAGES], font: BODY, size: 16, color: MUTED })],
        })],
      }),
      first: new Footer({ children: [new Paragraph({ children: [] })] }),
    },
    children,
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = process.argv[2] || 'DevOps_Project_Report.docx';
  fs.writeFileSync(out, buf);
  console.log('written:', out, (buf.length / 1024).toFixed(0) + ' KB');
});
