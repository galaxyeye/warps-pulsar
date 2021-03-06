package ai.platon.pulsar.protocol.browser.emulator

import ai.platon.pulsar.persist.ProtocolStatus

class NavigateTaskCancellationException: IllegalStateException {
    constructor() : super() {}

    constructor(message: String) : super(message) {
    }

    constructor(message: String, cause: Throwable) : super(message, cause) {
    }

    constructor(cause: Throwable) : super(cause) {
    }
}

class PrivacyLeakException: IllegalStateException {
    constructor() : super() {}

    constructor(message: String) : super(message) {
    }

    constructor(message: String, cause: Throwable) : super(message, cause) {
    }

    constructor(cause: Throwable) : super(cause) {
    }
}

class IncompleteContentException: Exception {
    var status: ProtocolStatus = ProtocolStatus.STATUS_EXCEPTION
    var content: String = ""

    constructor() : super() {}

    constructor(message: String, status: ProtocolStatus, content: String) : super(message) {
        this.content = content
    }

    constructor(message: String, cause: Throwable) : super(message, cause) {}

    constructor(cause: Throwable) : super(cause) {}
}

open class WebDriverPoolException: Exception {
    constructor() : super() {}

    constructor(message: String) : super(message) {
    }

    constructor(message: String, cause: Throwable) : super(message, cause) {}

    constructor(cause: Throwable) : super(cause) {}
}

class WebDriverPoolExhaustedException(message: String) : WebDriverPoolException(message)

class WebDriverPoolRetiredException(message: String) : WebDriverPoolException(message)

class WebDriverPoolClosedException(message: String) : WebDriverPoolException(message)
