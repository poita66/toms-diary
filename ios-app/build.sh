#!/bin/bash

# Tom's Diary - iOS Build Script
# Usage: ./build.sh [clean|build|test|run]

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="TomsDiary"
SCHEME_NAME="TomsDiary"
CONFIGURATION="Debug"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Xcode is installed
check_xcode() {
    if ! command -v xcodebuild &> /dev/null; then
        log_error "xcodebuild not found. Please install Xcode."
        exit 1
    fi
    log_info "Xcode version: $(xcodebuild -version | head -n 1)"
}

# Clean build artifacts
clean() {
    log_info "Cleaning build artifacts..."
    xcodebuild -project "$PROJECT_DIR/$PROJECT_NAME.xcodeproj" \
        -scheme "$SCHEME_NAME" \
        -configuration "$CONFIGURATION" \
        clean
    log_info "Clean complete."
}

# Build the project
build() {
    log_info "Building $SCHEME_NAME..."
    xcodebuild -project "$PROJECT_DIR/$PROJECT_NAME.xcodeproj" \
        -scheme "$SCHEME_NAME" \
        -configuration "$CONFIGURATION" \
        -derivedDataPath "$PROJECT_DIR/Build" \
        build
    
    if [ $? -eq 0 ]; then
        log_info "Build successful!"
        log_info "App location: $PROJECT_DIR/Build/Build/Products/$CONFIGURATION-iphonesimulator/$PROJECT_NAME.app"
    else
        log_error "Build failed!"
        exit 1
    fi
}

# Run tests
test() {
    log_info "Running tests..."
    local simulator="iPad Pro (12.9-inch) (6th generation)"
    
    # Boot simulator if not running
    xcrun simctl boot "$simulator" 2>/dev/null || log_warn "Simulator may already be running"
    
    xcodebuild -project "$PROJECT_DIR/$PROJECT_NAME.xcodeproj" \
        -scheme "$SCHEME_NAME" \
        -configuration "$CONFIGURATION" \
        -destination "platform=iOS Simulator,name=$simulator" \
        test
    
    if [ $? -eq 0 ]; then
        log_info "Tests passed!"
    else
        log_error "Tests failed!"
        exit 1
    fi
}

# Run on simulator
run() {
    log_info "Running on simulator..."
    local simulator="iPad Pro (12.9-inch) (6th generation)"
    
    # List available simulators
    log_info "Available iPad simulators:"
    xcrun simctl list devices available | grep -i ipad
    
    # Boot and run
    xcrun simctl boot "$simulator" 2>/dev/null || log_warn "Simulator may already be running"
    
    xcodebuild -project "$PROJECT_DIR/$PROJECT_NAME.xcodeproj" \
        -scheme "$SCHEME_NAME" \
        -configuration "$CONFIGURATION" \
        -destination "platform=iOS Simulator,name=$simulator" \
        clean build
    
    log_info "App built. Open simulator to see the app."
}

# Show usage
usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  clean   - Clean build artifacts"
    echo "  build   - Build the project"
    echo "  test    - Run tests on simulator"
    echo "  run     - Build and run on simulator"
    echo "  help    - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 build"
    echo "  $0 clean && $0 build"
}

# Main
check_xcode

case "${1:-build}" in
    clean)
        clean
        ;;
    build)
        build
        ;;
    test)
        test
        ;;
    run)
        run
        ;;
    help|--help|-h)
        usage
        ;;
    *)
        log_error "Unknown command: $1"
        usage
        exit 1
        ;;
esac
