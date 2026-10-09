# Per-task reports

One standalone Word document per subject task, **rendered from that task's own markdown document**
in [`../docs/`](../docs). Each is self-contained — its own title block, student details, running
header and page numbering — so a single task can be printed, emailed or submitted on its own.

The consolidated version is [`../DevOps_Project_Report_23102A0018.docx`](../DevOps_Project_Report_23102A0018.docx)
(~86 pages, with a title page, declaration and table of contents).

| # | Document | Source | Tag |
|---|---|---|---|
| 1 | `Task_01_Problem_Definition_and_Scope.docx` | `docs/01-problem-definition.md` | `task-01` |
| 2 | `Task_02_Agile_Planning_and_DevOps_Workflow.docx` | `docs/02-agile-planning.md` | `task-02` |
| 3 | `Task_03_Requirements_Architecture_and_Technology_Setup.docx` | `docs/03-requirements-architecture.md` | `task-03` |
| 4 | `Task_04_Git_and_GitHub_Repository_Initialization.docx` | `docs/04-repository-setup.md` | `task-04` |
| 5 | `Task_05_Feature_Development_with_Branching.docx` | `docs/05-feature-branching.md` | `task-05` |
| 6 | `Task_06_MVP_Completion_and_Git_Collaboration.docx` | `docs/06-mvp-and-conflict.md` | `task-06`, `v1.0.0` |
| 7 | `Task_07_Jenkins_Installation_and_Continuous_Integration_Job.docx` | `docs/07-jenkins-ci.md` | `task-07` |
| 8 | `Task_08_Pipeline_as_Code_and_Server_Deployment.docx` | `docs/08-pipeline-as-code.md` | `task-08` |
| 9 | `Task_09_Selenium_Test_Design_and_Local_Execution.docx` | `docs/09-selenium-tests.md` | `task-09` |
| 10 | `Task_10_Continuous_Testing_in_Jenkins.docx` | `docs/10-continuous-testing.md` | `task-10` |
| 11 | `Task_11_Docker_Image_and_Container_Lifecycle.docx` | `docs/11-docker-lifecycle.md` | `task-11` |
| 12 | `Task_12_JenkinsDocker_Continuous_Deployment.docx` | `docs/12-jenkins-docker-cd.md` | `task-12`, `v1.1.0` |
| 13 | `Task_13_Configuration_Management_Script.docx` | `docs/13-ansible-configuration.md` | `task-13` |
| 14 | `Task_14_Automated_Provisioning_and_Reliability_Validation.docx` | `docs/14-provisioning-rollback.md` | `task-14`, `v1.2.0` |
| 15 | `Task_15_Final_EndtoEnd_Release_Documentation_and_Viva.docx` | `docs/15-final-release.md` | `task-15`, `v1.3.0` |

## Structure of each document

| Section | Contents |
|---|---|
| Title block | Task number and title, the **Deliverable** quoted from the task sheet, student details, completion date and git tag |
| 1 … N | Numbered sections covering the work, with numbered subsections |
| Problems met and solved | Where the task hit real issues — cause, fix and the lesson |
| Decisions taken, and why | Each decision with its reasoning — the part a viva question lands on |
| Evidence index | File-to-evidence table pointing into `evidence/` |
| Task N Definition of Done | Scored against the global criteria D1–D12 in `docs/definition-of-done.md` |
| **Outcome** | A closing paragraph stating what the task actually achieved |

## Regenerating

These files are **generated**. Edit the markdown in `docs/`, not the `.docx`:

```bash
bash scripts/build-final-report.sh          # rebuild the consolidated markdown
cd docs/report-generator && npm run build   # rebuild all 16 Word documents
```
