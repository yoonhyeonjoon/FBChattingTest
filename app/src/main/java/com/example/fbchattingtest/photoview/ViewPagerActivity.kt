package com.example.fbchattingtest.photoview

class ViewPagerActivity:AppCompatActivity() {
    private val rootPath = Util9.getRootPath() + "/DirectTalk9/"
    internal var downloadBtnClickListener:Button.OnClickListener = object:View.OnClickListener() {
        fun onClick(view:View) {
            if (!Util9.isPermissionGranted(view.getContext() as Activity, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            {
                return
            }
            val message = imgList.get(viewPager.getCurrentItem())
            /// showProgressDialog("Downloading File.");
            val localFile = File(rootPath, message.getFilename())
            // realname == message.msg
            FirebaseStorage.getInstance().getReference().child("files/" + message.getMsg()).getFile(localFile).addOnSuccessListener(object:OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                fun onSuccess(taskSnapshot:FileDownloadTask.TaskSnapshot) {
                    // hideProgressDialog();
                    Util9.showMessage(view.getContext(), "Downloaded file")
                    Log.e("DirectTalk9 ", "local file created " + localFile.toString())
                }
            }).addOnFailureListener(object:OnFailureListener() {
                fun onFailure(@NonNull exception:Exception) {
                    Log.e("DirectTalk9 ", "local file not created " + exception.toString())
                }
            })
        }
    }
    internal var rotateBtnClickListener:Button.OnClickListener = object:View.OnClickListener() {
        fun onClick(view:View) {
            val child = viewPager.getChildAt(viewPager.getCurrentItem())
            val photoView = child.findViewById(R.id.photoView)
            photoView.setRotation(photoView.getRotation() + 90)
        }
    }
    fun onCreate(savedInstanceState:Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_pager)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        roomID = getIntent().getStringExtra("roomID")
        realname = getIntent().getStringExtra("realname")
        viewPager = findViewById(R.id.view_pager)
        viewPager.setAdapter(SamplePagerAdapter())
        findViewById(R.id.downloadBtn).setOnClickListener(downloadBtnClickListener)
        //findViewById(R.id.rotateBtn).setOnClickListener(rotateBtnClickListener);
        val actionBar = getSupportActionBar()
        //actionBar.setIcon(R.drawable.back);
        actionBar.setTitle("PhotoView")
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeButtonEnabled(true)
    }
    fun onOptionsItemSelected(item:MenuItem):Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
    internal class SamplePagerAdapter:PagerAdapter() {
        private val storageReference:StorageReference
        private val inx = -1
        val count:Int
            get() {
                return imgList.size()
            }
        init{
            storageReference = FirebaseStorage.getInstance().getReference()
            FirebaseFirestore.getInstance().collection("rooms").document(roomID).collection("messages").whereEqualTo("msgtype", "1")
                .get()
                .addOnCompleteListener(object:OnCompleteListener<QuerySnapshot>() {
                    fun onComplete(@NonNull task:Task<QuerySnapshot>) {
                        if (!task.isSuccessful()) {
                            return
                        }
                        for (document in task.getResult())
                        {
                            val message = document.toObject(Message::class.java)
                            imgList.add(message)
                            if (realname == message.getMsg()) {
                                inx = imgList.size() - 1
                            }
                        }
                        notifyDataSetChanged()
                        if (inx > -1)
                        {
                            viewPager.setCurrentItem(inx)
                        }
                    }
                })
        }
        fun instantiateItem(container:ViewGroup, position:Int):View {
            val photoView = PhotoView(container.getContext())
            photoView.setId(R.id.photoView)
            Glide.with(container.getContext())
                .load(storageReference.child("filesmall/" + imgList.get(position).getMsg()))
                .into(photoView)
            container.addView(photoView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            return photoView
        }
        fun destroyItem(container:ViewGroup, position:Int, `object`:Any) {
            container.removeView(`object` as View)
        }
        fun isViewFromObject(view:View, `object`:Any):Boolean {
            return view === `object`
        }
    }
    companion object {
        private val roomID:String
        private val realname:String
        private val viewPager:ViewPager
        private val imgList = ArrayList()
    }
}