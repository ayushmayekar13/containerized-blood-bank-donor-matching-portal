# Per-task reports

One standalone Word document per subject task. Each is self-contained — it carries its own title
block, student details and page numbering — so a single task can be printed, emailed or submitted
on its own without the other fourteen.

The consolidated version of the same material is
[`../DevOps_Project_Report_23102A0018.docx`](../DevOps_Project_Report_23102A0018.docx) (45 pages,
with a title page, declaration, table of contents and appendices).

| # | Document | Task | Completed | Tag |
|---|---|---|---|---|
| 1 | `Task_01_Problem_Definition_and_Scope.docx` | Problem Definition and Scope | 29 Sep 2026 | `task-01` |
| 2 | `Task_02_Agile_Planning_and_DevOps_Workflow.docx` | Agile Planning and DevOps Workflow | 29 Sep 2026 | `task-02` |
| 3 | `Task_03_Requirements_Architecture_and_Technology_Setup.docx` | Requirements, Architecture and Technology Setup | 30 Sep 2026 | `task-03` |
| 4 | `Task_04_Git_and_GitHub_Repository_Initialization.docx` | Git and GitHub Repository Initialization | 30 Sep 2026 | `task-04` |
| 5 | `Task_05_Feature_Development_with_Branching.docx` | Feature Development with Branching | 1 Oct 2026 | `task-05` |
| 6 | `Task_06_MVP_Completion_and_Git_Collaboration.docx` | MVP Completion and Git Collaboration | 2 Oct 2026 | `task-06`, `v1.0.0` |
| 7 | `Task_07_Jenkins_Installation_and_Continuous_Integration_Job.docx` | Jenkins Installation and Continuous Integration Job | 3 Oct 2026 | `task-07` |
| 8 | `Task_08_Pipeline_as_Code_and_Server_Deployment.docx` | Pipeline as Code and Server Deployment | 4 Oct 2026 | `task-08` |
| 9 | `Task_09_Selenium_Test_Design_and_Local_Execution.docx` | Selenium Test Design and Local Execution | 5 Oct 2026 | `task-09` |
| 10 | `Task_10_Continuous_Testing_in_Jenkins.docx` | Continuous Testing in Jenkins | 6 Oct 2026 | `task-10` |
| 11 | `Task_11_Docker_Image_and_Container_Lifecycle.docx` | Docker Image and Container Lifecycle | 7 Oct 2026 | `task-11` |
| 12 | `Task_12_JenkinsDocker_Continuous_Deployment.docx` | Jenkins-Docker Continuous Deployment | 7 Oct 2026 | `task-12`, `v1.1.0` |
| 13 | `Task_13_Configuration_Management_Script.docx` | Configuration Management Script | 8 Oct 2026 | `task-13` |
| 14 | `Task_14_Automated_Provisioning_and_Reliability_Validation.docx` | Automated Provisioning and Reliability Validation | 8 Oct 2026 | `task-14`, `v1.2.0` |
| 15 | `Task_15_Final_EndtoEnd_Release_Documentation_and_Viva.docx` | Final End-to-End Release, Documentation and Viva | 9 Oct 2026 | `task-15`, `v1.3.0` |

## What each document contains

| Section | Contents |
|---|---|
| Title block | Task number and title, student details, completion date and the git tag that recovers that state |
| Task statement | The subject task sheet's wording, quoted |
| Required deliverable | The task sheet's "Deliverable:" line, quoted |
| Work carried out | What was actually built |
| **Decisions taken, and why** | Each decision with its reasoning — the part a viva question lands on |
| Captured output | Real output from a run, not a reconstruction |
| Defects found | Where the task produced one, with its cause |
| Evidence retained | Where in the repository the proof lives |
| Verification | The commands that reproduce the result |

## Regenerating

Both the fifteen documents and the merged report are generated from the **same source**,
`docs/report-generator/tasks.js`, so they cannot drift apart — correcting a task means editing that
one file and rebuilding both:

```bash
cd docs/report-generator
npm install
node build.js       ../../DevOps_Project_Report_23102A0018.docx   # merged, 45 pages
node build-tasks.js ../../task-reports                            # these fifteen
```
