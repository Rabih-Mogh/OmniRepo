package app.spark.core

import java.io.File
import kotlin.system.exitProcess

/**
 * Says whether the app will take a pack, before it is published:
 * `./gradlew -q :core:checkPack -Ppack=/path/to/pack.json`. It reads the file exactly as
 * the phone does, lists everything wrong with it, and fails if anything is.
 */
fun main(args: Array<String>) {
    val files = args.filter { it.isNotBlank() }
    if (files.isEmpty()) {
        System.err.println("Usage: ./gradlew -q :core:checkPack -Ppack=/path/to/pack.json")
        exitProcess(2)
    }
    var wrong = false
    for (path in files) {
        val file = File(path)
        if (!file.isFile) {
            System.err.println("$path: no such file")
            wrong = true
            continue
        }
        val reading = Packs.read(file.readText())
        reading.problems.forEach { System.err.println("${file.name}: $it") }
        if (reading.problems.isNotEmpty()) wrong = true
        reading.pack?.let { println("${file.name}: ${it.reels.size} reels and ${it.lessons.size} lessons read") }
    }
    if (wrong) exitProcess(1)
    println("Nothing wrong.")
}
