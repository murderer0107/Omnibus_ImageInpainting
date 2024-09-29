package com.example.prac2

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.GridView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.InputStream

class GalleryActivity : AppCompatActivity() {

    private lateinit var gridView: GridView  // 갤러리에서 이미지를 표시할 GridView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        // 뒤로 가기 버튼 설정
        val ivBack: ImageView = findViewById(R.id.btnBackMain)
        gridView = findViewById(R.id.gridViewPhotos)

        // 뒤로 가기 버튼 클릭 시 MainActivity로 돌아감
        ivBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        loadImagesFromGallery()  // 갤러리에서 이미지를 불러오는 함수 호출
    }

    // 갤러리에서 이미지를 불러와 GridView에 표시
    private fun loadImagesFromGallery() {
        val projection = arrayOf(MediaStore.Images.Media._ID)  // 이미지 ID를 가져오기 위한 쿼리 필드
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,  // 외부 저장소의 이미지 URI
            projection,
            null,  // 필터 조건 없음
            null,
            null
        )

        cursor?.let {
            val imageIds = mutableListOf<Long>()  // 이미지 ID 리스트
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                imageIds.add(id)  // 이미지 ID 저장
            }
            cursor.close()

            // GridView에 이미지를 표시하는 어댑터 설정
            val adapter = ImageAdapter(this, imageIds)
            gridView.adapter = adapter
        }
    }

    // 사용자가 이미지를 선택하면 호출되는 함수
    fun onImageSelected(imageUri: Uri) {
        val reducedBitmap = getResizedBitmap(imageUri)  // 이미지를 리사이즈
        val tempUri = getImageUri(reducedBitmap)  // 리사이즈된 이미지를 Uri로 변환

        // 선택한 이미지와 리사이즈 정보를 다음 액티비티로 전달
        val intent = Intent(this, DrawingMaskActivity::class.java).apply {
            putExtra("photo_uri", tempUri.toString())  // 이미지 Uri 전달
            putExtra("is_resized", true)  // 리사이즈 여부 전달
        }
        startActivity(intent)
    }

    // 선택된 이미지의 Bitmap을 리사이즈하는 함수
    private fun getResizedBitmap(imageUri: Uri): Bitmap {
        // 이미지의 InputStream을 가져와 Bitmap으로 변환
        val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        // Bitmap이 null이 아닌 경우 리사이즈, 그렇지 않으면 예외 발생
        return if (originalBitmap != null) {
            resizeBitmap(originalBitmap, 800, 800)  // 800x800으로 리사이즈
        } else {
            throw IllegalArgumentException("Failed to decode the image")  // 이미지 디코딩 실패 시 예외 처리
        }
    }

    // Bitmap을 주어진 크기로 리사이즈하는 함수
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 이미지의 종횡비를 계산
        val aspectRatio: Float = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        // 가로가 더 큰 경우 가로 크기를 기준으로 리사이즈
        if (width > height) {
            targetWidth = maxWidth
            targetHeight = (maxWidth / aspectRatio).toInt()
        } else {
            // 세로가 더 큰 경우 세로 크기를 기준으로 리사이즈
            targetHeight = maxHeight
            targetWidth = (maxHeight * aspectRatio).toInt()
        }

        // 리사이즈된 Bitmap 반환
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    // Bitmap을 Uri로 변환하는 함수
    private fun getImageUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        // Bitmap을 JPEG 형식으로 압축
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        // 압축된 이미지를 MediaStore에 저장하고 Uri 반환
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Resized_Image", null)
        return Uri.parse(path.toString())
    }
}
