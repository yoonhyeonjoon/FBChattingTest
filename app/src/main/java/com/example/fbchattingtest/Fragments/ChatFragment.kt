package com.example.fbchattingtest.Fragments

import android.Manifest.permission
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore.Images.Media
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.example.fbchattingtest.R
import com.example.fbchattingtest.functionals.helpers
import com.example.fbchattingtest.models.ChatModel
import com.example.fbchattingtest.models.Message
import com.example.fbchattingtest.models.NotificationModel
import com.example.fbchattingtest.models.UserModel
import com.example.fbchattingtest.photoview.ViewPagerActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.DocumentChange.Type
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import kotlinx.android.synthetic.main.fragment_chat.*
import okhttp3.*
import okhttp3.Request.Builder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


@Suppress("DEPRECATION")
class ChatFragment : Fragment() {

    companion object {
        fun getInstance(toUid: String?, roomID: String?): ChatFragment {
            val thisfragment = ChatFragment()
            val bdl = Bundle()
            bdl.putString("toUid", toUid)
            bdl.putString("roomID", roomID)
            thisfragment.arguments = bdl
            return thisfragment
        }
    }

    val PICK_FROM_ALBUM = 1
    val PICK_FROM_FILE = 2

    var roomID: String? = null
    var myUid: String? = null
    var toUid: String? = null

    val rootPath: String = helpers.rootPath + "/DirectTalk9/"
    var recyclerView: RecyclerView? = null
    lateinit var mAdapter: ChatRecyclerViewAdapter
    @SuppressLint("SimpleDateFormat")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
    @SuppressLint("SimpleDateFormat")
    val dateFormatDay = SimpleDateFormat("yyyy-MM-dd")
    @SuppressLint("SimpleDateFormat")
    val dateFormatHour = SimpleDateFormat("aa hh:mm")

    val userList = hashMapOf<String, UserModel>()
    var listenerRegistration: ListenerRegistration? = null
    var firestore: FirebaseFirestore? = null
    var storageReference: StorageReference? = null
    var linearLayoutManager: LinearLayoutManager? = null
    var progressDialog: ProgressDialog? = null
    var userCount = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view: View = inflater.inflate(R.layout.fragment_chat, container, false)

        linearLayoutManager = LinearLayoutManager(context)
        recyclerView?.layoutManager = linearLayoutManager

