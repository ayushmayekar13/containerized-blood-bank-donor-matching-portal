# DevOps Lifecycle Workflow — Blood Bank Donor Matching Portal

Ayush Mayekar · 23102A0018 · Task 2 deliverable

All diagrams are Mermaid, so they render directly on GitHub and in most IDE previewers. To export
PNGs for the report: `npx -y @mermaid-js/mermaid-cli -i docs/diagrams/devops-lifecycle.md -o out.png`

---

## 1. The lifecycle as implemented (development → operations)

```mermaid
flowchart LR
    subgraph DEV["Development"]
        P["PLAN<br/>backlog item, issue,<br/>feature/&lt;slug&gt; branch"]
        C["CODE<br/>unit test red → green,<br/>conventional commit"]
    end
    subgraph CI["Continuous Integration — Jenkins"]
        B["BUILD<br/>mvn clean package<br/>→ bloodbank.war"]
        T1["UNIT TEST<br/>Surefire<br/>matching, eligibility,<br/>workflow guards"]
        PK["PACKAGE<br/>Docker image<br/>bloodbank:${BUILD_NUMBER}"]
    end
    subgraph CT["Continuous Testing — quality gate"]
        D1["DEPLOY TO TEST<br/>container on :9090"]
        T2{"UI TEST<br/>Selenium journeys<br/>J1 … J5"}
    end
    subgraph CD["Continuous Delivery / Deployment"]
        R["RELEASE<br/>push versioned image<br/>→ registry :5001"]
        DP["DEPLOY<br/>recreate container<br/>+ named volume"]
    end
    subgraph OPS["Operations"]
        O["OPERATE<br/>Ansible provisioning,<br/>restart policy"]
        M["MONITOR<br/>/actuator/health,<br/>container healthcheck,<br/>Jenkins trend"]
    end

    P --> C --> B --> T1 --> PK --> D1 --> T2
    T2 -- pass --> R --> DP --> O --> M
    T2 -- fail --> FB["FEEDBACK<br/>build red, deploy skipped,<br/>screenshot attached"]
    M --> FB
    FB --> P

    classDef gate fill:#ffe0e0,stroke:#c0392b,stroke-width:2px
    classDef dev fill:#e8f0fe,stroke:#3367d6
    classDef ops fill:#e6f4ea,stroke:#1e8e3e
    class T2 gate
    class P,C dev
    class O,M ops
```

**The single most important arrow** is `T2 -- fail --> FEEDBACK`: because the Selenium stage sits
*before* the release and deploy stages, a failing user journey cannot reach a running container.
That is the quality gate demonstrated in Task 10.

---

## 2. Tool-chain view — which tool owns which phase

```mermaid
flowchart TB
    subgraph L1["Plan & Code"]
        G1["Git<br/>branches, PRs, tags"]
        G2["GitHub<br/>issues, PR review,<br/>branch policy"]
    end
    subgraph L2["Build & Test"]
        M1["Maven 3.9<br/>compile, package WAR"]
        M2["JUnit 5 + Surefire<br/>unit tests"]
        M3["Selenium 4 + Failsafe<br/>UI journeys"]
    end
    subgraph L3["Integrate & Deliver"]
        J["Jenkins 2.568 LTS<br/>freestyle job + Jenkinsfile"]
        DR["Docker 28<br/>image build, tag, run"]
        RG["Private registry :5001<br/>versioned images"]
    end
    subgraph L4["Deploy & Operate"]
        TC["Tomcat 10.1<br/>servlet container"]
        AN["Ansible<br/>provision, idempotency,<br/>health check, rollback"]
    end
    subgraph L5["Monitor"]
        AC["Spring Boot Actuator<br/>/actuator/health"]
        DH["Docker healthcheck<br/>+ restart policy"]
    end

    G1 --> G2 --> J
    J --> M1 --> M2 --> M3
    M3 --> DR --> RG --> TC
    AN --> TC
    TC --> AC --> DH
    DH -. alerts .-> G2
```

---

## 3. Branching and promotion model

