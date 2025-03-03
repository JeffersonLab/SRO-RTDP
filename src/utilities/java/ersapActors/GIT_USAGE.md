# Git Usage Guide for ERSAP Actors Project

This document explains how to use Git with the ERSAP Actors project, especially when working with the devcontainer.

## Repository Structure

The ERSAP Actors project is part of a larger Git repository. The main repository is located at the root of the project, and the `ersapActors` directory is a subdirectory of this repository.

## Important: Avoiding Nested Git Repositories

It's crucial to ensure that there is no `.git` directory inside the `ersapActors` folder. If a `.git` directory exists there, it will cause Git to treat the `ersapActors` directory as a separate repository, and changes in that directory won't be tracked by the main repository.

If you encounter issues where Git shows that all files outside of `ersapActors` are being deleted, check for a nested `.git` directory and remove it:

```bash
# Check if there's a nested .git directory
ls -la /path/to/ersap-actors/src/utilities/java/ersapActors/.git

# If it exists, remove it
rm -rf /path/to/ersap-actors/src/utilities/java/ersapActors/.git
```

## Using Git in the Devcontainer

When you open the project in the devcontainer, the main Git repository is mounted into the container. This allows you to use Git commands within the container to interact with the repository.

### Initial Setup

When you first open the devcontainer, the setup script will:

1. Configure Git to recognize the workspace as a safe directory
2. Mount your local `.gitconfig` file to share your Git configuration with the container

### Git Commands

You can use standard Git commands within the devcontainer:

```bash
# Check the status of your repository
git status

# Add changes to the staging area
git add .

# Commit changes
git commit -m "Your commit message"

# Push changes to the remote repository
git push origin <branch-name>

# Pull changes from the remote repository
git pull origin <branch-name>
```

### Git Helper Script

A helper script is provided to assist with Git operations in the devcontainer. You can run it with:

```bash
./git-setup.sh
```

This script will:
1. Configure Git to recognize the workspace as a safe directory
2. Check if the Git repository is properly mounted
3. Configure your Git user name and email if not already set
4. Show the current Git status
5. Provide helpful information about Git commands

## Troubleshooting

If you encounter issues with Git in the devcontainer:

1. **Git repository not found**: Make sure the devcontainer is properly configured to mount the main Git repository. Check the `devcontainer.json` file to ensure the `.git` directory is mounted correctly.

2. **Permission denied**: Run the following command to add the workspace as a safe directory:
   ```bash
   git config --global --add safe.directory /workspace
   ```

3. **Git user not configured**: Run the `git-setup.sh` script to configure your Git user name and email.

4. **Changes not being tracked**: Make sure you're working in the correct directory and that the files you're modifying are part of the main repository.

5. **Git shows all files outside ersapActors as deleted**: Check for a nested `.git` directory inside the `ersapActors` folder and remove it if it exists.

## Best Practices

1. Always pull the latest changes before starting work to avoid conflicts.
2. Commit your changes frequently with descriptive commit messages.
3. Push your changes to the remote repository regularly to back up your work.
4. Create feature branches for new features or bug fixes.
5. Use meaningful commit messages that describe what changes were made and why. 