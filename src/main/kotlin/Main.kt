import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.tongfei.progressbar.ProgressBar
import java.io.File
import java.nio.file.Files

const val LETTERS = "abcdefghijklmnopqrstuvwxyz"

fun main() {
    val file = File("words_alpha.txt")
    val words = file.readLines()
        .map { it.trim() }
        .filter { it.length == 5 }
        .filter { it.toSet().size == 5 }
    println(words.size)

    val withLetterMap = mutableMapOf<Char, Set<String>>()
    LETTERS.forEach { letter ->
        withLetterMap[letter] = words.filter { letter in it }.toSet()
    }

    val map0 = mutableMapOf(
        listOf<String>() to words
    )
    val map0File = File("Map0.txt")
    Files.write(map0File.toPath(), map0.map { (k, v) -> "${Json.encodeToString(k)} -> ${Json.encodeToString(v)}" })

    calculateNextMap("Map0","Map1", words)
    calculateNextMap("Map1", "Map2", words)
    calculateNextMap("Map2", "Map3", words)
    calculateNextMap("Map3", "Map4", words, storeDone = true)
    calculateNextMap("Map4", "Map5", words, storeDone = true)
    println()
}

fun calculateNextMap(
    oldName: String,
    name: String,
    words: List<String>,
    storeDone: Boolean = false,
    writeResultsToFile: Boolean = true
) {
    val withLetterMap = mutableMapOf<Char, Set<String>>()
    LETTERS.forEach { letter ->
        withLetterMap[letter] = words.filter { letter in it }.toSet()
    }

    val outputFile = File("$name.txt")
    if (outputFile.exists()) {
        println("$name.txt already exists, ignoring")
        return
    }
    println("Calculating $name")

    val inputFile = File("$oldName.txt")
    assert(inputFile.exists()) { "$oldName.txt does not exist" }

    print("Initializing...")
    val progressMax = inputFile.useLines {
        it.filter { it.isNotBlank() }.sumOf {
            val remaining = Json.decodeFromString<List<String>>(it.split(" -> ")[1])
            remaining.size
        }
    }
    print("\r")

    val progressBar = ProgressBar(name, progressMax.toLong())
    var outputSize = 0L
    outputFile.printWriter().use { out ->
        inputFile.useLines {
            it.forEach { line ->
                val (usedWords, remaining1) = line.split(" -> ").map { Json.decodeFromString<List<String>>(it) }
                remaining1.forEachIndexed { index2, word2 ->
                    var remaining2 = remaining1.drop(index2).toSet()
                    val usedChars = (usedWords + word2).joinToString("").toSet()
                    usedChars.forEach { letter ->
                        remaining2 = remaining2 - withLetterMap[letter]!!
                    }
                    if (writeResultsToFile && (storeDone || remaining2.isNotEmpty())) {
                        out.println("${Json.encodeToString(usedWords + word2)} -> ${Json.encodeToString(remaining2)}")
                        outputSize++
                    }
                    progressBar.step()
                }
            }
        }

    }
    progressBar.close()
    println("$name size: $outputSize")
    if (writeResultsToFile) {
        println("Saved results to cache file $name.txt, size: ${outputFile.length().toFileSizeString()}")
    }
    println()
}

fun Long.toFileSizeString(): String {
    val kb = 1024L
    val mb = kb * 1024L
    val gb = mb * 1024L
    val tb = gb * 1024L
    val pb = tb * 1024L
    return when {
        this < kb -> "$this B"
        this < mb -> "${this / kb} KB"
        this < gb -> "${this / mb} MB"
        this < tb -> "${this / gb} GB"
        this < pb -> "${this / tb} TB"
        else -> "${this / pb} PB"
    }
}
