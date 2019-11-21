package com.example.fbchattingtest.models

import java.util.*

class ChatModel {
    var users: Map<String, String> = HashMap()
    var messages: Map<String, String> = HashMap()

    class FileInfo {
        var filename: String? = null
        var filesize: String? = null
    }
}