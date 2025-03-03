# Git Usage Guide for ERSAP Actors Project

This document explains how to use Git with the ERSAP Actors project, especially when working with the devcontainer.

## Repository Structure

The ERSAP Actors project is organized as follows:
```
ersap-actors/                    # Main project root
├── src/
│   └── utilities/
│       ├── java/
│       │   └── ersapActors/    # ERSAP Actors Java code
│       └── cpp/
│           └── pcap2stream/    # PCAP stream utilities
└── ... other project files
```

When using the devcontainer, the entire project is mounted at `/workspace` to ensure proper Git functionality.

## Using Git in the Devcontainer

When you open the project in the devcontainer, the entire project repository is mounted into the container at `/workspace`. This ensures that Git can track all files correctly and maintain the proper repository structure.

### Initial Setup

When you first open the devcontainer, the setup script will:

1. Configure Git to recognize the workspace as a safe directory
2. Mount your local `.gitconfig` file to share your Git configuration with the container

### Working Directory

Most of your work will be in the `/workspace/src/utilities/java/ersapActors` directory, but Git will track changes across the entire project. When you run Git commands, make sure you're aware of your current working directory:

```bash
# Check your current directory
pwd

# Change to the ERSAP Actors directory
cd /workspace/src/utilities/java/ersapActors

# Change to the project root
cd /workspace
```

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

1. **Git repository not found**: Make sure you're in the correct directory. The entire project is mounted at `/workspace`.

2. **Permission denied**: Run the following command to add the workspace as a safe directory:
   ```bash
   git config --global --add safe.directory /workspace
   ```

3. **Git user not configured**: Run the `git-setup.sh` script to configure your Git user name and email.

4. **Changes not being tracked**: Make sure you're working in the correct directory structure under `/workspace`.

5. **Files appear to be deleted**: If Git shows files as deleted that shouldn't be, check that you're at the project root (`/workspace`) when running Git commands that affect the whole repository.

## Best Practices

1. Always pull the latest changes before starting work to avoid conflicts.
2. Commit your changes frequently with descriptive commit messages.
3. Push your changes to the remote repository regularly to back up your work.
4. Create feature branches for new features or bug fixes.
5. Use meaningful commit messages that describe what changes were made and why.
6. Be aware of your working directory when running Git commands.
7. When making changes that affect multiple parts of the project, run Git commands from the project root (`/workspace`). 