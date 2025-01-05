package ai.umos.ambientai.kotlin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

// https://proandroiddev.com/android-interview-series-2024-part-6-kotlin-flows-730f6bf877df
class KotlinFlow {
    /** flow is cold flow. collect 를 하면 방출을 모두 받을 수 있다
     * flow collector 1 received: [0, 1, 2]
     * flow collector 2 received: [0, 1, 2]
     */
    @Test
    fun testColdFlow() = runTest {
        // Regular Flow example
        val coldFlow = flow {
            emit(0)
            emit(1)
            emit(2)
        }

        launch {
            // Calling collect the first time
            coldFlow.collect { value ->
                println("cold flow collector 1 received: $value")
            }

            delay(1000)

            // Calling collect a second time
            coldFlow.collect { value ->
                println("cold flow collector 2 received: $value")
            }
        }
    }

    /** MutableSharedFlow is hot flow. collect 를 하면 방출을 모두 받을 수 있다
     * not receive
     */
    @Test
    fun testHotFlow() = runTest {
        val sharedFlow = MutableSharedFlow<Int>()

        sharedFlow.emit(0)
        sharedFlow.emit(1)
        sharedFlow.emit(2)
        sharedFlow.emit(3)
        sharedFlow.emit(4)

        val job = launch {
            sharedFlow.collect { value ->
                println("SharedFlow collector 1 received: $value")
            }
        }
        job.cancelAndJoin()
        println("SharedFlow collector is ended without receive")
    }

    /** MutableSharedFlow is hot flow. collect 를 하면 방출을 모두 받을 수 있다
     * hot stream은 collect 를 먼저 선언한후 emit 을 해야 받을수있다(sharedflow)
     */
    @Test
    fun testHotFlowSolution() = runTest {
        val sharedFlow = MutableSharedFlow<Int>()

        val job = launch {
            sharedFlow.collect { value ->
                println("SharedFlow collector 1 received: $value")
            }
        }

        // 값을 방출하면서 지연을 추가하여 처리 시간을 보장
        launch {
            sharedFlow.emit(0)
            sharedFlow.emit(1)
            sharedFlow.emit(2)
            sharedFlow.emit(3)
            sharedFlow.emit(4)
        }

        // 충분한 시간을 준 후 수집 작업 완료
        delay(1000)

        println("SharedFlow collector is received")

        job.cancelAndJoin() // 종료 처리
    }

    /**
     * 1. 자식 코루틴이 취소된 경우, 부모 코루틴은 계속 실행됨
     * 자식 코루틴이 취소되더라도, 부모 코루틴은 기본적으로 계속 실행됨.
     * 부모는 자신의 작업을 수행하면서 자식의 취소 상태를 감지하거나 무시할 수 있다.
     * 예를 들어, launch를 사용하여 자식 코루틴을 생성하면 자식이 취소되어도 부모는 영향을 받지 않는다.
     *
     * 출력:
     *  Parent starts
     *  Child starts
     *  Parent continues
     *  Parent ends
     */
    @Test
    fun testCoroutineStructureConcurrency() = runTest {
        val parentJob = launch {
            println("Parent starts")

            val childJob = launch {
                println("Child starts")
                delay(1000)
                println("Child ends")
            }

            delay(500)
            childJob.cancel() // 자식 취소
            println("Parent continues...")
        }

        parentJob.join()
        println("Parent ends")
    }

    /**
     * 2. 자식 코루틴이 취소되면서 예외를 발생시키면 부모 코루틴은 예외를 전파받아 취소될 수 있다.
     * 자식 코루틴에서 발생한 예외는 기본적으로 부모에게 전파된다.
     * 부모는 예외를 처리하지 않으면 취소된다.따라서 자식은 try 문으로 감싸야 한다.
     *
     * 출력:
     * Parent starts
     * Child starts
     * Caught exception in child: Child failed
     * Parent continues
     * Parent ends
     */
    @Test
    fun testCoroutineStructureConcurrency2() = runTest {
        val parentJob = launch {
            println("Parent starts")

            val childJob = launch {
                println("Child starts")
                try {
                    throw Exception("Child failed")
                } catch (e: Exception) {
                    println("Caught exception in child: ${e.message}")
                }
            }
            childJob.join()
            println("Parent continues")
        }

        parentJob.join()
        println("Parent ends")
    }

    /**
     * 3. coroutineScope의 경우
     * coroutineScope 내부에서 자식 중 하나가 예외로 종료되면, 다른 모든 자식도 취소된다.
     * 부모 스코프도 예외를 받습니다.
     *
     * 출력:
     * Parent starts
     * Child starts
     * Caught exception in child: Child failed
     * Parent continues
     * Parent ends
     */
    @Test
    fun testCoroutineStructureConcurrency3() = runTest {
        try {
            coroutineScope {
                launch {
                    println("Child 1 starts")
                    delay(500)
                    println("Child 1 ends")
                }

                launch {
                    println("Child 2 starts")
                    throw Exception("Child 2 failed")
                }
            }
        } catch (e: Exception) {
            println("Caught exception in coroutineScope: ${e.message}")
        }

        println("Parent continues")
    }

