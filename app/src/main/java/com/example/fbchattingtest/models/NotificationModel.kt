package com.example.fbchattingtest.models

class NotificationModel {
    var to: String? = null
    var notification = Notification()
    var data = Data()

    class Notification {
        var title: String? = null
        var body: String? = null
    }

    class Data {
        var title: String? = null
        var body: String? = null
    }
}