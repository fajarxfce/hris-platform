import org.gradle.api.tasks.testing.Test

val shardArgument = providers.gradleProperty("hrisApiTestShard")
val countArgument = providers.gradleProperty("hrisApiTestShards")

if (shardArgument.isPresent || countArgument.isPresent) {
    val shard = shardArgument.orNull?.toIntOrNull()
    val count = countArgument.orNull?.toIntOrNull()
    require(shard != null && count != null && count in 1..16 && shard in 1..count) {
        "Set hrisApiTestShard and hrisApiTestShards together, with 1 <= shard <= count <= 16"
    }

    tasks.withType<Test>().configureEach {
        inputs.property("hrisApiTestShard", shard)
        inputs.property("hrisApiTestShards", count)
        doFirst {
            val selected = mutableSetOf<String>()
            candidateClassFiles.visit {
                if (!isDirectory && name.endsWith(".class")) {
                    val path = relativePath.pathString
                    // Nested tests and their enclosing class always share one owner.
                    val owner = path.substringBefore('$').removeSuffix(".class")
                    val assigned =
                        if (owner == "dev/fajar/hris/ApiContractHttpTest") 1
                        else Math.floorMod(owner.hashCode(), count) + 1
                    if (assigned == shard) selected += path
                }
            }
            check(selected.isNotEmpty()) { "API test shard $shard/$count has no class files" }
            setIncludes(selected)
            logger.lifecycle("API test shard $shard/$count: ${selected.size} class files")
        }
    }
}
