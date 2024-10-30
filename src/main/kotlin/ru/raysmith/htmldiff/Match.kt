package ru.raysmith.htmldiff

class Match(var StartInOld: Int, var StartInNew: Int, var Size: Int) {
    var EndInOld: Int = StartInOld + Size
    var EndInNew: Int = StartInNew + Size
}