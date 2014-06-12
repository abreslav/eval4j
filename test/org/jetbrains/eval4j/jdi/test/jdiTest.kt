package org.jetbrains.eval4j.jdi.test

import org.jetbrains.eval4j.*
import com.sun.jdi
import junit.framework.TestSuite
import org.jetbrains.eval4j.test.buildTestSuite
import junit.framework.TestCase
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.eval4j.jdi.*
import org.objectweb.asm.Type
import java.io.File
import com.sun.jdi.ArrayReference

val DEBUGEE_CLASS = javaClass<Debugee>()

fun suite(): TestSuite {
    val connectors = jdi.Bootstrap.virtualMachineManager().launchingConnectors()
    val connector = connectors[0]
    println("Using connector $connector")

    val connectorArgs = connector.defaultArguments()

    val debugeeName = DEBUGEE_CLASS.getName()
    println("Debugee name: $debugeeName")
    connectorArgs["main"]!!.setValue(debugeeName)
    connectorArgs["options"]!!.setValue("-classpath out/production/eval4j:out/test/eval4j")
    val vm = connector.launch(connectorArgs)!!

    val req = vm.eventRequestManager().createClassPrepareRequest()
    req.addClassFilter("*.Debugee")
    req.enable()

    val latch = CountDownLatch(1)
    var classLoader : jdi.ClassLoaderReference? = null
    var thread : jdi.ThreadReference? = null

    Thread {
        val eventQueue = vm.eventQueue()
        @mainLoop while (true) {
            val eventSet = eventQueue.remove()
            for (event in eventSet.eventIterator()) {
                when (event) {
                    is jdi.event.ClassPrepareEvent -> {
                        val _class = event.referenceType()!!
                        if (_class.name() == debugeeName) {
                            for (l in _class.allLineLocations()) {
                                if (l.method().name() == "main") {
                                    classLoader = l.method().declaringType().classLoader()
                                    val breakpointRequest = vm.eventRequestManager().createBreakpointRequest(l)
                                    breakpointRequest.enable()
                                    println("Breakpoint: $breakpointRequest")
                                    break
                                }
                            }
                            for (l in _class.allLineLocations()) {
                                if (l.method().name() == "foo") {
                                    val breakpointRequest = vm.eventRequestManager().createBreakpointRequest(l)
                                    breakpointRequest.enable()
                                    println("Breakpoint: $breakpointRequest")
                                    break
                                }
                            }
                            vm.resume()
                        }
                    }
                    is jdi.event.BreakpointEvent -> {
                        println("Suspended at: " + event.location() + " in " + event.location().method())

                        if (event.location().method().name() == "main") {
                            thread = event.thread()
                            latch.countDown()
                        }
                        else {
                            vm.resume()
                        }


//                        break @mainLoop
                    }
                    else -> {}
                }
            }
        }
    }.start()

    vm.resume()

    latch.await()
    println("Latch passed")

    val eval = JDIEval(vm, classLoader!!, thread!!)
    eval.invokeStaticMethod(
            MethodDescription(
                    debugeeName.replace('.', '/'),
                    "foo",
                    "()V",
                    true
            ),
            listOf()
    )

    try {
        val classLoaderReference = classLoader!!
        val threadReference = thread!!
        val eval = JDIEval(vm, classLoaderReference, threadReference)
        val classFile = File("out/production/runtime/1.class")
        val bytes = classFile.readBytes()

        val start = System.nanoTime()
        val arr = eval.newArray(Type.getType("[B"), classFile.length().toInt()).jdiObj as ArrayReference

        for (j in 1..1) {
            var i = 0;
            for (b in bytes) {
                arr.setValue(i, vm.mirrorOf(b))
                i++
            }
        }

        val loaded = classLoaderReference.invokeMethod(
            threadReference,
            classLoaderReference.referenceType().methodsByName("defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;")[0],
            listOf(
                    vm.mirrorOf("0.0"),
                    arr,
                    vm.mirrorOf(0),
                    vm.mirrorOf(bytes.size)
            ),
            0
        ) as jdi.ClassObjectReference

        println(java.lang.String.format("Loaded: %.2f", (System.nanoTime() - start) * 1e-9))

//        for (m in loaded.reflectedType().methods()) {
//            println(m)
//        }
    }
    catch (e: jdi.InvocationException) {
        val ex = e.exception()
        println(ex)
    }

    var remainingTests = AtomicInteger(0)

    val suite = buildTestSuite {
        methodNode, ownerClass, expected ->
        remainingTests.incrementAndGet()
        object : TestCase("test" + methodNode.name.capitalize()) {

            override fun runTest() {
                val eval = JDIEval(
                        vm, classLoader!!, thread!!
                )
                val value = interpreterLoop(
                        methodNode,
                        makeInitialFrame(methodNode, listOf()),
                        eval
                )

                fun jdi.ObjectReference?.callToString(): String? {
                    if (this == null) return "null"
                    return (eval.invokeMethod(
                                                this.asValue(),
                                                MethodDescription(
                                                        "java/lang/Object",
                                                        "toString",
                                                        "()Ljava/lang/String;",
                                                        false
                                                ),
                                                listOf()).jdiObj as jdi.StringReference).value()

                }

                try {
                    if (value is ExceptionThrown) {
                        val str = value.exception.jdiObj.callToString()
                        System.err.println("Exception: $str")
                    }

                    if (expected is ValueReturned && value is ValueReturned && value.result is ObjectValue) {
                        assertEquals(expected.result.obj.toString(), value.result.jdiObj.callToString())
                    }
                    else {
                        assertEquals(expected, value)
                    }
                }
                finally {
                    if (remainingTests.decrementAndGet() == 0) vm.resume()
                }

            }
        }
    }

    return suite
}