package com.example.fbchattingtest

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.example.fbchattingtest.ChatModule.SelectUserActivity
import com.example.fbchattingtest.fragments.ChatRoomFragment
import com.example.fbchattingtest.fragments.UserFragment
import com.example.fbchattingtest.fragments.UserListFragment
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.iid.FirebaseInstanceId
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null
    //lateinit var mViewPager: ViewPager
    //private var makeRoomBtn: FloatingActionButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)
        //mViewPager = findViewById<ViewPager>(R.id.container)
        container.adapter = mSectionsPagerAdapter
        val tabLayout: TabLayout = findViewById(R.id.tabs)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener{

            @SuppressLint("RestrictedApi")
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 1) {     // char room
                    makeRoomBtn.visibility = View.VISIBLE
                }
            }

            @SuppressLint("RestrictedApi")
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                makeRoomBtn.visibility = View.INVISIBLE
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        container.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(container))
        sendRegistrationToServer()
        //makeRoomBtn = findViewById(R.id.makeRoomBtn)

        @SuppressLint("RestrictedApi")
        makeRoomBtn.visibility = View.INVISIBLE
        makeRoomBtn.setOnClickListener{
            startActivity(Intent(it.context, SelectUserActivity::class.java))
        }
    }

    private fun sendRegistrationToServer() {
        val uid =
            FirebaseAuth.getInstance().currentUser!!.uid
        val token =
            FirebaseInstanceId.getInstance().token
        val map = mutableMapOf<String, String?>()
        map["token"] = token
        FirebaseFirestore.getInstance().collection("users")
            .document(uid).set(map, SetOptions.merge())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        val id = item.itemId

        if (id == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut()
            val intent =
                Intent(this, LoginActivity::class.java)
            this.startActivity(intent)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

       override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> UserListFragment()
                1 -> ChatRoomFragment()
                else -> UserFragment()
            }
        }

        override fun getCount(): Int {
            return 3
        }
    }
}
