import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream

/*
This solution runs under a second

Java code from: https://gist.github.com/fredoverflow/e3e0b5be677fd44500071356faabcf27
https://www.youtube.com/watch?v=GTsP8ss5gjk&ab_channel=FredOverflow

Matt Parker
Can you find: five five-letter words with twenty-five unique letters?
FJORD
GUCKS
NYMPH
VIBEX
WALTZ
Q
Matt:     32 days
Benjamin: 15 minutes
Fred:      1 second
Constraints:
- No duplicate letters (valid words have 5 unique characters)
- Order of letters irrelevant (ignore anagrams during search)
- i.e. Word is Set of Characters
Representation:
- 32-bit number
- 26 bits for the letters A-Z
- 6 bits unused
25                       0
ABCDEFGHIJKLMNOPQRSTUVWXYZ
   1 1   1    1  1         fjord
---D-F---J----O--R-------- fjord
--C---G---K-------S-U----- gucks
-------------------------- fjord AND gucks (INTERSECTION)
--CD-FG--JK---O--RS-U----- fjord OR gucks (UNION)
*/
private val rawWords: MutableList<String> = mutableListOf()

fun main() {
    val stopwatch = Stopwatch()
    rawWords.addAll(Files.readAllLines(Path.of("wordle-nyt-answers-alphabetical.txt")))
    rawWords.addAll(Files.readAllLines(Path.of("wordle-nyt-allowed-guesses.txt")))
    println("${rawWords.size} raw words")
    val cookedWords = rawWords.stream() // A---E------L---P---------- apple
        // --C-----I--L-----------XY- cylix
        // --C-----I--L-----------XY- xylic
        .mapToInt { encodeWord(it) } // remove words with duplicate characters
        .filter { Integer.bitCount(it) == 5 }.sorted() // remove anagrams
        .distinct()
        .toArray()
    println("${cookedWords.size} cooked words\n")

    // 54 MB  skip[i][j] is the first i-intersection-free index >= j
    val skip = Array(cookedWords.size) { ShortArray(cookedWords.size + 1) }
    for (i in cookedWords.indices) {
        skip[i][cookedWords.size] = cookedWords.size.toShort() // 5176
        val A = cookedWords[i]
        for (j in cookedWords.size - 1 downTo i) {
            val B = cookedWords[j]
            skip[i][j] = if (A and B == 0) j.toShort() else skip[i][j + 1]
        }
    }
    // 20 KB  first[i] is identical to skip[i][i] but hot in cache
    val first = IntArray(cookedWords.size)
    for (i in cookedWords.indices) {
        first[i] = skip[i][i].toInt()
    }

    // for (int i = 0; i < cookedWords.length; ++i) {
    IntStream.range(0, cookedWords.size).parallel().forEach { i: Int ->
        val A = cookedWords[i]

        // for (int j = i + firstStep[i]; j < cookedWords.length; ++j) {
        var j = first[i]
        while (j < cookedWords.size) {
            val B = cookedWords[j] // lurks  monty  zloty
            val AB = A or B

            // for (int k = j + firstStep[j]; k < cookedWords.length; ++k) {
            var k = first[j]
            while (k < cookedWords.size) {
                val C = cookedWords[k]
                if (AB and C != 0) {
                    k++
                    k = skip[i][k].toInt()
                    k = skip[j][k].toInt()
                    continue
                }
                val ABC = AB or C

                // for (int l = k + firstStep[k]; l < cookedWords.length; ++l) {
                var l = first[k]
                while (l < cookedWords.size) {
                    val D = cookedWords[l]
                    if (ABC and D != 0) {
                        l++
                        l = skip[i][l].toInt()
                        l = skip[j][l].toInt()
                        l = skip[k][l].toInt()
                        continue
                    }
                    val ABCD = ABC or D

                    // for (int m = l + firstStep[l]; m < cookedWords.length; ++m) {
                    var m = first[l]
                    while (m < cookedWords.size) {
                        val E = cookedWords[m]
                        if (ABCD and E != 0) {
                            m++
                            m = skip[i][m].toInt()
                            m = skip[j][m].toInt()
                            m = skip[k][m].toInt()
                            m = skip[l][m].toInt()
                            continue
                        }
                        System.out.printf(
                            "%s\n%s\n\n", stopwatch.elapsedTime(), decodeWords(A, B, C, D, E)
                        )
                        m++
                        m = skip[i][m].toInt()
                        m = skip[j][m].toInt()
                        m = skip[k][m].toInt()
                        m = skip[l][m].toInt()
                    }
                    l++
                    l = skip[i][l].toInt()
                    l = skip[j][l].toInt()
                    l = skip[k][l].toInt()
                }
                k++
                k = skip[i][k].toInt()
                k = skip[j][k].toInt()
            }
            j++
            j = skip[i][j].toInt()
        }
    }
    println(stopwatch.elapsedTime())
}

private fun decodeWord(word: Int): String {
    // --C-----I--L-----------XY- cylix/xylic
    return rawWords.stream()
        .filter { encodeWord(it) == word }
        .collect(Collectors.joining("/", visualizeWord(word) + " ", ""))
}

private fun decodeWords(vararg words: Int): String {
    // ----E-----K-M--P---T------ kempt
    // ---D---H------O------V---Z vozhd
    // --C-----I--L-----------XY- cylix/xylic
    // -B----G------N---R--U----- brung
    // A----F----------Q-S---W--- waqfs
    return Arrays.stream(words).mapToObj(::decodeWord).collect(Collectors.joining("\n"))
}

private fun encodeWord(raw: String): Int {
    //    1 1   1    1  1         fjord
    var bitset = 0
    for (element in raw) {
        bitset = bitset or (1 shl 26 shr element.code)
    }
    return bitset
}

private fun visualizeWord(inputWord: Int): String {
    //    1 1   1    1  1
    // ---D-F---J----O--R--------
    var word = inputWord
    val a = CharArray(26)
    word = word shl 6
    var i = 0
    while (i < a.size) {
        a[i] = if (word < 0) ('A'.code + i).toChar() else '-'
        ++i
        word = word shl 1
    }
    return String(a)
}


internal class Stopwatch {
    private val start = Instant.now()
    fun elapsedTime(): String {
        val now = Instant.now()
        val duration = Duration.between(start, now).truncatedTo(ChronoUnit.MILLIS)
        val formatted = DateTimeFormatter.ISO_LOCAL_TIME.format(duration.addTo(LocalTime.of(0, 0)))
        return "[$formatted] "
    }
}