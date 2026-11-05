package hr.bertlab.fluteassistant.debug.session

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A tool that happens to be a test: replays the recorded session named by the `fa.session`
 * system property (the build passes `-Psession=<folder>`, relative to the repository root)
 * and writes `replay.txt` into its folder, plus `sweep.txt` when `-Psweep=<rule>:<v1>,...`
 * is given (`ui/flute/CLAUDE.md`, "Sessions"). Skipped when no session is named; never fails
 * on a diff. Run it with
 * `./gradlew :app:testDebugUnitTest --tests '*SessionReplayTest*' -Psession=sessions/<folder>`.
 */
class SessionReplayTest {

    @Test
    fun `replays the session named by fa session`() {
        val path = System.getProperty("fa.session").orEmpty()
        assumeTrue("no -Psession=<folder> given", path.isNotBlank())
        val dir = File(path)
        require(dir.isDirectory) { "no session folder at $dir" }
        val session = Session.load(dir)
        val report = Replay.report(session)
        File(dir, "replay.txt").writeText(report)
        println(report)
        println("replay written to ${File(dir, "replay.txt")}")
        val sweep = System.getProperty("fa.sweep").orEmpty()
        if (sweep.isNotBlank()) {
            val table = Replay.sweep(session, sweep)
            File(dir, "sweep.txt").writeText(table)
            println(table)
            println("sweep written to ${File(dir, "sweep.txt")}")
        }
    }
}
