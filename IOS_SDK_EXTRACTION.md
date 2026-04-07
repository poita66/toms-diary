# iOS SDK Extraction Guide

## How to Extract the iOS SDK (Free!)

This repository includes a GitHub Actions workflow that extracts the iOS SDK for free using GitHub's macOS runners.

### Step 1: Trigger the Workflow

1. Go to the **Actions** tab in this repository
2. Click on **"Extract iOS SDK"** on the left
3. Click the **green "Run workflow"** button
4. Select the `main` branch and click **Run workflow**

### Step 2: Wait for Completion

The workflow will:
- Run on a macOS runner (~15-30 minutes)
- Install xtool
- Extract the iOS SDK from Xcode
- Package it as a downloadable artifact

### Step 3: Download the SDK

Once the workflow completes:

1. Click on the completed workflow run
2. Scroll down to the **Artifacts** section (bottom of page)
3. Download `ios-sdk` (it's a `.tar.gz` file, ~2-3GB)

### Step 4: Install on Linux

```bash
# Download the tar.gz file from GitHub Actions

# Extract it
tar -xzf ios-sdk.tar.gz

# Move to xtool's expected location
mkdir -p ~/.xtool
mv swift-sdks ~/.xtool/

# Verify
ls -la ~/.xtool/swift-sdks/
```

### Step 5: Build the iOS App

```bash
cd ~/toms-diary/ios-app
xtool dev build
```

## Notes

- **Free**: Uses GitHub's free macOS runners (2000 minutes/month for public repos)
- **Artifacts expire**: Download within 30 days
- **One-time setup**: Once you have the SDK, you can build on Linux forever
- **For releases**: Trigger manually to create a permanent release download

## Alternative: GitHub Release

If you want a permanent download link, trigger the workflow manually and it will also upload to GitHub Releases (if configured).