```mermaid
gitGraph
    commit id: "T1 scope"
    commit id: "T2 agile plan"
    commit id: "T3 architecture"
    commit id: "T4 scaffolding"
    branch develop
    checkout develop
    branch feature/donor-registry
    commit id: "donor entity + repo"
    commit id: "matching engine"
    commit id: "donor UI + tests"
    checkout develop
    merge feature/donor-registry id: "T5 PR-01 merge" tag: "task-05"
    branch feature/request-workflow
    commit id: "request entity"
    commit id: "role workflow"
    commit id: "dashboard"
    checkout develop
    merge feature/request-workflow id: "T6 PR-02 merge (conflict resolved)"
    checkout main
    merge develop id: "release" tag: "v1.0.0"
    commit id: "T7 Jenkins CI"
    commit id: "T8 Jenkinsfile"
    commit id: "T9 Selenium"
    commit id: "T10 quality gate"
    commit id: "T11 Docker"
    commit id: "T12 CD" tag: "v1.1.0"
    commit id: "T13 Ansible"
    commit id: "T14 rollback" tag: "v1.2.0"
    commit id: "T15 release" tag: "v1.3.0"
```

---

## 4. End-to-end pipeline sequence (commit to running container)

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant Git as Git repository
    participant Jen as Jenkins (:8090)
    participant Mvn as Maven
    participant Dock as Docker engine
    participant Reg as Registry (:5001)
    participant App as Container (:9090)
    participant Ans as Ansible

    Dev->>Git: git push (feature → develop → main)
    Jen->>Git: SCM poll detects new commit
    Jen->>Mvn: mvn clean package
    Mvn-->>Jen: bloodbank.war + Surefire reports
    Jen->>Jen: archive artefact, publish unit test results
    Jen->>Dock: docker build -t bloodbank:${BUILD_NUMBER}
    Jen->>App: docker run -p 9090:8080 (test instance)
    Jen->>Mvn: mvn failsafe:integration-test (Selenium J1…J5)
    alt any journey fails
        Mvn-->>Jen: failure + screenshots
        Jen-->>Dev: build RED, release and deploy stages skipped
    else all journeys pass
        Mvn-->>Jen: Failsafe reports green
        Jen->>Reg: docker push localhost:5001/bloodbank:${BUILD_NUMBER}
        Jen->>Dock: docker rm -f old container
        Jen->>App: docker run new container + volume bloodbank-data
        Jen->>App: GET /actuator/health
        App-->>Jen: {"status":"UP"}
        Jen-->>Dev: build GREEN, version deployed
    end
    Dev->>Ans: ansible-playbook site.yml
    Ans->>Reg: pull pinned image tag
    Ans->>App: ensure container, volume, ports, restart policy
    Ans->>App: health check (uri module)
    Note over Ans,App: second identical run reports changed=0 (idempotent)
    Dev->>Ans: ansible-playbook rollback.yml (on failure)
    Ans->>App: recreate from previous stable tag + health check
```

---

## 5. Request status workflow (the role-based gate inside the application)

```mermaid
stateDiagram-v2
    [*] --> NEW : Staff records request
    NEW --> MATCHED : Staff assigns a compatible,<br/>eligible, available donor
    NEW --> REJECTED : Admin rejects with reason
    MATCHED --> FULFILLED : Admin confirms donation<br/>(donor last-donation date set to today)
    MATCHED --> REJECTED : Admin rejects with reason
    FULFILLED --> [*]
    REJECTED --> [*]

    note right of NEW
        Staff may NOT jump NEW → FULFILLED.
        Enforced in the service layer and
        unit-tested (AC-07.4). This is the
        defect deliberately re-introduced
        in Task 10 to prove the quality gate.
    end note
    note right of FULFILLED
        FULFILLED and REJECTED are terminal.
        Any further transition is refused.
    end note
```

---

## 6. Environments

```mermaid
flowchart LR
    subgraph HOST["Developer laptop (macOS, Apple Silicon) — the whole lab"]
        direction TB
        JEN["Jenkins LTS<br/>localhost:8090<br/>JAVA_HOME = JDK 21"]
        REG["Private registry<br/>localhost:5001<br/>registry:2"]
        subgraph DOCKER["Docker engine"]
            CT1["bloodbank-app<br/>Tomcat 10.1 + JDK 21<br/>9090 → 8080"]
            VOL[("volume<br/>bloodbank-data<br/>H2 database")]
        end
        CHROME["Google Chrome<br/>headless, driven by<br/>Selenium Manager"]
    end
    JEN -->|build, push| REG
    JEN -->|docker run| CT1
    CT1 --- VOL
    CHROME -->|HTTP journeys| CT1
    JEN -->|invoke| CHROME
```

Port 8080 on this host is already in use by an unrelated service, which is why Jenkins listens on
**8090** and the application is published on **9090**. `scripts/preflight.sh` asserts that 8090,
9090 and 5001 are free (or owned by this project) before anything starts.
