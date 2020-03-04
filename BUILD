java_binary(
    name = "main",
    main_class = "org.pantsbuild.jarjar.Main",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/java/org/pantsbuild/jarjar/help.txt"]),
    deps = [
        "@ant//jar",
        "@asm//jar",
        "@asm_commons//jar",
        "@maven_plugin_api//jar",
    ],
    visibility = ["//visibility:public"],
)

# TODO: a target for tests
