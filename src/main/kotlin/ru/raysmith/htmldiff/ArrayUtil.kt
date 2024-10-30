package ru.raysmith.htmldiff

fun <T> MutableList<T>.removeRange(index: Int, count: Int): MutableList<T> {
    if (index + count <= this.size) {
        for (i in 0 until count) {
            this.removeAt(index)
        }
    }
    return this
}