package com.example.fbchattingtest

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.fbchattingtest.functionals.helpers
import com.example.fbchattingtest.models.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_login.*


class LoginActivity : AppCompatActivity() {

    /**gujc**/
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        sharedPreferences = getSharedPreferences("sharedinfo", Context.MODE_PRIVATE)
        loginBtn.setOnClickListener {
            if (validateForm()) {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(
                    user_id!!.text.toString(),
                    user_pw!!.text.toString()
                ).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        sharedPreferences.edit().putString("user_id", user_id!!.text.toString()).apply()

                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        helpers.showMessage(applicationContext, task.exception?.message!!)
                    }
                }
            }
        }

        signupBtn.setOnClickListener {
            if (validateForm()) {
                val id = user_id!!.text.toString()

                FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(id, user_pw!!.text.toString())
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            sharedPreferences.edit().putString("user_id", id).apply()
                            val uid = FirebaseAuth.getInstance().uid
                            val userModel = UserModel()
                            userModel.uid = uid
                            userModel.userid = id
                            userModel.usernm = extractIDFromEmail(id)
                            userModel.usermsg = "..."
                            userModel.testdataset = 3

                            val db = FirebaseFirestore.getInstance()
                            db.collection("users").document(uid!!)
                                .set(userModel)
                                .addOnSuccessListener {
                                    val intent =
                                        Intent(this@LoginActivity, MainActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                    Log.e("로그인액티비티 버그 체크","DocumentSnapshot added with ID: $uid")
                                }
                        } else {
                            helpers.showMessage(applicationContext, task.exception?.message!!)
                        }
                    }
            }
        }

        val id = sharedPreferences.getString("user_id", "")
        if ("" != id) {
            user_id!!.setText(id)
        }
    }

    internal fun extractIDFromEmail(email: String): String {
        val parts = email.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return parts[0]
    }

    private fun validateForm(): Boolean {
        var valid = true

        val email = user_id!!.text.toString()
        if (TextUtils.isEmpty(email)) {
            user_id!!.error = "Required."
            valid = false
        } else {
            user_id!!.error = null
        }

        val password = user_pw!!.text.toString()
        if (TextUtils.isEmpty(password)) {
            user_pw!!.error = "Required."
            valid = false
        } else {
            user_pw!!.error = null
        }

        return valid
    }
}
