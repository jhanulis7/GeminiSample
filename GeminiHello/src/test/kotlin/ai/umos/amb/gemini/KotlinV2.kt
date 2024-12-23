package ai.umos.amb.gemini

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class KotlinV2 {
    @Before
    fun setup() {
    }

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
            .collect {
                println("collect: $it")
            }
    }

    @Test
    fun mapTest2() = runBlocking {
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
            .launchIn(CoroutineScope(Dispatchers.Default))

        delay(6000)
    }

    @Test
    fun callbackFlowTest() = runBlocking {
        val stateFlow = getGearState()

        val job = CoroutineScope(Dispatchers.Default).launch {
            stateFlow.collect { state ->
                println("Current gear state: $state")
            }
        }

        delay(3000)
        println("Stopping collection")
        job.cancelAndJoin()

        delay(3000)
        println("Exiting main")
    }

    private fun getGearState(): StateFlow<String?> = callbackFlow {
        val gear = "gear"
        println("Initializing callbackFlow")

        trySend("gear: $gear").isSuccess

        // 콜백 해제 및 플로우 종료 처리 (job.cancelAndJoin())
        awaitClose {
            println("Cleaning up resources in awaitClose")
        }
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default), // CoroutineScope 사용
        started = SharingStarted.WhileSubscribed(1000), // 구독 중일 때만 활성화
        initialValue = "Initial Value" // 초기값 설정
    )

    @Test
    fun combineFlowTest() = runBlocking {
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
    fun combineFlowTest2() = runBlocking {
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
    fun combineFlowTest3() = runBlocking {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E")
            .onEach { delay(1000) }

        flow1.combine(flow2) { num, str ->
            println("$num$str") // launchIn collect 후 delay 만큼 출력(5A, 5B, 5C)
            "$num$str"
        }.launchIn(CoroutineScope(Dispatchers.Default)) // launchIn 은 별도의 코루틴

        // launchIn 이 별도의 코루틴으로 비동기 돌아서 combineFlowTest3 종료되므로 delay 필요
        delay(3000) // 사용하지 않으려면 Test2 사용, collect 해서 다 방출될때가지 runblocking
    }

    @Test
    fun combineFlowTest4(): Unit = runBlocking {
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
    fun zipFlowTest() = runBlocking {
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
    fun zipFlowTest2() = runBlocking {
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
    fun zipFlowTest3() = runBlocking {
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
    fun mergeFlowTest() = runBlocking {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E", "F")

        merge(flow1, flow2).collect { value ->
            println(value) // 출력: 1, 2, 3, 4, 5, A, B, C, D, E, F
        }
    }

    @Test
    fun mergeFlowTest2() = runBlocking {
        val flow1 = flowOf(1, 2, 3, 4, 5)
        val flow2 = flowOf("A", "B", "C", "D", "E", "F")
            .onEach { delay(1000) }

        // 여러 Flow를 병합하여 한 Flow로 만듭니다. 각 Flow의 데이터가 준비되는 대로 순서에 상관없이 방출됩니다.
        merge(flow1, flow2).collect { value ->
            println(value) // 출력: 1, 2, 3, 4, 5, A, B, C, D, E, F (순서보장없음)
        }
    }
}