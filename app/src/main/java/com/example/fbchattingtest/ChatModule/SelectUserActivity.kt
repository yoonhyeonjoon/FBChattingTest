package com.example.fbchattingtest.ChatModule


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.fbchattingtest.R
import com.example.fbchattingtest.functionals.FirestoreAdapter
import com.example.fbchattingtest.functionals.helpers
import com.example.fbchattingtest.models.UserModel
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.*





class SelectUserActivity : AppCompatActivity() {

    private var roomID: String? = null
    var firestoreAdapter: FirestoreAdapter<*>? = null
    val selectedUsers = mutableMapOf<String, String>()

     override fun onStart() {
        super.onStart()
        if (firestoreAdapter != null) {
            firestoreAdapter!!.startListening()
        }
    }

    override fun onStop() {
        super.onStop()
        if (firestoreAdapter != null) {
            firestoreAdapter!!.stopListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_user)

        roomID = intent.getStringExtra("roomID")
        firestoreAdapter = RecyclerViewAdapter(FirebaseFirestore.getInstance().collection("users").orderBy("usernm"))
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.setLayoutManager(LinearLayoutManager(this))
        recyclerView.setAdapter(firestoreAdapter)
        val makeRoomBtn: Button = findViewById(R.id.makeRoomBtn)
        if (roomID == null) makeRoomBtn.setOnClickListener(makeRoomClickListener) else makeRoomBtn.setOnClickListener(
            addRoomUserClickListener
        )
    }

    private var makeRoomClickListener =
        OnClickListener {
            if (selectedUsers.size < 2) {
                helpers.showMessage(applicationContext, "Please select 2 or more user")
                return@OnClickListener
            }
            selectedUsers[FirebaseAuth.getInstance().currentUser!!.uid] = ""
            val newRoom =
                FirebaseFirestore.getInstance()
                    .collection("rooms").document()
            CreateChattingRoom(newRoom)
        }
    private var addRoomUserClickListener =
        OnClickListener {
            if (selectedUsers.isEmpty()) {
                helpers.showMessage(applicationContext, "Please select 1 or more user")
                return@OnClickListener
            }
            CreateChattingRoom(
                FirebaseFirestore.getInstance().collection(
                    "rooms"
                ).document(roomID!!)
            )
        }

    fun CreateChattingRoom(room: DocumentReference) {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val users = mutableMapOf<String, Int>()
        var title = ""
        for (key in selectedUsers.keys) {
            users[key] = 0
            if (title.length < 20 && (key != uid)) {
                title += selectedUsers[key].toString() + ", "
            }
        }
        val data = mutableMapOf<String, Any>()
        data["title"] = title.substring(0, title.length - 2)
        data["users"] = users
        room.set(data).addOnCompleteListener{
                if (it.isSuccessful) {
                    val intent = Intent(
                        this@SelectUserActivity,
                        ChatActivity::class.java
                    )
                    intent.putExtra("roomID", room.id)
                    startActivity(intent)
                    this@SelectUserActivity.finish()
                }
        }
    }

    inner class RecyclerViewAdapter(query: Query?):FirestoreAdapter<CustomViewHolder>(query) {


        private val requestOptions: RequestOptions = RequestOptions().transforms(CenterCrop(), RoundedCorners(90))
        private val storageReference: StorageReference = FirebaseStorage.getInstance().reference
        private val myUid = FirebaseAuth.getInstance().currentUser!!.uid

        @NonNull
        override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): CustomViewHolder {
            val view: View = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_select_user, parent, false)
            return CustomViewHolder(view)
        }

        override fun onBindViewHolder(@NonNull viewHolder: CustomViewHolder, position: Int) {
            val documentSnapshot: DocumentSnapshot =
                getSnapshot(position)
            val userModel: UserModel = documentSnapshot.toObject<UserModel>(UserModel::class.java)!!
            if (myUid == userModel.uid) {
                viewHolder.itemView.visibility = View.INVISIBLE
                viewHolder.itemView.layoutParams.height = 0
                return
            }

            viewHolder.user_name.text = userModel.usernm
            viewHolder.itemView.context
            if (userModel.userphoto == null) {
                Glide.with(viewHolder.itemView.context).load(R.drawable.user)
                    .apply(requestOptions)
                    .into(viewHolder.user_photo)
            } else {
                Glide.with(viewHolder.itemView.context)
                    .load(storageReference.child("userPhoto/" + userModel.userphoto))
                    .apply(requestOptions)
                    .into(viewHolder.user_photo)
            }
            viewHolder.userChk.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedUsers[userModel.uid!!] = userModel.usernm!!
                } else {
                    selectedUsers.remove(userModel.uid)
                }
            }
        }

    }
     class CustomViewHolder internal constructor(view: View) :
        RecyclerView.ViewHolder(view) {
        var user_photo: ImageView = view.findViewById(R.id.user_photo)
        var user_name: TextView = view.findViewById(R.id.user_name)
        var userChk: CheckBox = view.findViewById(R.id.userChk)
    }
}