    /**
     * 4. supervisorScope 사용
     * supervisorScope를 사용하면, 자식 코루틴의 실패가 부모나 다른 자식에게 전파되지 않습니다.
     * 출력:
     * Parent starts
     * Child starts
     * Caught exception in child: Child failed
     * Parent continues
     * Parent ends
     */
    @Test
    fun testCoroutineStructureConcurrency4() = runTest {
        val parentJob = launch {
            println("Parent starts")

            supervisorScope {
                val childJob = launch {
                    println("Child starts")
                    try {
                        throw Exception("Child failed")
                    } catch (e: Exception) {
                        println("Caught exception: ${e.message}")
                    }
                }
                childJob.join()
            }

            println("Parent continues")
        }

        parentJob.join()
        println("Parent ends")
    }

    @Test
    fun testCoroutineJobAndScope() = runTest {
        val job = Job()
        val scope = CoroutineScope(job + Dispatchers.IO)

        // JOB Structure ConCurrent. 즉, 부모 작업이 취소되면 그 안에 있는 모든 자식 작업도 취소됩니다.
        scope.launch {
            println("이 코루틴은 'job' 수명 주기에 연결됩니다.")
            launch {
                println("이 코루틴은 자식 코루틴1 입니다.")
            }.invokeOnCompletion { throwable ->
                when (throwable) {
                    is CancellationException -> println("Job 코루틴1 Cancel: $throwable")
                    else -> println("Job 코루틴1 is completed without no error")
                }
            }
            launch {
                println("이 코루틴은 자식 코루틴2 입니다.")
            }.invokeOnCompletion { throwable ->
                when (throwable) {
                    is CancellationException -> println("Job 코루틴2 Cancel: $throwable")
                    else -> println("Job 코루틴2 is completed without no error")
                }
            }
        }.invokeOnCompletion { throwable ->
            when (throwable) {
                is CancellationException -> println("Job Parent Cancel: $throwable")
                else -> println("Job Parent is completed without no error")
            }
        }

        job.cancel()
        println("Parent job을 취소하면 계층적 하위 코루틴도 취소합니다.")
    }

    @Test
    fun testCoroutineJobAndScope2() = runTest {
        val job = Job()
        val job2 = Job()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val scope2 = CoroutineScope(job2 + Dispatchers.IO)

        // JOB 계층적입니다. 즉, 부모 작업이 취소되면 그 안에 있는 모든 자식 작업도 취소됩니다.
        scope.launch {
            println("이 코루틴은 'job' 수명 주기에 연결됩니다.")
            scope2.launch {
                println("이 코루틴은 자식 코루틴1 입니다.")
            }.invokeOnCompletion { throwable ->
                when (throwable) {
                    is CancellationException -> println("Job 코루틴1 Cancel: $throwable")
                    else -> println("Job 코루틴1 is completed without no error")
                }
            }
            launch {
                println("이 코루틴은 자식 코루틴2 입니다.")
            }.invokeOnCompletion { throwable ->
                when (throwable) {
                    is CancellationException -> println("Job 코루틴2 Cancel: $throwable")
                    else -> println("Job 코루틴2 is completed without no error")
                }
            }
        }.invokeOnCompletion { throwable ->
            when (throwable) {
                is CancellationException -> println("Job Parent Cancel: $throwable")
                else -> println("Job Parent is completed without no error")
            }
        }

        job2.cancel()
        println("Child job2 취소(코루틴1)는 Parent, 코루틴2에 영향을 주지 않습니다.")
    }

    @Test
    fun testCoroutineJobAndScope3() = runTest {
        val job = Job()
        val scope = CoroutineScope(job + Dispatchers.IO)

        // JOB 계층적입니다. 즉, 부모 작업이 취소되면 그 안에 있는 모든 자식 작업도 취소됩니다.
        scope.launch {
            println("이 코루틴은 'job' 수명 주기에 연결됩니다.")
            launch {
                println("이 코루틴은 자식 코루틴1 입니다.")
                val a = 10 / 0
            }.invokeOnCompletion { throwable ->
                println("Job 코루틴1 ===> $throwable")
                when (throwable) {
                    is CancellationException -> println("Job 코루틴1 Cancel: $throwable")
                    is Exception -> println("Job 코루틴1 Exception: $throwable")
                    else -> println("Job 코루틴1 is completed without no error")
                }
            }
            launch {
                println("이 코루틴은 자식 코루틴2 입니다.")
            }.invokeOnCompletion { throwable ->
                when (throwable) {
                    is CancellationException -> println("Job 코루틴2 Cancel: $throwable")
                    is Exception -> println("Job 코루틴2 Exception")
                    else -> println("Job 코루틴2 is completed without no error")
                }
            }
        }.invokeOnCompletion { throwable ->
            when (throwable) {
                is CancellationException -> println("Job Parent Cancel: $throwable")
                is Exception -> println("Job Parent Exception: $throwable")
                else -> println("Job Parent is completed without no error")
            }
        }

        //scope.cancel()
        println("Child job2을 취소(코루틴1)하여 계층적 하위 코루틴을 취소합니다.")
    }
}
