# Task 6 evidence — merge conflict creation and resolution

## What conflicted, and why

Two branches edited **the same region** of `src/main/resources/templates/fragments/layout.html`:
the last `<li>` of the main navigation list.

| Branch | Change | Commit |
|---|---|---|
| `develop` (via `fix/navbar-health-indicator`) | Added an unconditional **Health** link with a status dot, so operations can reach `/actuator/health` in one click | `fix: surface the health endpoint in the navigation` |
| `feature/request-workflow` | Added a role-conditional **My profile** link for `DONOR`, alongside the new Dashboard and Requests links | `feat: add authentication, three roles, request screens and dashboard` |

Git could merge every other hunk in the file automatically. It could not merge this one, because
both branches inserted different content at the same position and Git has no way to know whether
one replaces the other or both belong.

## The conflict as Git reported it

```
$ git merge develop
Auto-merging src/main/resources/templates/fragments/layout.html
CONFLICT (content): Merge conflict in src/main/resources/templates/fragments/layout.html
Automatic merge failed; fix conflicts and then commit the result.

$ git status --short
M  src/main/resources/static/css/app.css
UU src/main/resources/templates/fragments/layout.html
```

`UU` means **both modified** — the file is unmerged on both sides. `app.css` shows plain `M`
because the two branches touched different parts of it, so Git merged it without help.

The conflicted file as Git left it is preserved here as
[`layout.html.conflicted`](layout.html.conflicted), including the markers:

```html
<<<<<<< HEAD
      <li class="nav-item" sec:authorize="hasRole('DONOR')">
        <a class="nav-link" th:classappend="${active == 'me'} ? 'active'"
           th:href="@{/me}" data-testid="nav-me">My profile</a>
=======
      <li class="nav-item">
        <a class="nav-link bb-nav-health" th:href="@{/actuator/health}" target="_blank"
           title="Application health - used by the container probe and Ansible"
           data-testid="nav-health">
          <span class="bb-health-dot"></span> Health
        </a>
>>>>>>> develop
      </li>
```

Reading the markers: `<<<<<<< HEAD` to `=======` is **our** side — the feature branch being merged
*into*. `=======` to `>>>>>>> develop` is **their** side — the branch being merged *in*.

## How it was resolved

**Both changes were kept.** They serve different people and neither replaces the other: the
profile link is for a donor, the health link is for whoever is operating the deployment. The
resolution reorders them so the role-conditional item comes first and the universal item last, and
records the reasoning in a comment in the template itself, so the next person to touch that list
knows why two superficially similar links sit side by side.

One judgement had to be made that Git could not make: **should the Health link be restricted to
STAFF and ADMIN?** It was left unconditional, because `/actuator/health` is deliberately
unauthenticated for the container probe and the Ansible health check (FR-26), so hiding the link
would protect nothing while removing useful information from a donor.

```bash
# resolve by editing the file, then
git add src/main/resources/templates/fragments/layout.html
mvn clean verify          # 178 tests green BEFORE committing the resolution
git commit                # the merge commit records the resolution
```

## Why `mvn clean verify` before committing the resolution

A conflict resolution is the one kind of change that no diff review can fully validate: the result
exists in neither parent, so neither side's tests were ever run against it. Committing an
unverified resolution is how a merge breaks a build that both branches passed. Here, the full suite
was run on the resolved working tree first — 178 tests, green — and only then committed.

## Commands to verify this after the fact

```bash
# the merge commit, and that it has two parents
git log --merges --oneline
git show --stat <merge-commit>

# which side each surviving line came from
git log -1 --format='%H %s' <merge-commit>
git show <merge-commit> -- src/main/resources/templates/fragments/layout.html

# both links are present in the merged template
grep -n 'data-testid="nav-me"'     src/main/resources/templates/fragments/layout.html
grep -n 'data-testid="nav-health"' src/main/resources/templates/fragments/layout.html
```
