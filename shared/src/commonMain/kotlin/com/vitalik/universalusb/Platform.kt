package com.vitalik.universalusb

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform