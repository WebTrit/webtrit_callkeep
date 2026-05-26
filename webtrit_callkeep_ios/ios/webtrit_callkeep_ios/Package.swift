// swift-tools-version: 5.9
import PackageDescription

let package = Package(
  name: "webtrit_callkeep_ios",
  platforms: [
    .iOS("13.0"),
  ],
  products: [
    .library(name: "webtrit-callkeep-ios", targets: ["webtrit_callkeep_ios"]),
  ],
  targets: [
    .target(
      name: "webtrit_callkeep_ios",
      cSettings: [
        .headerSearchPath("include/webtrit_callkeep_ios"),
      ]
    ),
  ]
)
