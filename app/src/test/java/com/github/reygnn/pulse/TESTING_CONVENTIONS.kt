package com.github.reygnn.pulse

/**
 * ============================================================================
 * PULSE — TEST CONVENTIONS
 * ============================================================================
 *
 * Pulse is a small single-module Android app. The test suite is JUnit 4
 * only, JVM-only, no Android runtime in scope. This file is the technical
 * reference for *why* the suite looks the way it does and how to grow it
 * without breaking the small-and-pragmatic shape.
 *
 * The companion meta document is `app/src/test/CLAUDE.md` — read that
 * first if you want orientation. This file is the canonical answer to
 * "how do I do X correctly?".
 *
 * Conventions imported from the sibling project Kolibri-Launcher are
 * marked as such; they apply 1:1 because the pitfalls are framework-
 * intrinsic, not project-specific.
 * ============================================================================
 */

/**
 * ============================================================================
 * SECTION 1 — JUNIT BASICS, NAMING, STRUCTURE
 * ============================================================================
 *
 * One test class per production file under test. Mirror the package layout:
 *
 *   main/.../workout/HrZone.kt           → test/.../workout/HrZoneTest.kt
 *   main/.../ble/HeartRateManager.kt     → test/.../ble/HeartRateManagerParseTest.kt
 *
 * Suffix `Test` on the class. If a single source file has two distinct
 * responsibilities worth testing in isolation (like HeartRateManager
 * having both BLE plumbing and a pure parser), split into two test
 * classes with descriptive suffixes (`HeartRateManagerParseTest` etc.).
 *
 * Test method names use Kotlin backtick-quoted natural-language names:
 *
 *   @Test fun `flag bit 0 cleared - 0xFF reads as unsigned 255`() { ... }
 *
 * Read like a sentence about behaviour, not a code identifier. Underscores
 * are allowed but rare; prefer hyphens for sub-clauses.
 *
 * One assertion focus per test. Pinning a formula? One test per branch
 * (male vs female, mid-HR vs high-HR), not one mega-test with five
 * assertions. The failure message then names the broken case directly.
 *
 * ============================================================================
 */

/**
 * ============================================================================
 * SECTION 2 — WHAT BELONGS IN A UNIT TEST, AND WHAT DOESN'T
 * ============================================================================
 *
 * Pulse's test suite is deliberately small. The deciding question is:
 *
 *   "Could this code be silently wrong, and would no other layer catch it?"
 *
 * Yes → test it. Examples:
 *   - The Keytel calorie formula in `UserProfile.calculateCalories`. A
 *     transposed coefficient would produce wrong-but-plausible numbers
 *     forever.
 *   - The flag-bit dispatch in `parseHeartRate`. A wrong shift would
 *     produce real-looking BPM values from the wrong byte.
 *   - The zone-boundary lookup in `HrZone.fromHeartRate`. Off-by-one
 *     misclassification is invisible to the user.
 *
 * No → don't test it. Examples:
 *   - GATT-callback ordering — verified by the BLE-spec contract and
 *     by real-device smoke tests.
 *   - Compose UI rendering — visual smoke tests on device cover this.
 *   - Trivial SharedPreferences round-trips — Android guarantees them.
 *
 * "I added a test because I touched the file" is not a justification.
 * Tests only earn their keep by failing on real regressions; tests that
 * never fail are noise.
 *
 * ============================================================================
 */

/**
 * ============================================================================
 * SECTION 3 — VISIBILITY: @VisibleForTesting AND COMPANION FUNCTIONS
 * ============================================================================
 *
 * Pure helpers that are private inside an Android-coupled class can be
 * lifted to the class's `companion object` and marked `internal` plus
 * `@VisibleForTesting` (from `androidx.annotation`). The pattern in
 * use:
 *
 *   class HeartRateManager(...) {
 *       companion object {
 *           @VisibleForTesting
 *           internal fun parseHeartRate(data: ByteArray): Int { ... }
 *       }
 *       // production code calls parseHeartRate(value) — companion access
 *       // is implicit from inside the class.
 *   }
 *
 *   // In the test:
 *   import com.github.reygnn.pulse.ble.HeartRateManager.Companion.parseHeartRate
 *
 * Why this and not "make it public"? `internal` keeps it out of the
 * public API surface; `@VisibleForTesting` makes IDE and lint flag any
 * non-test consumer.
 *
 * Don't use this as a wedge for testing inappropriately glued code. If
 * the helper depends on Android `Context` or system services, the right
 * answer is usually a refactor that pulls the pure part out into its
 * own free-standing function — not @VisibleForTesting on a method that
 * still touches the framework.
 *
 * ============================================================================
 */

/**
 * ============================================================================
 * SECTION 4 — DRIFT DOCUMENTATION OVER HIDDEN DIVERGENCE
 * ============================================================================
 *
 * Imported from Kolibri-Launcher; the principle scales down to a
 * single-implementation project too.
 *
 * If a test deliberately differs from production — say, the test
 * accepts an input the production code rejects, or skips a side-effect
 * production performs — that intent must be a comment in the test, not
 * an implicit assumption. Future-you (or the next Claude session) will
 * read the test cold and trust the comment to explain whether the
 * divergence is correct or a bug worth chasing.
 *
 * Same applies in the opposite direction: if the production code has a
 * known sharp edge that the test does not exercise (e.g. `parseHeartRate`
 * with a payload of length 4+ where bytes 3..n are flag-extension data),
 * a comment in the test class declares the gap and links to the spec
 * note that justifies it.
 *
 * ============================================================================
 */

