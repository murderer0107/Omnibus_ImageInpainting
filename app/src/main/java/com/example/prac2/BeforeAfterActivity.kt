package com.example.prac2

import android.media.MediaScannerConnection
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class BeforeAfterActivity : AppCompatActivity() {

    // 비포 애프터 이미지를 표시하는 ImageView와 결과 이미지 경로를 저장할 변수
    private lateinit var imageViewPhotoBefore: ImageView
    private lateinit var imageViewPhotoAfter: ImageView
    private lateinit var savedResultPath: String // 결과 이미지 경로 저장

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_before_after)

        // 이미지 뷰 초기화
        imageViewPhotoBefore = findViewById(R.id.imageViewPhotoBefore)
        imageViewPhotoAfter = findViewById(R.id.imageViewPhotoAfter)

        // Intent에서 전달된 원본 이미지 URI 및 결과 이미지 경로 받기
        val originalImageUri = intent.getStringExtra("originalImageUri")
        val resultImagePath = intent.getStringExtra("resultImagePath")

        // 1. 원본 이미지 로딩 및 표시
        if (originalImageUri != null) {
            // ContentResolver를 통해 Uri에서 Bitmap 생성
            val originalBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(Uri.parse(originalImageUri)))
            imageViewPhotoBefore.setImageBitmap(originalBitmap) // ImageView에 원본 이미지 표시
        } else {
            // 원본 이미지 로딩 실패 시 오류 메시지 출력
            Toast.makeText(this, "Failed to load original image", Toast.LENGTH_SHORT).show()
        }

        // 2. 결과 이미지 로딩 및 표시
        if (resultImagePath != null) {
            // 결과 이미지 경로 저장
            savedResultPath = resultImagePath
            val resultBitmap = BitmapFactory.decodeFile(resultImagePath) // 파일에서 비트맵 생성
            imageViewPhotoAfter.setImageBitmap(resultBitmap) // ImageView에 결과 이미지 표시
        } else {
            // 결과 이미지 로딩 실패 시 오류 메시지 출력
            Toast.makeText(this, "Failed to load result image", Toast.LENGTH_SHORT).show()
        }

        // 뒤로 가기 버튼 클릭 시 액티비티 종료
        val btnBackDrawingMask = findViewById<ImageView>(R.id.btnBackDrawingMask)
        btnBackDrawingMask.setOnClickListener {
            finish() // 현재 액티비티를 종료하여 이전 화면으로 돌아감
        }

        // 저장 버튼 클릭 시 결과 이미지를 갤러리에 저장
        val ivCheck = findViewById<ImageView>(R.id.ivCheck)
        ivCheck.setOnClickListener {
            // 결과 이미지가 로드되었는지 확인
            if (::savedResultPath.isInitialized) {
                // 파일 경로에서 결과 이미지 비트맵 로드
                val resultBitmap = BitmapFactory.decodeFile(savedResultPath)
                saveImageToGallery(resultBitmap) // 결과 이미지를 갤러리에 저장
            } else {
                // 결과 이미지가 없을 경우 오류 메시지 출력
                Toast.makeText(this, "No result image to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 갤러리에 이미지를 저장하는 함수
    private fun saveImageToGallery(bitmap: Bitmap) {
        try {
            // 현재 시간을 기준으로 파일명 생성
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            // 파일 경로 및 이름 설정
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SAVED_$timeStamp.jpg")
            val outputStream = FileOutputStream(file)
            // 비트맵을 JPEG 형식으로 저장 (품질 100%)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            // 미디어 스캔을 통해 저장된 이미지를 갤러리에 반영
            MediaScannerConnection.scanFile(this, arrayOf(file.toString()), null, null)

            // 저장 성공 메시지 표시
            Toast.makeText(this, "Image saved to gallery: ${file.absolutePath}", Toast.LENGTH_LONG).show()

            // MainActivity로 돌아가고, 이전 액티비티들을 모두 종료
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish() // 현재 액티비티 종료
        } catch (e: Exception) {
            // 저장 실패 시 예외 처리
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image to gallery", Toast.LENGTH_LONG).show()
        }
    }
}
