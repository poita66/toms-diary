// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TomsDiary",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "TomsDiary",
            targets: ["TomsDiary"]
        )
    ],
    targets: [
        .target(
            name: "TomsDiary",
            path: "TomsDiary",
            exclude: [
                "Assets.xcassets",
                "Info.plist",
                "Caveat-Regular.ttf"
            ],
            resources: [
                .process("Resources")
            ]
        )
    ]
)