/**
 * ============================================================================
 * SECTION 5 — WHEN THE TEST GOES RED
 * ============================================================================
 *
 * Default rule: **production behaviour is truth, the test follows.**
 * Production code runs against real data and real users. A failing test
 * that asserted a wrong belief about the formula needs to be corrected,
 * not its production counterpart.
 *
 * The exception is a real bug. Three shapes to recognise:
 *
 *   1. The test makes a spec-grounded claim and the code violates the
 *      spec (e.g. a parser ignoring a flag bit it should honour).
 *      Fix code.
 *
 *   2. The test pins a numerical answer derived from a formula and the
 *      code's output is off. Recompute by hand. If the test's hand-
 *      derivation was wrong, fix the test. If the code is computing
 *      something different from what the formula says, fix the code.
 *
 *   3. The test passed before, the production change introduced the
 *      red, and the change is meant to be a refactor (no behaviour
 *      change). The refactor regressed something — fix the code.
 *
 * Every red is one of these three. Saying "I corrected the test" without
 * naming which case it was is a code smell.
 *
 * ============================================================================
 */

/**
 * ============================================================================
 * SECTION 6 — WHEN VIEWMODEL TESTS DAZUKOMMEN (FUTURE)
 * ============================================================================
 *
 * Pulse currently has no ViewModel tests. When the first one lands, it
 * will pull in:
 *
 *   testImplementation("io.mockk:mockk:<version>")
 *   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:<version>")
 *   testImplementation("app.cash.turbine:turbine:<version>")
 *
 * The conventions below are pre-imported from Kolibri-Launcher; they
 * apply 1:1 because the framework pitfalls are universal. They are
 * dormant until the first VM test is written.
 *
 * ----------------------------------------------------------------------------
 * 6.1 — ONE DISPATCHER, ONE SOURCE
 * ----------------------------------------------------------------------------
 *
 *   @get:Rule
 *   val mainDispatcherRule = MainDispatcherRule()  // sets Dispatchers.Main
 *
 *   @Test fun whatever() = runTest(mainDispatcherRule.dispatcher) {
 *       // ...
 *   }
 *
 * Plain `runTest { }` creates its own `TestCoroutineScheduler`, so
 * `advanceUntilIdle()` / `advanceTimeBy()` will not drive code that
 * runs on `Dispatchers.Main`. The result is silent flakiness.
 *
 * Never instantiate a second `StandardTestDispatcher()` or `TestScope()`
 * inside a test that already has the rule. One dispatcher, one
 * scheduler — full stop.
 *
 * (This requires `HeartRateViewModel` to take its main dispatcher as a
 * constructor parameter, which it currently does NOT. Adding the rule
 * meaningfully implies that refactor first.)
 *
 * ----------------------------------------------------------------------------
 * 6.2 — MOCKK NAMING AND RELAXED FLAGS
 * ----------------------------------------------------------------------------
 *
 * Variables are named after what they represent, no `mock` prefix:
 *
 *   private val service: HeartRateService = mockk(relaxed = true)   // ✅
 *   private val mockService: HeartRateService = mockk(relaxed = true) // ❌
 *
 * For mocks of types with suspend-Unit methods, use `relaxed = true`.
 * `relaxUnitFun = true` does NOT cover suspend functions — the call
 * compiles, then crashes at runtime with `MockKException: no answer
 * found`.
 *
 *   val mock = mockk<X>(relaxUnitFun = true)  // ❌ crashes on x.suspendFn()
 *   val mock = mockk<X>(relaxed = true)       // ✅ both covered
 *
 * Suspend functions are stubbed with `coEvery`, verified with
 * `coVerify`. Properties (incl. Flow-typed `val`s) and non-suspend
 * functions use `every` / `verify`. Mixing these gives confusing
 * runtime errors, not compile errors.
 *
 * `coAnswers` does not exist. For arg-based suspend returns, use
 * `coEvery { ... } answers { secondArg() }`. The `answers` lambda is
 * always synchronous even for suspend stubs.
 *
 * For `MutableSharedFlow<Unit>`, stub with `emit(Unit)`, not
 * `emit(any())` — MockK's `any()` does not reliably match `Unit`.
 *
 * ----------------------------------------------------------------------------
 * 6.3 — TIME-BASED ASSERTIONS
 * ----------------------------------------------------------------------------
 *
 * Anything that uses `delay`, `withTimeout`, or relative timing in
 * production needs at least one test that asserts on virtual time, not
 * just on the final emitted value. Two idioms:
 *
 *   `testScheduler.currentTime` snapshot for *relative* assertions
 *   (was the second call as fast as the first?), `advanceTimeBy(N)` +
 *   boundary checks for *absolute* ones (does the timeout fire at N?).
 *
 * Pulse only has one site that would benefit today:
 * `HeartRateViewModel.recordSample`'s 5-second long-term throttle. It
 * currently uses `System.currentTimeMillis()` directly, so it is not
 * testable in virtual time without a `Clock` abstraction. Either
 * accept that gap or refactor first.
 *
 * ============================================================================
 */
