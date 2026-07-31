# GitHub Repository Setup and Branching Strategy

The goal is to initialize a local Git repository for the **Askvocate** project, create a specific branch structure, and upload the code to a new GitHub repository.

## User Review Required

> [!IMPORTANT]
> **Git Environment Issue:** A Git repository was detected at `C:\Users\SUDIP\.git` (your home directory). This is causing Git to track unrelated files from your entire user profile. I propose initializing a **new, dedicated Git repository** inside the `Askvocate` folder to keep the project clean.

> [!WARNING]
> **GitHub CLI (`gh`) Missing:** The GitHub CLI tool is not currently installed or configured in this environment. I cannot automatically create the repository on your GitHub account without it.
>
> **Action Required:**
> 1. Please create an empty repository named **Askvocate** on your GitHub account (https://github.com/new).
> 2. Alternatively, provide a **GitHub Personal Access Token (PAT)** with `repo` permissions, and I can attempt to create it using `curl`.

## Proposed Changes

### Local Git Initialization
- Initialize a new Git repository in `C:\Users\SUDIP\OneDrive\Documents\Askvocate`.
- Create a standard Android `.gitignore` file to avoid tracking build artifacts and IDE settings.
- Commit all existing project files to the `main` branch.

### Branching Structure
- **main**: The stable/production branch.
- **develop**: The integration branch for ongoing work.
- **feature-ai**: Dedicated to AI-related features.
- **feature-auth**: Dedicated to authentication features.
- **feature-booking**: Dedicated to booking features.
- **feature-ui**: Dedicated to UI-related features.

### GitHub Integration
- Add the GitHub repository as a remote origin.
- Push all local branches to the remote repository.

## Verification Plan

### Automated Checks
- Run `git branch -a` to verify all local and remote branches.
- Run `git remote -v` to verify the remote origin configuration.

### Manual Verification
- User to verify the files and branches are visible on GitHub.
