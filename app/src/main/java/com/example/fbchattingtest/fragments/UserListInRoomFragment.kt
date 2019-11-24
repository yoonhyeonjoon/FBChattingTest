package com.example.fbchattingtest.fragments

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
import com.example.fbchattingtest.ChatModule.SelectUserActivity
import com.example.fbchattingtest.R
import com.example.fbchattingtest.models.UserModel
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.fragment_userlistinroom.*


class UserListInRoomFragment : Fragment() {

    companion object {
        fun getInstance(roomID: String, userModels: Map<String, UserModel>): UserListInRoomFragment {
            val users = mutableListOf<UserModel>()
            for ((_, value) in userModels) {users.add(value)}
            val f = UserListInRoomFragment()
            f.setUserList(users)
            val bdl = Bundle()
            bdl.putString("roomID", roomID)
            f.arguments = bdl
            return f
        }
    }

    private var roomID: String? = null
    private var userModels: List<UserModel>? = null
    private var recyclerView: RecyclerView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_userlistinroom, container, false)
        if (arguments != null) {roomID = arguments?.getString("roomID")}

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView!!.layoutManager = LinearLayoutManager(inflater.context)
        recyclerView!!.adapter = UserFragmentRecyclerViewAdapter()

        addContactBtn.setOnClickListener {
            val intent = Intent(activity, SelectUserActivity::class.java)
            intent.putExtra("roomID", roomID)
            startActivity(intent)
        }

        return view
    }

    fun setUserList(users: List<UserModel>) {
        userModels = users
    }

    internal inner class UserFragmentRecyclerViewAdapter :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val storageReference: StorageReference = FirebaseStorage.getInstance().reference
        private val requestOptions = RequestOptions().transforms(CenterCrop(), RoundedCorners(90))

        override fun getItemCount() = userModels!!.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
            return CustomViewHolder(view)
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val user = userModels!![position]
            val customViewHolder = holder as CustomViewHolder
            customViewHolder.user_name.text = user.usernm
            if (user.userphoto == null) {
                Glide.with(activity!!).load(R.drawable.user).apply(requestOptions).into(customViewHolder.user_photo)
            } else {
                Glide.with(activity!!).load(storageReference.child("userPhoto/" + user.userphoto)).apply(requestOptions).into(customViewHolder.user_photo)
            }
        }
    }

    private inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var user_photo: ImageView = view.findViewById(R.id.user_photo)
        var user_name: TextView = view.findViewById(R.id.user_name)
        var user_msg: TextView = view.findViewById(R.id.user_msg)
        init {
            user_msg.visibility = View.GONE
        }
    }

}