package com.reconvene.ui.pod

import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.auth.asMember
import com.reconvene.data.model.PodActionException
import com.reconvene.data.model.PodActionProblem
import com.reconvene.data.repository.PodRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Runs join, leave and cancel for a screen's ViewModel. Each pod takes one action at a time, and a
// refusal or a failed request becomes a message for the screen to show.
class PodActionRunner(
    private val podRepository: PodRepository,
    private val scope: CoroutineScope,
) {
    private val _busyPodIds = MutableStateFlow(emptySet<String>())
    val busyPodIds: StateFlow<Set<String>> = _busyPodIds.asStateFlow()

    private val _message = MutableStateFlow<PodMessage?>(null)
    val message: StateFlow<PodMessage?> = _message.asStateFlow()

    fun join(podId: String, user: SignedInUser) = start(podId) {
        podRepository.joinPod(podId, user.asMember())
    }

    fun leave(podId: String, user: SignedInUser) = start(podId) {
        podRepository.leavePod(podId, user.uid)
    }

    // The buttons ask the host to confirm first. A canceled pod drops out of Discover, so the
    // message confirms where it went.
    fun cancel(podId: String, user: SignedInUser) = start(podId) {
        podRepository.cancelPod(podId, user.uid)
        _message.value = PodMessage.CANCELED
    }

    // For a screen's own actions, so every message goes through one snackbar.
    fun show(message: PodMessage) {
        _message.value = message
    }

    fun messageShown() {
        _message.value = null
    }

    private fun start(podId: String, action: suspend () -> Unit) {
        if (podId in _busyPodIds.value) return
        _busyPodIds.update { it + podId }
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PodActionException) {
                _message.value = e.problem.toMessage()
            } catch (e: Exception) {
                _message.value = PodMessage.FAILED
            } finally {
                _busyPodIds.update { it - podId }
            }
        }
    }

    private fun PodActionProblem.toMessage(): PodMessage = when (this) {
        PodActionProblem.FULL -> PodMessage.FULL
        PodActionProblem.CLOSED -> PodMessage.CLOSED
        PodActionProblem.NOT_FOUND -> PodMessage.GONE
        // Leave shows only to members and Cancel only to the host, so these mean the cached pod
        // is out of date.
        PodActionProblem.HOST_CANNOT_LEAVE, PodActionProblem.NOT_HOST -> PodMessage.FAILED
    }
}
