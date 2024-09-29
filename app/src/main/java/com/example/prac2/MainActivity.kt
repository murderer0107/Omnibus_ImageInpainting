package com.example.prac2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_IMAGE_CAPTURE = 1  // 카메라 요청 코드
    private val REQUEST_IMAGE_PICK = 2     // 갤러리 선택 요청 코드
    private lateinit var currentPhotoPath: String  // 사진 파일 경로 저장 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 갤러리 버튼을 클릭하면 이미지 선택 인텐트를 실행
        val btnGallery: ImageView = findViewById(R.id.buttonGallery)
        btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }

        // 카메라 버튼을 클릭하면 권한 확인 후 카메라 인텐트 실행
        val btnCamera: ImageView = findViewById(R.id.buttonCamera)
        btnCamera.setOnClickListener {
            // 카메라 및 저장 권한 확인
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // 권한이 없는 경우 권한 요청
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_IMAGE_CAPTURE)
            } else {
                // 권한이 있는 경우 카메라 실행
                dispatchTakePictureIntent()
            }
        }
    }

    // 카메라 인텐트 실행 함수
    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // 파일 생성 후 파일의 URI를 카메라 인텐트에 전달
            val photoFile: File? = try {
                createImageFile()  // 파일 생성 함수 호출
            } catch (ex: IOException) {
                ex.printStackTrace()
                null
            }
            photoFile?.also {
                // 파일 URI 생성 및 인텐트에 첨부
                val photoURI: Uri = FileProvider.getUriForFile(this, "com.example.prac2.fileprovider", it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }

    // 임시 이미지 파일 생성 함수
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // 파일 이름에 현재 시간을 포함하여 고유한 파일 생성
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_",  // 파일 이름 규칙
            ".jpg",               // 파일 확장자
            storageDir            // 저장 위치
        ).apply {
            currentPhotoPath = absolutePath  // 파일의 절대 경로 저장
        }
    }

    // 갤러리 또는 카메라 인텐트의 결과를 처리하는 함수
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            // 카메라 촬영 성공 시, 사진을 다음 액티비티로 전달
            val intent = Intent(this, DrawingMaskActivity::class.java).apply {
                putExtra("photo_uri", Uri.fromFile(File(currentPhotoPath)).toString())  // 파일 경로 URI를 인텐트로 전달
            }
            startActivity(intent)
        } else if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            // 갤러리 이미지 선택 성공 시, 이미지를 다음 액티비티로 전달
            data?.data?.also { uri ->
                val intent = Intent(this, DrawingMaskActivity::class.java).apply {
                    putExtra("photo_uri", uri.toString())  // 선택한 이미지 URI를 전달
                }
                startActivity(intent)
            }
        }
    }

    // 권한 요청 결과를 처리하는 함수
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // 카메라 권한이 허용된 경우 카메라 인텐트 실행
                dispatchTakePictureIntent()
            } else {
                // 권한이 거부된 경우 사용자에게 알림 처리 가능
            }
        }
    }
}
