package brsh.co.PushRecord

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.AlphabeticIndex.Record
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.telephony.PhoneNumberFormattingTextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var button: Button
    private lateinit var otpButton: Button
    private lateinit var okHttpClient: OkHttpClient
    private var storedMobileNumber: String? = null // This variable will store the mobile number

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        okHttpClient = OkHttpClient()
        otpButton = findViewById(R.id.otpbutton)

        // Find the EditText view for the phone number
        val phoneNumberEditText = findViewById<EditText>(R.id.editTextPhone)

        otpButton.setOnClickListener {
            val mobileNumber = phoneNumberEditText.text.toString()
            if (otpButton.text == "התחבר") {
                verifyOtp(mobileNumber)
            } else {
                storedMobileNumber = mobileNumber // Store the mobile number when generating the OTP
                generateOtp(mobileNumber)
            }
        }

        button = findViewById(R.id.button)
        button.setOnClickListener {
            val file = getLatestRecording()
            if (file == null) {
                Toast.makeText(this, "No recording found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val mediaType = "application/octet-stream".toMediaTypeOrNull()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("phone_number", "0524371673") // Replace with the valid phone number
                .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("http://10.15.14.52:8080/upload")
                .post(requestBody)
                .build()

            val call = okHttpClient.newCall(request)
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "הקובץ עלה לענן בהצלחה", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("MainActivity", "Failed to upload file: " + response.code)
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("MainActivity", "תקלה בהעלאת קובץ", e)
                }
            })
        }
    }

    private fun generateOtp(mobileNumber: String) {
        if (mobileNumber == "05") {
            Toast.makeText(this, "הקש מספר טלפון נייד להזדהות", Toast.LENGTH_SHORT).show()
            return
        }
        val json = JSONObject()
        val otpUrl = "http://10.15.14.52:8080/generate-otp"
        json.put("phonenumber", mobileNumber)
        val jsonString = json.toString()
        val contentType = "application/json".toMediaTypeOrNull()
        val requestBody = RequestBody.create(contentType, jsonString) // Use the text from the EditText
        val request = Request.Builder()
            .url(otpUrl)
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "הקוד נשלח בהצלחה, הזן במקום הטלפון את הקוד שהתקבל במסרון", Toast.LENGTH_SHORT).show()
                        otpButton.text = "התחבר"
                        findViewById<TextView>(R.id.textView).text = "הקש קוד שהתקבל במסרון"
                        findViewById<EditText>(R.id.editTextPhone).setText("")
                    }
                } else {
                    Log.e("MainActivity", "המסרון לא נשלח אנא נסה שנית: " + response.code)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "תקלה אנא פנה לתמיכה: brsh.co.il/support", Toast.LENGTH_SHORT).show()
                }
                Log.e("MainActivity", "תקלה אנא פנה לתמיכה: brsh.co.il/support", e)
            }
        })
    }

    private fun verifyOtp(otp: String) {
        if (otp.isEmpty() || otp.length != 6) {
            Toast.makeText(this, "הקש קןד בן 6 ספרות להתחברות", Toast.LENGTH_SHORT).show()
            return
        }
        val otpUrl2 = "http://10.15.14.52:8080/verify-otp?phonenumber=$storedMobileNumber&otp=$otp" // Use the stored mobile number here
        val request2 = Request.Builder()
            .url(otpUrl2)
            .get()
            .build()
        val call2 = okHttpClient.newCall(request2)
        call2.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        otpButton.visibility = View.GONE
                        findViewById<TextView>(R.id.textView).text = "פרטי שיחה אחרונה להעלאה לענן"
                        findViewById<EditText>(R.id.editTextPhone).visibility = View.GONE
                        findViewById<Button>(R.id.button).visibility = View.VISIBLE
                        findViewById<Button>(R.id.button2).visibility = View.VISIBLE
                        hideKeyboard()

                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                // Handle failure here if needed
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getLatestRecording(): File? {
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val permission = Manifest.permission.MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                return getLatestRecordingFromStorage()
            } else {
                val packageUri = Uri.parse("package:${packageName}")
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri))
                Toast.makeText(this, "Please grant the manage all files access permission", Toast.LENGTH_LONG).show()
                return null
            }
        } else {
            if (arePermissionsGranted(requiredPermissions)) {
                return getLatestRecordingFromStorage()
            } else {
                ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
                return null
            }
        }
    }

    private fun arePermissionsGranted(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (arePermissionsGranted(permissions)) {
                val file = getLatestRecording()
                if (file != null) {
                    // Do something with the file
                } else {
                    Toast.makeText(this, "No recording found", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(button.windowToken, 0)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getLatestRecordingFromStorage(): File? {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "/Call")
        val files = directory.listFiles()
        (Log.i("record", files.sortedByDescending { it.lastModified() }.first().toString()))

    return if (files.isNotEmpty()) {
            files.sortedByDescending { it.lastModified() }.first()

        } else {
            null
        }
    }
}
