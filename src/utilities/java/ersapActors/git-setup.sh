#!/bin/bash

# This script helps with Git operations in the devcontainer

# Check if we're in a Git repository
if [ ! -d ".git" ]; then
    echo "Not in a Git repository. Initializing..."
    git init
    git config --global --add safe.directory $(pwd)
    
    # Create .gitignore file if it doesn't exist
    if [ ! -f ".gitignore" ]; then
        echo "Creating .gitignore file..."
        cat > .gitignore << EOF
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# Eclipse
.classpath
.project
.settings/

# IntelliJ IDEA
.idea/
*.iml
*.iws
*.ipr

# VS Code
.vscode/
!.vscode/settings.json
!.vscode/tasks.json
!.vscode/launch.json
!.vscode/extensions.json

# Compiled class files
*.class

# Log files
*.log

# Package files
*.jar
*.war
*.ear

# ERSAP data
ersap-data/

# Generated PCAP files
samples/test.pcap

# OS-specific files
.DS_Store
Thumbs.db
EOF
    fi
    
    # Add all files and make initial commit
    git add .
    git commit -m "Initial commit"
    echo "Git repository initialized."
else
    echo "Already in a Git repository."
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
echo "If you need to connect to a remote repository, use:"
echo "  git remote add origin <repository-url>"
echo "  git push -u origin main" 