package com.example.fbchattingtest.photoview

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.example.fbchattingtest.R
import com.example.fbchattingtest.functionals.helpers
import com.example.fbchattingtest.models.Message
import com.example.fbchattingtest.models.UserModel
import com.github.chrisbanes.photoview.PhotoView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_view_pager.*
import java.io.File

class ViewPagerActivity: AppCompatActivity() {

    companion object {
        lateinit var roomID:String
        lateinit var  realname:String
        lateinit var  viewPager: ViewPager
        lateinit var  imgList : ArrayList<Message>
    }

    private val rootPath = helpers.rootPath + "/DirectTalk9/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_pager)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        roomID = intent.getStringExtra("roomID")
        realname = intent.getStringExtra("realname")
        viewPager = findViewById(R.id.view_pager)
        viewPager.adapter = SamplePagerAdapter()

        downloadBtn.setOnClickListener {

            if (!helpers.isPermissionGranted(applicationContext as Activity, WRITE_EXTERNAL_STORAGE))
                return@setOnClickListener

            val message = imgList[viewPager.currentItem]
            val localFile = File(rootPath, message.filename)
            FirebaseStorage.getInstance().reference.child("files/" + message.getMsg()).getFile(localFile).addOnSuccessListener{
                helpers.showMessage(applicationContext, "Downloaded file")
                Log.e("DirectTalk9 ", "local file created $localFile")
            }.addOnFailureListener{
                Log.e("DirectTalk9 ", "local file not created $it")
            }

        }
        val actionBar = supportActionBar
        actionBar?.setTitle("PhotoView")
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setHomeButtonEnabled(true)
    }
    override fun onOptionsItemSelected(item: MenuItem):Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    inner class SamplePagerAdapter: PagerAdapter() {
        private val storageReference: StorageReference = FirebaseStorage.getInstance().reference
        private var inx = -1

        init{
            FirebaseFirestore.getInstance().collection("rooms").document(roomID).collection("messages").whereEqualTo("msgtype", "1")
                .get()
                .addOnCompleteListener{
                    if (!it.isSuccessful) {
                        return@addOnCompleteListener
                    }
                    for (document in it.result!!)
                    {
                        val message = document.toObject(Message::class.java)
                        imgList.add(message)
                        if (realname == message.msg) {
                            inx = imgList.size - 1
                        }
                    }
                    notifyDataSetChanged()
                    if (inx > -1)
                    {
                        viewPager.currentItem = inx
                    }
                }


        }
        override fun getCount() : Int = imgList.size
        override fun instantiateItem(container:ViewGroup, position:Int):View {
            val photoView = PhotoView(container.context)
            photoView.id = R.id.photoView
            Glide.with(container.context)
                .load(storageReference.child("filesmall/" + imgList.get(position).getMsg()))
                .into(photoView)
            container.addView(photoView, MATCH_PARENT, MATCH_PARENT)
            return photoView
        }
        override fun destroyItem(container:ViewGroup, position:Int, `object`:Any) {
            container.removeView(`object` as View)
        }
        override fun isViewFromObject(view:View, `object`:Any):Boolean {
            return view === `object`
        }
    }
}