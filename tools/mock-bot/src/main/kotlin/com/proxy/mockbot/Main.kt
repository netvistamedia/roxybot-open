package com.proxy.mockbot

const val DEFAULT_PORT: Int = 8765

fun main(args: Array<String>) {
    val port: Int = args.firstOrNull()?.toIntOrNull() ?: DEFAULT_PORT
    val server: MockBotServer = MockBotServer(port)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(1000) })
    server.run()
}