        sendBtn.setOnClickListener {
            val msg = msg_input.text.toString()
            sendMessage(msg, "0", null)
            msg_input.setText("")
        }
        imageBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = Media.CONTENT_TYPE
            startActivityForResult(intent, PICK_FROM_ALBUM)
        }
        fileBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, Media.EXTERNAL_CONTENT_URI)
            intent.type = "*/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select File"), PICK_FROM_FILE)
        }
        msg_input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus)
                helpers.hideKeyboard(activity!!)
        }
        if (arguments != null) {
            roomID = arguments?.getString("roomID")
            toUid = arguments?.getString("toUid")
        }
        firestore = FirebaseFirestore.getInstance()
        storageReference = FirebaseStorage.getInstance().reference
        dateFormatDay.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        dateFormatHour.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        myUid = FirebaseAuth.getInstance().currentUser?.uid

        if ("" != toUid && toUid != null) {                     // find existing room for two user
            findChatRoom(toUid!!)
        } else if ("" != roomID && roomID != null) { // existing room (multi user)

            setChatRoom(roomID!!)
        }
        if (roomID == null) {                                                     // new room for two user

            getUserInfoFromServer(myUid)
            getUserInfoFromServer(toUid)
            userCount = 2
        }

        recyclerView?.addOnLayoutChangeListener { _, _, _, bottom, _, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                val lastAdapterItem = mAdapter.itemCount - 1
                recyclerView?.post {
                    var recyclerViewPositionOffset = -1000000
                    val bottomView = linearLayoutManager?.findViewByPosition(lastAdapterItem)
                    if (bottomView != null) recyclerViewPositionOffset = 0 - bottomView.height
                    linearLayoutManager?.scrollToPositionWithOffset(
                        lastAdapterItem,
                        recyclerViewPositionOffset
                    )
                }
            }
        }
        return view
    }

    private fun sendMessage(msg: String?, msgtype: String?, fileinfo: ChatModel.FileInfo?) {
        sendBtn.isEnabled = false
        if (roomID == null) {             // create chatting room for two user
            roomID = firestore?.collection("rooms")?.document()?.id
            CreateChattingRoom(firestore!!.collection("rooms").document(roomID!!))
        }
        val messages = hashMapOf<String, Any?>()
        messages["uid"] = myUid
        messages["msg"] = msg
        messages["msgtype"] = msgtype
        messages["timestamp"] = FieldValue.serverTimestamp()
        if (fileinfo != null) {
            messages["filename"] = fileinfo.filename
            messages["filesize"] = fileinfo.filesize
        }
        val docRef = firestore!!.collection("rooms").document(roomID!!)

        docRef.get().addOnCompleteListener {
            if (!it.isSuccessful) {
                return@addOnCompleteListener
            }
            val batch = firestore!!.batch()
            // save last message
            batch.set(docRef, messages, SetOptions.merge())
            // save message
            val readUsers: MutableList<String?> = mutableListOf()
            readUsers.add(myUid)
            messages["readUsers"] = readUsers   //new String[]{myUid} );
            batch.set(docRef.collection("messages").document(), messages)
            // inc unread message count
            val document = it.result
            val users = document!!.get("users") as MutableMap<String, Long>?
            for (key in users!!.keys) {
                if (myUid != key) users[key] = users[key]!! + 1
            }
            document.reference.update("users", users)
            batch.commit().addOnCompleteListener { it2 ->
                if (it2.isSuccessful) {
                    //sendGCM();
                    sendBtn.isEnabled = true
                }
            }
        }
    }

    fun CreateChattingRoom(room: DocumentReference) {
        val users = hashMapOf<String, Int>()
        val title = ""
        for (key in userList.keys) users[key] = 0
        val data = hashMapOf<String, Any?>()
        data["title"] = null
        data["users"] = users
        room.set(data).addOnCompleteListener {
            if (it.isSuccessful) {
                mAdapter = ChatRecyclerViewAdapter()
                recyclerView!!.adapter = mAdapter
            }
        }
    }

    // Returns the room ID after locating the chatting room with the user ID.
    private fun findChatRoom(toUid: String) {
        firestore!!.collection("rooms").whereGreaterThanOrEqualTo("users.$myUid", 0).get()
            .addOnCompleteListener {
                if (!it.isSuccessful) return@addOnCompleteListener
                for (document in it.result!!) {
                    val users = document.get("users") as Map<String, Long>?
                    if (users?.size == 2 && users[toUid]?.toInt() != null) {
                        setChatRoom(document.id)
                        break
                    }
                }
            }
    }

    // get user list in a chatting room
    private fun setChatRoom(rid: String) {
        roomID = rid
        firestore?.collection("rooms")?.document(roomID!!)?.get()?.addOnCompleteListener {
            if (!it.isSuccessful) {
                return@addOnCompleteListener
            }
            val document = it.result
            val users =
                document!!.get("users") as Map<String, Long>?
            for (key in users!!.keys) {
                getUserInfoFromServer(key)
            }
            userCount = users.size//users.put(myUid, (long) 0);
            //document.getReference().update("users", users);

        }
    }

    // get a user info
    private fun getUserInfoFromServer(id: String?) {
        firestore!!.collection("users").document(id!!).get().addOnSuccessListener {
            val userModel = it.toObject(
                UserModel::class.java
            )
            userList[userModel?.uid!!] = userModel
            if (roomID != null && userCount == userList.size) {
                mAdapter = ChatRecyclerViewAdapter()
                recyclerView!!.adapter = mAdapter
            }
        }
    }

    internal fun setUnread2Read() {
        if (roomID == null) return
        firestore!!.collection("rooms").document(roomID!!).get().addOnCompleteListener {
            if (!it.isSuccessful) {
                return@addOnCompleteListener
            }
            val document = it.result
            val users = document!!.get("users") as MutableMap<String?, Long>
            users[myUid] = 0.toLong()
            document.reference.update("users", users)
        }
    }

    fun getUserList(): Map<String, UserModel> = userList


    internal fun sendGCM() {
        val gson = Gson()
        val notificationModel = NotificationModel()
        notificationModel.notification.title = userList[myUid]?.usernm
        notificationModel.notification.body = msg_input.text.toString()
        notificationModel.data.title = userList[myUid]?.usernm
        notificationModel.data.body = msg_input.text.toString()
        for ((_, value) in userList) {
            if (myUid == value.uid) continue
            notificationModel.to = value.token
            val requestBody: RequestBody? = RequestBody.create(
                MediaType.parse("application/json; charset=utf8"),
                gson.toJson(notificationModel)
            )
            val request: Request? = Builder()
                .header("Content-Type", "application/json")
                .addHeader("Authorization", "key=")
                .url("https://fcm.googleapis.com/fcm/send")
                .post(requestBody)
                .build()
            val okHttpClient = OkHttpClient()
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call?, e: IOException?) {}
                @Throws(IOException::class)
                override fun onResponse(call: Call?, response: Response?) {
                }
            })
        }
    }

    // uploading image / file
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return

        val fileUri: Uri? = data!!.data
        val filename: String = helpers.uniqueValue
        showProgressDialog("Uploading selected File.")
        val fileinfo: ChatModel.FileInfo? = getFileDetailFromUri(context!!, fileUri)
        storageReference!!.child("files/$filename").putFile(fileUri!!)
            .addOnCompleteListener {
                sendMessage(filename, requestCode.toString(), fileinfo)
                hideProgressDialog()
            }
        if (requestCode != PICK_FROM_ALBUM) {
            return
        }
        // small image
        Glide.with(context!!)
            .asBitmap()
            .load(fileUri)
            .apply(RequestOptions().override(150, 150))
            .into(object : SimpleTarget<Bitmap?>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap?>?
                ) {
                    val baos = ByteArrayOutputStream()
                    resource.compress(CompressFormat.JPEG, 100, baos)
                    val data: ByteArray? = baos.toByteArray()
                    storageReference!!.child("filesmall/$filename").putBytes(data!!)
                }
            })
    }

    fun showProgressDialog(title: String?) {
        if (progressDialog == null) {
            progressDialog = ProgressDialog(context)
        }
        progressDialog?.isIndeterminate = true
        progressDialog?.setTitle(title)
        progressDialog?.setMessage("Please wait..")
        progressDialog?.setCancelable(false)
        progressDialog?.show()
    }

    // get file name and size from Uri
    fun getFileDetailFromUri(context: Context, uri: Uri?): ChatModel.FileInfo? {
        if (uri == null) {
            return null
        }
        val fileDetail: ChatModel.FileInfo = ChatModel.FileInfo()
        // File Scheme.
        if (ContentResolver.SCHEME_FILE == uri.scheme) {
            val file = File(uri.path)
            fileDetail.filename = file.name
            fileDetail.filesize = helpers.size2String(file.length())
        }
        // Content Scheme.
        else if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            val returnCursor: Cursor? =
                context.contentResolver.query(uri, null, null, null, null)
            if (returnCursor != null && returnCursor.moveToFirst()) {
                val nameIndex =
                    returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex =
                    returnCursor.getColumnIndex(OpenableColumns.SIZE)
                fileDetail.filename = returnCursor.getString(nameIndex)
                fileDetail.filesize = helpers.size2String(returnCursor.getLong(sizeIndex))
                returnCursor.close()
            }
        }
        return fileDetail
    }

    fun setProgressDialog(value: Int) {
        progressDialog!!.progress = value
    }

    fun hideProgressDialog() = progressDialog!!.dismiss()


    // =======================================================================================
    inner class ChatRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val requestOptions = RequestOptions().transforms(CenterCrop(), RoundedCorners(90))
        internal var messageList: MutableList<Message>? = null
        private var beforeDay: String? = null
        internal var beforeViewHolder: MessageViewHolder? = null


        fun startListening() {
            beforeDay = null
            messageList?.clear()
            val roomRef: CollectionReference? =
                firestore?.collection("rooms")?.document(roomID!!)?.collection("messages")
            // my chatting room information

            listenerRegistration = roomRef?.orderBy("timestamp")?.addSnapshotListener { p0, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                var message: Message
                for (change in p0!!.documentChanges) {
                    when (change.type) {
                        Type.ADDED -> {
                            message = change.document.toObject<Message>(Message::class.java)
                            if (message.readUsers.indexOf(myUid) == -1) {
                                message.readUsers.add(myUid)
                                change.document.reference
                                    .update("readUsers", message.getReadUsers())
                            }
                            messageList?.add(message)
                            notifyItemInserted(change.newIndex)
                        }
                        Type.MODIFIED -> {
                            message = change.document.toObject<Message>(
                                Message::class.java
                            )
                            messageList?.set(change.oldIndex, message)
                            notifyItemChanged(change.oldIndex)
                        }
                        Type.REMOVED -> {
                            messageList?.removeAt(change.oldIndex)
                            notifyItemRemoved(change.oldIndex)
                        }
                    }
                }
                recyclerView?.scrollToPosition(messageList!!.size - 1)

            }
        }

        fun stopListening() {
            if (listenerRegistration != null) {
                listenerRegistration?.remove()
                listenerRegistration = null
            }
            messageList?.clear()
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            val message: Message = messageList!![position]
            return if (myUid == message.uid) {
                when (message.msgtype) {
                    "1" -> R.layout.item_chatimage_right
                    "2" -> R.layout.item_chatfile_right
                    else -> R.layout.item_chatmsg_right
                }
            } else {
                when (message.getMsgtype()) {
                    "1" -> R.layout.item_chatimage_left
                    "2" -> R.layout.item_chatfile_left
                    else -> R.layout.item_chatmsg_left
                }
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerView.ViewHolder {
            var view: View? = null
            view = LayoutInflater.from(parent.context)
                .inflate(viewType, parent, false)
            return MessageViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val messageViewHolder: ChatFragment.MessageViewHolder =
                holder as ChatFragment.MessageViewHolder
            val message: Message = messageList!![position]
            setReadCounter(message, messageViewHolder.read_counter)
            if ("0" == message.msgtype) {                                      // text message

                messageViewHolder.msg_item.text = message.msg
            } else if ("2" == message.msgtype) {                                      // file transfer

                messageViewHolder.msg_item.setText(message.filename.toString() + "\n" + message.filesize)
                messageViewHolder.filename = message.filename
                messageViewHolder.realname = message.msg
                val file =
                    File(rootPath + message.filename)
                if (file.exists()) {
                    messageViewHolder.button_item.text = "Open File"
                } else {
                    messageViewHolder.button_item.text = "Download"
                }
            } else {                                                                // image transfer
                messageViewHolder.realname = message.msg
                Glide.with(context!!)
                    .load(storageReference?.child("filesmall/" + message.msg))
                    .apply(RequestOptions().override(1000, 1000))
                    .into(messageViewHolder.img_item)
            }
            if (myUid != message.uid) {
                val userModel: UserModel? = userList[message.uid]
                messageViewHolder.msg_name.setText(userModel?.usernm)
                if (userModel?.userphoto == null) {
                    Glide.with(context!!).load(R.drawable.user)
                        .apply(requestOptions)
                        .into(messageViewHolder.user_photo)
                } else {
                    Glide.with(context!!)
                        .load(storageReference?.child("userPhoto/" + userModel.userphoto))
                        .apply(requestOptions)
                        .into(messageViewHolder.user_photo)
                }
            }
            messageViewHolder.divider.visibility = View.INVISIBLE
            messageViewHolder.divider.layoutParams.height = 0
            messageViewHolder.timestamp.text = ""
            if (message.timestamp == null) {
                return
            }
            val day: String = dateFormatDay.format(message.timestamp)
            val timestamp: String = dateFormatHour.format(message.timestamp)
            messageViewHolder.timestamp.text = timestamp
            if (position == 0) {
                messageViewHolder.divider_date.text = day
                messageViewHolder.divider.visibility = View.VISIBLE
                messageViewHolder.divider.layoutParams.height = 60
            }
            /*messageViewHolder.timestamp.setText("");
        if (message.getTimestamp()==null) {return;}

        String day = dateFormatDay.format( message.getTimestamp());
        String timestamp = dateFormatHour.format( message.getTimestamp());

        messageViewHolder.timestamp.setText(timestamp);

        if (position==0) {
            messageViewHolder.divider_date.setText(day);
            messageViewHolder.divider.setVisibility(View.VISIBLE);
            messageViewHolder.divider.getLayoutParams().height = 60;
        };
        if (!day.equals(beforeDay) && beforeDay!=null) {
            beforeViewHolder.divider_date.setText(beforeDay);
            beforeViewHolder.divider.setVisibility(View.VISIBLE);
            beforeViewHolder.divider.getLayoutParams().height = 60;
        }
        beforeViewHolder = messageViewHolder;
        beforeDay = day;*/ else {
                val beforeMsg: Message = messageList!![position - 1]
                val beforeDay: String = dateFormatDay.format(beforeMsg.timestamp)
                if (day != beforeDay && beforeDay != null) {
                    messageViewHolder.divider_date.text = day
                    messageViewHolder.divider.visibility = View.VISIBLE
                    messageViewHolder.divider.layoutParams.height = 60
                }
            }
        }

        private fun setReadCounter(message: Message, textView: TextView) {
            val cnt: Int = userCount - message.readUsers.size
            if (cnt > 0) {
                textView.visibility = View.VISIBLE
                textView.text = cnt.toString()
            } else {
                textView.visibility = View.INVISIBLE
            }
        }

        override fun getItemCount() = messageList!!.size

        init {
            val dir = File(rootPath)
            if (!dir.exists()) {
                if (!helpers.isPermissionGranted(
                        activity!!,
                        permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {

                } else dir.mkdirs()
            }
            messageList = ArrayList<Message>()
            setUnread2Read()
            startListening()
        }
    }


    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var user_photo: ImageView = view.findViewById(R.id.user_photo)
        var msg_item: TextView = view.findViewById(R.id.msg_item)
        var img_item: ImageView = view.findViewById(R.id.img_item) // only item_chatimage_
        var msg_name: TextView = view.findViewById(R.id.msg_name)
        var timestamp: TextView = view.findViewById(R.id.timestamp)
        var read_counter: TextView = view.findViewById(R.id.read_counter)
        var divider: LinearLayout = view.findViewById(R.id.divider)
        var divider_date: TextView = view.findViewById(R.id.divider_date)
        var button_item: TextView = view.findViewById(R.id.button_item) // only item_chatfile_
        var msgLine_item: LinearLayout = view.findViewById(R.id.msgLine_item) // only item_chatfile_
        var filename: String = ""
        var realname: String = ""


         var downloadClickListener = object : View.OnClickListener {
                override fun onClick(view: View) {
                    if ("Download" == button_item.text) {
                        download()
                    } else {
                        openWith()
                    }
                }

                fun download() {
                    if (!helpers.isPermissionGranted(activity!!, WRITE_EXTERNAL_STORAGE)
                    ) {
                        return
                    }
                    showProgressDialog("Downloading File.")
                    val localFile = File(rootPath, filename)
                    storageReference?.child("files/" + realname)?.getFile(localFile)?.addOnSuccessListener{
                        button_item.setText("Open File")
                        hideProgressDialog()
                        Log.e("DirectTalk9 ", "local file created $localFile")
                    }?.addOnFailureListener{
                        Log.e("DirectTalk9 ", "local file not created $it")
                    }
                }

                @SuppressLint("ObsoleteSdkInt")
                fun openWith() {
                    val newFile = File(rootPath + filename)
                    val mime = MimeTypeMap.getSingleton()
                    val ext = newFile.name.substring(newFile.name.lastIndexOf(".") + 1)
                    val type = mime.getMimeTypeFromExtension(ext)
                    val intent = Intent(Intent.ACTION_VIEW)
                    val uri: Uri
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        uri = FileProvider.getUriForFile(context!!,activity!!.packageName + ".provider", newFile)
                        val resInfoList = activity!!.packageManager
                            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        for (resolveInfo in resInfoList) {
                            val packageName = resolveInfo.activityInfo.packageName
                            activity!!.grantUriPermission(packageName,uri,
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    } else {
                        uri = Uri.fromFile(newFile)
                    }
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.setDataAndType(uri, type)//"application/vnd.android.package-archive");
                    startActivity(Intent.createChooser(intent, "Your title"))
                }
            }
        // photo view
        private var imageClickListener = View.OnClickListener {
            val intent = Intent(context, ViewPagerActivity::class.java)
            intent.putExtra("roomID", roomID)
            intent.putExtra("realname", realname)
            startActivity(intent)
        }

        // file download and open
        init {
            // for file
            msgLine_item.setOnClickListener(downloadClickListener)
            img_item.setOnClickListener(imageClickListener)
        }


    }

    fun backPressed() {}

}