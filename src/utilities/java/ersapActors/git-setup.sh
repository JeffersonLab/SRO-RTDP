#!/bin/bash

# This script helps with Git operations in the devcontainer

# Configure Git to recognize the workspace as safe
git config --global --add safe.directory /workspace

# Check if we're in a Git repository
if [ ! -d ".git" ]; then
    echo "Git repository not found. This is unexpected as the main repository should be mounted."
    echo "Please check your devcontainer configuration."
else
    echo "Git repository found."
fi

# Configure Git user if not already configured
if [ -z "$(git config --get user.name)" ]; then
    echo "Git user name not configured."
    echo "Please enter your name for Git commits:"
    read -r name
    git config --global user.name "$name"
fi

if [ -z "$(git config --get user.email)" ]; then
    echo "Git user email not configured."
    echo "Please enter your email for Git commits:"
    read -r email
    git config --global user.email "$email"
fi

# Show Git status
echo "Git repository status:"
git status

echo ""
echo "Git is now set up and ready to use."
echo "You can use the following commands to work with Git:"
echo "  git add .                  # Add all changes to the staging area"
echo "  git commit -m \"Message\"    # Commit changes with a message"
echo "  git status                 # Check the status of your repository"
echo "  git log                    # View commit history"
echo ""
echo "If you need to push changes to the remote repository, use:"
echo "  git push origin <branch-name>"
echo ""
echo "If you need to pull changes from the remote repository, use:"
echo "  git pull origin <branch-name>" 