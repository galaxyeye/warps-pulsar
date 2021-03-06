package ai.platon.pulsar.browser.driver.chrome.util

open class ChromeProcessException: RuntimeException {
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}

open class ChromeProcessTimeoutException : ChromeProcessException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}

open class ChromeLaunchException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}

open class WebSocketServiceException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}

open class ChromeServiceException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}

open class ScreenshotException : ChromeServiceException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}

open class ChromeDevToolsInvocationException : RuntimeException {
    var code = -1L

    constructor(message: String) : super(message)

    constructor(code: Long, message: String) : super(message) {
        this.code = code
    }

    constructor(message: String, cause: Throwable) : super(message, cause)
}
