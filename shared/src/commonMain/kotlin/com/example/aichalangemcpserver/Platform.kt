package com.example.aichalangemcpserver

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform