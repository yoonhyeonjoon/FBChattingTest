package com.example.fbchattingtest.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.fbchattingtest.ChatModule.ChatActivity
import com.example.fbchattingtest.R
import com.example.fbchattingtest.models.ChatRoomModel
import com.example.fbchattingtest.models.Message
import com.example.fbchattingtest.models.UserModel
import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.fragment_chatroom.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class ChatRoomFragment : Fragment() {

    companion object {
        fun setBadge(context: Context, count: Int) {
            val launcherClassName = getLauncherClassName(context) ?: return
            val intent = Intent("android.intent.action.BADGE_COUNT_UPDATE")
            intent.putExtra("badge_count", count)
            intent.putExtra("badge_count_package_name", context.packageName)
            intent.putExtra("badge_count_class_name", launcherClassName)
            context.sendBroadcast(intent)
        }
        fun getLauncherClassName(context: Context): String? {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (resolveInfo in resolveInfos) {
                val pkgName = resolveInfo.activityInfo.applicationInfo.packageName
                if (pkgName.equals(context.packageName, ignoreCase = true)) {
                    return resolveInfo.activityInfo.name
                }
            }
            return null
        }
    }

    @SuppressLint("SimpleDateFormat")
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
    private var mAdapter: RecyclerViewAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view: View = inflater.inflate(R.layout.fragment_chat, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(inflater.context)
        mAdapter = RecyclerViewAdapter()
        recyclerView.adapter = mAdapter
        simpleDateFormat.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (mAdapter != null) {mAdapter!!.stopListening()}
    }

    // =============================================================================================
    inner class RecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val requestOptions = RequestOptions().transforms(CenterCrop(), RoundedCorners(90))
        private val roomList = ArrayList<ChatRoomModel>()
        private val userList = HashMap<String, UserModel>()
        private val myUid: String = FirebaseAuth.getInstance().currentUser!!.uid
        private val storageReference: StorageReference = FirebaseStorage.getInstance().reference
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
        private var listenerRegistration: ListenerRegistration? = null
        private var listenerUsers: ListenerRegistration? = null
        var unreadTotal: Int = 0
        override fun getItemCount(): Int = roomList.size
        init {
            // all users information
            listenerUsers = firestore.collection("users").addSnapshotListener(EventListener { value, e ->
                    if (e != null) { return@EventListener }
                    for (doc in value!!) { userList[doc.id] = doc.toObject<UserModel>(UserModel::class.java) }
                    getRoomInfo()
                })
        }
        fun getRoomInfo() {
            // my chatting room information
            listenerRegistration =
                firestore.collection("rooms").whereGreaterThanOrEqualTo("users.$myUid", 0)
                    //                    a.orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener(EventListener { value, e ->
                        if (e != null) { return@EventListener }

                        val orderedRooms =
                            TreeMap<Date, ChatRoomModel>(Collections.reverseOrder<Any>())

                        for (document in value!!) {
                            val message = document.toObject<Message>(Message::class.java!!)
                            if ((message.msg != null) and (message.timestamp == null)) {
                                continue
                            } // FieldValue.serverTimestamp is so late

                            val chatRoomModel = ChatRoomModel()
                            chatRoomModel.roomID = document.id

                            if (message.msg != null) { // there are no last message
                                chatRoomModel.lastDatetime = simpleDateFormat.format(message.timestamp)
                                when (message.msgtype) {
                                    "1" -> chatRoomModel.lastMsg = ("Image")
                                    "2" -> chatRoomModel.lastMsg = ("File")
                                    else -> chatRoomModel.lastMsg = (message.msg)
                                }
                            }
                            val users = document.get("users") as Map<String, Long>?
                            chatRoomModel.userCount = (users!!.size)
                            for (key in users.keys) {
                                if (myUid == key) {
                                    val unread = (users[key] as Long).toInt()
                                    unreadTotal += unread
                                    chatRoomModel.unreadCount = unread
                                    break
                                }
                            }
                            if (users.size == 2) {
                                for (key in users.keys) {
                                    if (myUid == key) continue
                                    val userModel = userList[key]
                                    chatRoomModel.title = (userModel!!.usernm)
                                    chatRoomModel.photo = userModel.userphoto
                                }
                            } else {                // group chat room
                                chatRoomModel.title = document.getString("title")
                            }
                            if (message.timestamp == null) message.timestamp = Date()
                            orderedRooms[message.timestamp] = chatRoomModel
                        }
                        roomList.clear()
                        for ((_, value1) in orderedRooms) {
                            roomList.add(value1)
                        }
                        notifyDataSetChanged()
                        setBadge(context!!, unreadTotal!!)
                    })
        }

        fun stopListening() {
            if (listenerRegistration != null) {
                listenerRegistration!!.remove()
                listenerRegistration = null
            }
            if (listenerUsers != null) {
                listenerUsers!!.remove()
                listenerUsers = null
            }

            roomList.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_chatroom, parent, false)
            return RoomViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val roomViewHolder = holder as RoomViewHolder
            val chatRoomModel = roomList[position]
            roomViewHolder.room_title.text = chatRoomModel.title
            roomViewHolder.last_msg.text = chatRoomModel.lastMsg
            roomViewHolder.last_time.text = chatRoomModel.lastDatetime
            if (chatRoomModel.photo == null) {
                Glide.with(activity!!).load(R.drawable.user)
                    .apply(requestOptions)
                    .into(roomViewHolder.room_image)
            } else {
                Glide.with(activity!!)
                    .load(storageReference.child("userPhoto/" + chatRoomModel.photo))
                    .apply(requestOptions)
                    .into(roomViewHolder.room_image)
            }
            if (chatRoomModel.userCount!! > 2) {
                roomViewHolder.room_count.text = chatRoomModel.userCount.toString()
                roomViewHolder.room_count.visibility = View.VISIBLE
            } else {
                roomViewHolder.room_count.visibility = View.INVISIBLE
            }
            if (chatRoomModel.unreadCount!! > 0) {
                roomViewHolder.unread_count.text = chatRoomModel.unreadCount.toString()
                roomViewHolder.unread_count.visibility = View.VISIBLE
            } else {
                roomViewHolder.unread_count.visibility = View.INVISIBLE
            }

            roomViewHolder.itemView.setOnClickListener { v ->
                val intent = Intent(v.context, ChatActivity::class.java)
                intent.putExtra("roomID", chatRoomModel.roomID)
                intent.putExtra("roomTitle", chatRoomModel.title)
                startActivity(intent)
            }
        }

        private inner class RoomViewHolder internal constructor(view: View) :
            RecyclerView.ViewHolder(view) {
            var room_image: ImageView = view.findViewById(R.id.room_image)
            var room_title: TextView = view.findViewById(R.id.room_title)
            var last_msg: TextView = view.findViewById(R.id.last_msg)
            var last_time: TextView = view.findViewById(R.id.last_time)
            var room_count: TextView = view.findViewById(R.id.room_count)
            var unread_count: TextView = view.findViewById(R.id.unread_count)
        }
    }

}
