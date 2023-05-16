class ImageProcessingExceptions {
    class ImageProcessingEx(message: String) : Exception(message)
    class ImageProcessingException(cause: Throwable, message: String, stackTrace: Array<StackTraceElement>) : Exception(message, cause) {
        init {
            this.stackTrace = stackTrace
        }
    }
}