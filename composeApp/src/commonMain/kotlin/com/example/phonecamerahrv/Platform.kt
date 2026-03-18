package com.example.phonecamerahrv

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform