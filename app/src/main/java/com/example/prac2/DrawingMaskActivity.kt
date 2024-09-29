package com.example.prac2

import android.app.AlertDialog
import android.content.Intent
import android.graphics.* // 그래픽 관련 API들 사용
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import kotlinx.coroutines.* // 코루틴 사용 (비동기 작업 처리)
import okhttp3.OkHttpClient //okhttp. RESTAPI
import okhttp3.Request
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.* // 날짜 관련 처리
import java.util.concurrent.TimeUnit

class DrawingMaskActivity : AppCompatActivity() {

    // 비트맵을 사용해 마스크를 그리고 이를 캔버스에 적용하기 위한 변수들
    private lateinit var maskBitmap: Bitmap
    private lateinit var maskCanvas: Canvas
    private lateinit var paint: Paint
    private lateinit var eraserPaint: Paint
    private lateinit var imageView: ImageView
    private lateinit var drawingView: View
    private var isErasing = false // 현재 지우개 모드인지 확인하는 변수
    private var imageMatrix = Matrix() // 이미지 스케일/이동 등을 위한 행렬
    private var inverseMatrix = Matrix() // 터치 이벤트 처리 시 역행렬
    private lateinit var savedMaskPath: String // 저장된 마스크 경로
    private lateinit var resultImagePath: String // API 처리 후 결과 이미지 경로

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing_mask)

        // Intent로 전달받은 이미지의 URI 정보 획득
        val photoUriString = intent.getStringExtra("photo_uri")
        val photoUri = Uri.parse(photoUriString)

        // 이미지 표시와 그리기 뷰 초기화
        imageView = findViewById(R.id.imageViewPhoto)
        drawingView = findViewById(R.id.drawingView)

        // 페인트(그리기 도구) 설정 - 흰색 선, 두께 10, 부드러운 곡선
        paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
        }

        // 지우개 설정 - 투명한 선으로 지움
        eraserPaint = Paint().apply {
            color = Color.TRANSPARENT
            style = Paint.Style.STROKE
            strokeWidth = 10f
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // 캔버스를 투명하게 지움
            isAntiAlias = true
        }

        // 이미지를 스트림으로 불러옴 (사진 URI로부터 InputStream 획득)
        val inputStream = contentResolver.openInputStream(photoUri)
        var bitmap = BitmapFactory.decodeStream(inputStream) // Bitmap 생성

        // 비트맵이 정상적으로 로드되었는지 확인
        if (bitmap != null) {
            // 1. 이미지를 리사이징 (1600x1600 이내로 조정)
            bitmap = resizeBitmap(bitmap, 1600, 1600)

            // 2. 마스크용 비트맵 생성 (불러온 이미지 크기와 동일한 투명한 비트맵 생성)
            maskBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            maskCanvas = Canvas(maskBitmap) // 마스크를 그리기 위한 캔버스
            maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) // 투명한 캔버스 초기화

            // 3. 이미지를 ImageView에 설정
            imageView.setImageBitmap(bitmap)

            // 4. 레이아웃이 완료된 후 그리기 뷰 크기 및 이미지 매트릭스 설정
            imageView.doOnLayout {
                adjustDrawingView(bitmap) // 이미지 크기에 맞추어 그리기 뷰 조정
            }

            // 5. 그리기 뷰에서 터치 이벤트 처리 (그리기 또는 지우기)
            drawingView.setOnTouchListener { _, event ->
                // 터치 좌표를 이미지의 좌표로 변환
                val touchPoint = floatArrayOf(event.x, event.y)
                inverseMatrix.mapPoints(touchPoint)

                val x = touchPoint[0]
                val y = touchPoint[1]

                // 터치 좌표가 이미지 범위 내에 있는지 확인
                if (x >= 0 && y >= 0 && x <= bitmap.width && y <= bitmap.height) {
                    // 터치 이벤트에 따른 동작 (그리기 또는 지우기)
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            // 지우기 모드일 경우 지우개로 그림, 아니면 흰색으로 그림
                            if (isErasing) {
                                maskCanvas.drawCircle(x, y, eraserPaint.strokeWidth / 2, eraserPaint)
                            } else {
                                maskCanvas.drawCircle(x, y, paint.strokeWidth / 2, paint)
                            }
                            // ImageView에 업데이트
                            updateImageView(bitmap)
                        }
                    }
                }
                true // 터치 이벤트를 처리했음을 반환
            }
        } else {
            // 이미지 로드 실패 시 에러 로그 출력 및 토스트 메시지 표시
            Log.e("DrawingMaskActivity", "Failed to load image")
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }

        // 뒤로 가기 버튼 클릭 시 갤러리 화면으로 이동
        val btnBackMain = findViewById<ImageView>(R.id.btnBackMain)
        btnBackMain.setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        }

        // 마스크 저장 및 API 호출 버튼 클릭 이벤트 처리
        val ivCheck = findViewById<ImageView>(R.id.ivCheck)
        ivCheck.setOnClickListener {
            try {
                // 마스크 이미지를 흑백으로 변환 후 저장
                val blackAndWhiteMask = convertMaskToBlackAndWhite()
                saveConvertedMaskImage(blackAndWhiteMask)

                // API 호출하여 이미지 및 마스크 전송
                sendImageToAPI(photoUri, savedMaskPath)
            } catch (e: Exception) {
                // 에러 발생 시 로그 출력 및 메시지 표시
                e.printStackTrace()
                Toast.makeText(this, "Error in processing or API call", Toast.LENGTH_SHORT).show()
            }
        }

        // 연필 버튼 클릭 시 선 두께 선택 다이얼로그 표시
        val ivPencil = findViewById<ImageView>(R.id.ivPencil)
        ivPencil.setOnClickListener {
            showStrokeWidthDialog() // 연필 두께 선택 다이얼로그
        }
    }

    // 비트맵 크기를 주어진 최대 크기 내로 리사이즈하는 함수
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 너비와 높이 비율을 유지하며 크기 조정
        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        // 가로가 더 긴 이미지일 경우
        if (width > height) {
            newWidth = if (width > maxWidth) maxWidth else width
            newHeight = (newWidth / aspectRatio).toInt() // 비율에 맞추어 높이 계산
        } else { // 세로가 더 긴 경우
            newHeight = if (height > maxHeight) maxHeight else height
            newWidth = (newHeight * aspectRatio).toInt() // 비율에 맞추어 너비 계산
        }

        // 크기가 조정된 새로운 비트맵 반환
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // API로 이미지를 전송하는 함수
    private fun sendImageToAPI(imageUri: Uri, maskPath: String) {
        // 코루틴을 사용해 비동기적으로 API 호출
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // OkHttpClient 설정 (타임아웃 30초)
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                // 이미지 파일 및 마스크 파일 불러오기
                val imageFile = getFileFromUri(imageUri)
                val maskFile = File(maskPath)

                // 이미지와 마스크 크기 비교 및 크기 맞추기
                val imageBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                val maskBitmap = BitmapFactory.decodeFile(maskFile.absolutePath)

                // 이미지가 마스크와 크기가 다르면 이미지 크기 조정
                val resizedImageBitmap = if (imageBitmap.width != maskBitmap.width || imageBitmap.height != maskBitmap.height) {
                    Bitmap.createScaledBitmap(imageBitmap, maskBitmap.width, maskBitmap.height, true)
                } else {
                    imageBitmap
                }

                // 리사이즈된 이미지를 임시 파일로 저장
                val resizedImageFile = saveResizedImageToTempFile(resizedImageBitmap)

                // 멀티파트 요청을 생성해 이미지와 마스크를 API로 전송
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image_file", "image.jpg", resizedImageFile.asRequestBody("image/jpeg".toMediaType()))
                    .addFormDataPart("mask_file", "mask.png", maskFile.asRequestBody("image/png".toMediaType()))
                    .addFormDataPart("mode", "quality")
                    .build()

                // API 요청 생성 (API 키 필요)
                val request = Request.Builder()
                    .header("x-api-key", "b17f3de033a064a14784d5e749ec08d113fed2a8d8bdefc9c2fecf80f56751c69218c80b17457f01e0fb4d56293f3704") // 실제 API 키로 변경해야 함
                    .url("https://clipdrop-api.co/cleanup/v1")
                    .post(requestBody)
                    .build()

                // API 호출 및 응답 처리
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response") // 응답이 실패했을 경우 예외 발생
                }

                // 응답 데이터를 받아서 이미지로 저장
                val responseData = response.body?.bytes()
                if (responseData != null) {
                    resultImagePath = saveResultImage(responseData)
                    // 응답이 성공하면 BeforeAfterActivity로 이동
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@DrawingMaskActivity, BeforeAfterActivity::class.java)
                        intent.putExtra("resultImagePath", resultImagePath)
                        intent.putExtra("originalImageUri", imageUri.toString())
                        startActivity(intent)
                    }
                }
            } catch (e: IOException) {
                // 예외 발생 시 로그 출력 및 메시지 표시
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DrawingMaskActivity, "Failed to fetch API", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 임시 파일에 리사이즈된 이미지를 저장하는 함수
    private fun saveResizedImageToTempFile(bitmap: Bitmap): File {
        // 임시 파일 생성
        val file = File(cacheDir, "resized_image.jpg")
        // 파일을 압축해서 JPEG 형식으로 저장
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return file // 저장된 파일 반환
    }

    // API 응답 데이터를 이미지 파일로 저장하는 함수
    private fun saveResultImage(imageData: ByteArray): String {
        // 현재 시간을 기반으로 파일명 생성
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        // "omnibus"라는 디렉토리에 이미지 저장 (없으면 생성)
        val galleryDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "omnibus")
        if (!galleryDir.exists()) {
            galleryDir.mkdirs()
        }

        // 결과 이미지 파일 생성
        val file = File(galleryDir, "RESULT_$timeStamp.png")
        val outputStream = FileOutputStream(file)
        outputStream.write(imageData) // 이미지 데이터 작성
        outputStream.flush()
        outputStream.close()

        // 저장된 이미지를 갤러리에 반영
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val contentUri = Uri.fromFile(file)
        mediaScanIntent.data = contentUri
        sendBroadcast(mediaScanIntent) // 갤러리에 반영

        return file.absolutePath // 저장된 파일 경로 반환
    }

    // URI로부터 파일을 가져오는 함수
    private fun getFileFromUri(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri) // URI로부터 InputStream 열기
        val tempFile = File.createTempFile("temp_image", ".jpg", cacheDir) // 임시 파일 생성
        val outputStream = FileOutputStream(tempFile)

        // InputStream을 파일로 복사
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        return tempFile // 생성된 파일 반환
    }

    // 이미지 뷰 크기에 맞추어 그리기 뷰 크기 조정
    private fun adjustDrawingView(bitmap: Bitmap) {
        val imageViewWidth = imageView.width // 이미지 뷰의 너비
        val imageViewHeight = imageView.height // 이미지 뷰의 높이
        val imageWidth = bitmap.width // 비트맵 이미지의 너비
        val imageHeight = bitmap.height // 비트맵 이미지의 높이

        val scale: Float // 이미지 스케일 계산
        val dx: Float // x축 이동값
        val dy: Float // y축 이동값

        // 이미지 비율에 따라 스케일 및 이동값 계산
        if (imageWidth * imageViewHeight > imageViewWidth * imageHeight) {
            scale = imageViewWidth.toFloat() / imageWidth.toFloat()
            dx = 0f
            dy = (imageViewHeight - imageHeight * scale) * 0.5f
        } else {
            scale = imageViewHeight.toFloat() / imageHeight.toFloat()
            dx = (imageViewWidth - imageWidth * scale) * 0.5f
            dy = 0f
        }

        // 이미지 스케일 및 이동 설정
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)
        imageView.imageMatrix = imageMatrix // 이미지 뷰에 적용
        imageMatrix.invert(inverseMatrix) // 터치 좌표 변환을 위한 역행렬 계산

        // 그리기 뷰 크기 조정
        val params = drawingView.layoutParams
        params.width = imageViewWidth
        params.height = imageViewHeight
        drawingView.layoutParams = params
    }

    // 선 두께 및 지우개 선택 다이얼로그 표시
    private fun showStrokeWidthDialog() {
        val inflater = LayoutInflater.from(this) // 레이아웃 인플레이터 초기화
        val view = inflater.inflate(R.layout.dialog_select_stroke_width, null) // 선택창 레이아웃 설정
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Tool and Width") // 다이얼로그 제목 설정
            .setView(view) // 다이얼로그에 뷰 설정
            .create() // 다이얼로그 생성

        // 각 버튼 클릭 시 선 두께 설정
        val btnThickness10 = view.findViewById<Button>(R.id.btnThickness10)
        val btnThickness25 = view.findViewById<Button>(R.id.btnThickness25)
        val btnThickness50 = view.findViewById<Button>(R.id.btnThickness50)
        val btnEraser10 = view.findViewById<Button>(R.id.btnEraser10)
        val btnEraser25 = view.findViewById<Button>(R.id.btnEraser25)
        val btnEraser50 = view.findViewById<Button>(R.id.btnEraser50)

        // 연필 두께 10 설정
        btnThickness10.setOnClickListener {
            setPaintProperties(10f, false) // 연필 모드, 두께 10
            dialog.dismiss() // 다이얼로그 닫기
        }

        // 연필 두께 25 설정
        btnThickness25.setOnClickListener {
            setPaintProperties(25f, false) // 연필 모드, 두께 25
            dialog.dismiss()
        }

        // 연필 두께 50 설정
        btnThickness50.setOnClickListener {
            setPaintProperties(50f, false) // 연필 모드, 두께 50
            dialog.dismiss()
        }

        // 지우개 두께 10 설정
        btnEraser10.setOnClickListener {
            setPaintProperties(10f, true) // 지우개 모드, 두께 10
            dialog.dismiss()
        }

        // 지우개 두께 25 설정
        btnEraser25.setOnClickListener {
            setPaintProperties(25f, true) // 지우개 모드, 두께 25
            dialog.dismiss()
        }

        // 지우개 두께 50 설정
        btnEraser50.setOnClickListener {
            setPaintProperties(50f, true) // 지우개 모드, 두께 50
            dialog.dismiss()
        }

        dialog.show() // 다이얼로그 표시
    }

    // 페인트 속성 설정 함수 (선 두께 및 지우개 여부)
    private fun setPaintProperties(strokeWidth: Float, isEraser: Boolean) {
        if (isEraser) {
            eraserPaint.strokeWidth = strokeWidth // 지우개 두께 설정
            isErasing = true // 지우개 모드 활성화
        } else {
            paint.strokeWidth = strokeWidth // 연필 두께 설정
            paint.color = Color.WHITE // 연필 색상 설정
            paint.xfermode = null // 연필 모드 활성화
            isErasing = false // 지우개 모드 비활성화
        }
    }

    // 마스크와 원본 이미지를 결합하여 ImageView에 표시하는 함수
    private fun updateImageView(originalBitmap: Bitmap) {
        // 마스크와 원본 이미지를 결합한 새로운 비트맵 생성
        val overlayBitmap = Bitmap.createBitmap(maskBitmap.width, maskBitmap.height, maskBitmap.config)
        val overlayCanvas = Canvas(overlayBitmap)
        overlayCanvas.drawBitmap(originalBitmap, 0f, 0f, null) // 원본 이미지 그리기
        overlayCanvas.drawBitmap(maskBitmap, 0f, 0f, null) // 마스크 이미지 덮어쓰기
        imageView.setImageBitmap(overlayBitmap) // 결합된 이미지 표시
    }

    // 마스크 이미지를 흑백으로 변환하는 함수
    private fun convertMaskToBlackAndWhite(): Bitmap {
        // 마스크 크기와 동일한 흑백 비트맵 생성
        val blackAndWhiteBitmap = Bitmap.createBitmap(maskBitmap.width, maskBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blackAndWhiteBitmap)
        val paint = Paint().apply {
            color = Color.BLACK // 검은색 페인트 설정
            style = Paint.Style.FILL
        }

        // 검은 배경을 그린 후
        canvas.drawRect(0f, 0f, maskBitmap.width.toFloat(), maskBitmap.height.toFloat(), paint)

        // 마스크는 흰색으로 덮어씀
        paint.color = Color.WHITE
        canvas.drawBitmap(maskBitmap, 0f, 0f, paint)

        return blackAndWhiteBitmap // 변환된 흑백 비트맵 반환
    }

    // 변환된 마스크 이미지를 저장하는 함수
    private fun saveConvertedMaskImage(bitmap: Bitmap) {
        try {
            // 파일명을 현재 시간으로 생성
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MASK_$timeStamp.png")
            val outputStream = FileOutputStream(file)

            // PNG 형식으로 저장
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            // 저장 경로 저장
            savedMaskPath = file.absolutePath

            // 갤러리에 반영 (미디어 스캔)
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val contentUri = Uri.fromFile(file)
            mediaScanIntent.data = contentUri
            sendBroadcast(mediaScanIntent)

            // 저장 완료 메시지 표시
            Toast.makeText(this, "Mask image saved: $savedMaskPath", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // 저장 실패 시 예외 처리
            e.printStackTrace()
            Toast.makeText(this, "Failed to save mask image", Toast.LENGTH_LONG).show()
        }
    }
}