package sh.eliza.textbender

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

private const val TAG = "TextbenderAccessibilityService"

private val state = AtomicReference<State>(null)

class TextbenderAccessibilityService : AccessibilityService() {
  override fun onServiceConnected() {
    state.getAndSet(State(this))?.close()

    accessibilityButtonController.registerAccessibilityButtonCallback(
      object : AccessibilityButtonCallback() {
        override fun onAvailabilityChanged(
          controller: AccessibilityButtonController,
          available: Boolean
        ) {}

        override fun onClicked(controller: AccessibilityButtonController) {
          activate()
        }
      }
    )
  }

  override fun onUnbind(intent: Intent): Boolean {
    val state = state.getAndSet(null)
    if (state.service === this) {
      state.close()
    }
    return false
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {}

  override fun onInterrupt() {}

  companion object {
    fun activate() {
      state.get()?.activate()
    }
  }
}

private class State(val service: TextbenderAccessibilityService) : AutoCloseable {
  private val lock = ReentrantLock()

  private enum class Message {
    QUIT,
    ACTIVATE,
  }

  private var message: Message? = null
  private var newMessage = lock.newCondition()

  private val messageThread =
    thread(start = true, isDaemon = true, name = "TextbenderAccessibilityServiceThread") {
      Log.d(TAG, "messageThread: Created")
      while (true) {
        val message =
          lock.withLock {
            newMessage.await()
            val message = message
            this.message = null
            message
          }
        check(message != null)
        when (message) {
          Message.QUIT -> break
          Message.ACTIVATE -> doActivate()
        }
      }
      Log.d(TAG, "messgeThread: Quitting")
    }

  override fun close() {
    Log.d(TAG, "close()")
    lock.withLock {
      message = Message.QUIT
      newMessage.signalAll()
    }
  }

  fun activate() {
    Log.d(TAG, "activate()")
    lock.withLock {
      message = Message.ACTIVATE
      newMessage.signalAll()
    }
  }

  private fun doActivate() {
    Log.d(TAG, "messageThread: Activate")
    for (window in service.windows) {
      window.root.dump("")
    }
  }
}

private operator fun AccessibilityWindowInfo.iterator() =
  object : Iterator<AccessibilityWindowInfo> {
    var index = 0

    override fun hasNext() = maybeNext() !== null
    override fun next(): AccessibilityWindowInfo {
      val (nextItem, nextIndex) = maybeNext()!!
      index = nextIndex + 1
      return nextItem
    }

    private fun maybeNext(): Pair<AccessibilityWindowInfo, Int>? {
      if (index >= childCount) {
        return null
      }
      var index = index
      do {
        val child = getChild(index)
        if (child != null) {
          return Pair(child, index)
        }
        index++
      } while (index < childCount)

      return null
    }
  }

private operator fun AccessibilityNodeInfo.iterator() =
  object : Iterator<AccessibilityNodeInfo> {
    var index = 0

    override fun hasNext() = maybeNext() !== null
    override fun next(): AccessibilityNodeInfo {
      val (nextItem, nextIndex) = maybeNext()!!
      index = nextIndex + 1
      return nextItem
    }

    private fun maybeNext(): Pair<AccessibilityNodeInfo, Int>? {
      if (index >= childCount) {
        return null
      }
      var index = index
      do {
        val child = getChild(index)
        if (child != null) {
          return Pair(child, index)
        }
        index++
      } while (index < childCount)

      return null
    }
  }

private fun AccessibilityNodeInfo.dump(prefix: String) {
  val myPrefix = "${prefix}/${className}"
  if (text !== null) {
    Log.i(TAG, "${myPrefix}: ${text}")
  }
  for (child in this) {
    child.dump(myPrefix)
  }
}

private fun AccessibilityWindowInfo.dump(prefix: String) {
  val myPrefix = "${prefix}/${title}"
  Log.i(TAG, myPrefix)
  if (root !== null) {
    root.dump("${myPrefix} -- ")
  }
  for (child in this) {
    child.dump(myPrefix)
  }
}

private val AccessibilityNodeInfo.root: AccessibilityNodeInfo
  get() {
    var node = this
    while (true) {
      val parent = node.getParent(AccessibilityNodeInfo.FLAG_PREFETCH_ANCESTORS)
      if (parent === null) {
        return node
      }
      node = parent
    }
  }

// private fun AccessibilityNodeInfo.asIterable() = Iterable {}
