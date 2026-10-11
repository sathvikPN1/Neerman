# Key follow-up prompts

Follow-up prompts given after the master prompt ([01-phase1-master-prompt.md](01-phase1-master-prompt.md)),
with what each one led to. They are mostly about delivery: running the project, version control and
course alignment. Feature work for Phase 2 onwards will add new files here.

## 1. Running the project

> "how do i run this project"

**Outcome:** run instructions for Docker Compose (`docker compose up --build`) and for running the
app locally against a Postgres container, plus demo logins per role.

## 2. Aligning delivery with the course

> "this is part of my project … push only files which are human doable in one day … comments are one of
> the most important thing … https://www.cse.iitm.ac.in/~krishna/cs5013/projects.html — we need to follow
> this to max our marks"

**Context given:** the CS5013 project page (grading criteria, AI-usage statement, continuous commits,
explain-any-code-at-the-viva).

**Refined into a concrete rule:** each commit is one module that

- compiles on its own on top of the previous commit,
- passes its own tests and starts against an empty database,
- has a descriptive message and an honest AI-assisted note (`Co-Authored-By` trailer),
- is reviewed before pushing, because the viva checks understanding.

The assistant also stated what it would not do: no backdated commits and no invented history.

## 3. Connecting to GitHub

> "give me command to connect it to my github" · "how do i add my ssh key" · *(screenshot of GitHub's
> "Add new SSH key" page)*

**Outcome:** `ssh -T git@github.com` showed the existing key belonged to another GitHub account, so a
separate key (`id_ed25519_sathvik`) and an SSH host alias (`github-sathvik`) were created for this project.

## 4. Pushing module slices

> "make the push the next set now" · "give me the next set of files to push" · "make a list for today and
> push it 20 files"

**Outcome:** nine commits, 1–8 October 2026: skeleton and schema → users and permissions → security →
money and time helpers → teams, cohorts and notifications → claim state machine → documents and storage →
payments and claim services → screens, demo data and integration tests. Each slice was built and tested on
its own before it was pushed.

## 5. Mid-evaluation document

> "this is the guideline doc for my mid-term evaluation; help me make a similar evaluation doc for my
> project with my information and give me the output here first; if everything is good, then give it in PDF"

**Technique:** a draft is reviewed in chat before the final format is produced. Placeholders are used for
missing facts instead of guessing, and the draft calls out any claim that wasn't yet true (for example, this
`docs/prompts/` folder) so it could be fixed before submission.
