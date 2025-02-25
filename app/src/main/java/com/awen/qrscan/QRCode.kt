package com.awen.qrscan

data class QRCode(
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QRCode) return false
        return content == other.content
    }

    override fun hashCode(): Int {
        return content.hashCode()
    }
} 