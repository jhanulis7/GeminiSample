package ai.umos.ambientai.kotlin

import ai.umos.ambientai.ui.event.SystemEvent
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.coroutines.cancellation.CancellationException

@Config(application = HiltTestApplication::class, manifest = Config.NONE)
class KotlinV2 {
    private var _isSleepingState = MutableStateFlow(true)
    private val isSleepingState = _isSleepingState.asStateFlow()

    private var _uiStateSystemEvent = MutableStateFlow(SystemEvent.None)
    private val uiStateSystemEvent = _uiStateSystemEvent.asStateFlow()

    @Test
    fun mapTest() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)

        flow1
            .onStart {
                println("onStart")
            }
            .onEach {
                delay(1000)
                println("onEach: $it")
            }
            .map {
                println("map: $it")
                "map:$it"
            }
            .onCompletion {
                println("onCompletion: $it")
            }
            // flow 는 cold stream 이므로 collect 해야 emit 발생됨
            .collect {
                println("collect: $it")
            }
    }

    @Test
    fun mapTest2() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)

        val job = flow1
            .onStart {
                println("onStart")
            }
            .onEach {
                delay(1000)
                println("onEach: $it")
            }
            .map {
                println("map: $it")
                "map:$it"
            }
            // flow 는 cold stream 이므로 collect 해야 emit 발생됨. collect 를 사용 하지 않고 launch 시킴
            .launchIn(CoroutineScope(Dispatchers.Default))
        job.join()
        println("End")
    }

    private fun getGearState(scope: CoroutineScope): StateFlow<String?> = callbackFlow {
        val gear = "gear"
        println("Initializing callbackFlow")

        trySend("gear: $gear").isSuccess

        awaitClose {
            println("Cleaning up resources in awaitClose")
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly, // 즉시 시작하도록 변경
        initialValue = "Initial Value"
    )

    @Test
    fun callbackFlowTest() = runTest {
        // 테스트용 스코프 생성
        val testScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val stateFlow = getGearState(testScope)

        val job = testScope.launch {
            stateFlow.collect { state ->
                println("Current gear state: $state")
            }
        }

        // 구독 취소 및 awaitClose 호출
        println("Stopping collection")
        job.cancelAndJoin()
        println("End")
        testScope.cancel() // 스코프 취소
    }

    @Test
    fun combineFlowTest() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E")

        flow1.combine(flow2) { num, str ->
            "$num$str"
        }.collect {
            // Answer: 1A 2B 3C 4D 5E
            println(it)
        }
    }

    @Test
    fun combineFlowTest2() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E")
            .onEach { delay(1000) }

        flow1.combine(flow2) { num, str ->
            "$num$str"
        }.collect {
            // Answer: 5A 5B 5C 5D 5E
            println(it)
        }
    }

    @Test
    fun combineFlowTest3() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E")
            .onEach { delay(1000) }

        val job = flow1.combine(flow2) { num, str ->
            println("$num$str") // launchIn collect 후 delay 만큼 출력(5A, 5B, 5C)
            "$num$str"
        }.launchIn(CoroutineScope(Dispatchers.Default)) // launchIn 은 별도의 코루틴

        job.join()
    }

    @Test
    fun combineFlowTest4(): Unit = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E")
            .onEach { delay(1000) }
        flow1.combine(flow2) { num, str ->
            "$num$str"
        }.stateIn(
            scope = this, // runBlocking 스코프 사용
            started = SharingStarted.Lazily,
            initialValue = "Initial" // 초기값은 String으로 설정
        ).take(6).collect { value ->
            println(value) // 출력: Initial, 1A, 2B, ...
        }
    }

    @Test
    fun zipFlowTest() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E")

        flow1.zip(flow2) { num, str ->
            "$num$str"
        }.collect { result ->
            // Answer: 1A 2B 3C 4D 5E
            println(result)
        }
    }

    @Test
    fun zipFlowTest2() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E")
            .onEach { delay(1000) }

        flow1.zip(flow2) { num, str ->
            "$num$str"
        }.collect { result ->
            // Answer: 1A 2B 3C 4D 5E, onEach delay 가 있어도 각 방출 1개씩
            println(result)
        }
    }

    @Test
    fun zipFlowTest3() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E", "F")
            .onEach { delay(1000) }

        flow1.zip(flow2) { num, str ->
            "$num$str"
        }.collect { result ->
            // Answer: 1A 2B 3C 4D 5E 끝, "F" 는 zip 되지 않는다.
            println(result)
        }
    }

    @Test
    fun mergeFlowTest() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E", "F")

        merge(flow1, flow2).collect { value ->
            println(value) // 출력: 1, 2, 3, 4, 5, A, B, C, D, E, F
        }
    }

    @Test
    fun mergeFlowTest2() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E", "F")
            .onEach { delay(1000) }

        // 여러 Flow를 병합하여 한 Flow로 만듭니다. 각 Flow의 데이터가 준비되는 대로 순서에 상관없이 방출됩니다.
        merge(flow1, flow2).collect { value ->
            println(value) // 출력: 1, 2, 3, 4, 5, A, B, C, D, E, F (순서보장없음)
        }
    }

    @Test
    fun mergeFlowTest3() = runTest {
        val flow1 = flowOf(1, 2, 3, 4, 5).onEach {
            delay(1000)
        }
        val flow2 = flowOf("A", "B", "C", "D", "E", "F").onEach {
            delay(500)
        }

        merge(flow1, flow2).collect { value ->
            println(value) // 출력: A, 1, B, C, 2, D, E, 3, F, 4, 5
        }
    }

    @Test
    fun collectChangeWindowFlagStateTest_scope() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)

        try {
            isSleepingState.combine(uiStateSystemEvent) { isSleeping, systemEvent ->
                println("---> collectChangeWindowFlagState isSleeping : $isSleeping, systemEvent : $systemEvent")
            }.stateIn(testScope, SharingStarted.Eagerly, true)

            _isSleepingState.value = false
            _uiStateSystemEvent.value = SystemEvent.GearReverse

            yield()
            advanceTimeBy(2000)
        } catch (e: Exception) {
            println("collectChangeWindowFlagStateTest2 error:${e.stackTraceToString()}")
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun collectChangeWindowFlagStateTest2_scopeCancel() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)

        try {
            isSleepingState.combine(uiStateSystemEvent) { isSleeping, systemEvent ->
                println("--> collectChangeWindowFlagState isSleeping : $isSleeping, systemEvent : $systemEvent")
            }.launchIn(testScope)

            _isSleepingState.value = false
            _uiStateSystemEvent.value = SystemEvent.GearReverse

            yield()
            advanceTimeBy(2000)
        } catch (e: Exception) {
            println("collectChangeWindowFlagStateTest2 error:${e.stackTraceToString()}")
        } finally {
            testScope.cancel()
        }
    }

    private suspend fun fetchTest1(): String {
        println("fetchTest1 Start")
        delay(1000)
        println("fetchTest1 End")
        return "fetchTest1"
    }

    private suspend fun fetchTest2(): String {
        println("fetchTest2 Start")
        delay(2000)
        println("fetchTest2 End")
        return "fetchTest2"
    }

    @Test
    fun testFlowAsync() = runTest {
        val deferred1 = async { fetchTest1() }
        val deferred2 = async { fetchTest2() }

        // 두 결과를 모두 기다림
        val result = deferred1.await() + deferred2.await()
        println(result)
    }
}
