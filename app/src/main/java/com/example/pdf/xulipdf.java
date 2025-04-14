package com.example.pdf;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.view.MotionEvent;
import android.graphics.Matrix;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import android.widget.ImageButton;
import java.io.ByteArrayOutputStream;

public class xulipdf extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final int STORAGE_PERMISSION_REQUEST = 101;
    private static final int MANAGE_EXTERNAL_STORAGE_REQUEST = 102;
    private static final float A4_RATIO = 1f / 1.41f;
    private static final int PHOTO_VIEWER_REQUEST = 103;
    private ImageButton btnViewPhotos;
    private Camera camera;
    private CameraPreview cameraPreview;
    private Button btnCapture, btnSave, btnShare;
    private File photoFile;
    private File pdfFile;
    private Button btnExit;
    private boolean hasPhoto = false;
    private ArrayList<File> photoFiles = new ArrayList<>(); // Store multiple photos
    private TextView capturedCountTextView; // Show how many photos captured
    private View a4GuideOverlay;
    private boolean isGuideVisible = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xuli);
        // Khởi tạo và thiết lập khung hướng dẫn A4
        a4GuideOverlay = findViewById(R.id.a4_guide_overlay);
        setupA4Guide();

        // Initialize the TextView for photo count
        capturedCountTextView = findViewById(R.id.tvCapturedCount);
        updateCapturedCount(); // Initialize the count


        // Check for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            setupCamera();
        }
        btnExit = findViewById(R.id.btnExit);
        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExitConfirmation();
            }
        });
        // Request storage permission
        //requestStoragePermission();

        // Initialize buttons
        btnCapture = findViewById(R.id.btnCapture);
        btnSave = findViewById(R.id.btnSave);
        btnShare = findViewById(R.id.btnShare);
        btnViewPhotos = findViewById(R.id.btnViewPhotos);

        // Setup capture button
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capturePhoto();
            }
        });

        // Setup view photos button with proper intent
        btnViewPhotos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (photoFiles.size() > 0) {
                    // Create intent for PhotoViewerActivity (you need to create this class)
                    Intent intent = new Intent(xulipdf.this, PhotoViewerActivity.class);

                    // Convert the file paths to strings for intent passing
                    ArrayList<String> photoPaths = new ArrayList<>();
                    for (File file : photoFiles) {
                        photoPaths.add(file.getAbsolutePath());
                    }

                    intent.putStringArrayListExtra("photo_paths", photoPaths);
                    startActivityForResult(intent, PHOTO_VIEWER_REQUEST);
                } else {
                    Toast.makeText(xulipdf.this, "Chưa có ảnh nào để hiển thị", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Setup save button
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasPhoto) {
                    saveToPdf();
                } else {
                    Toast.makeText(xulipdf.this, "Vui lòng chụp ảnh trước", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Setup share button
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pdfFile != null && pdfFile.exists()) {
                    shareDocument(pdfFile);
                } else if (hasPhoto) {
                    // If no PDF exists, create PDF first and then share
                    saveToPdfAndShare();
                } else {
                    Toast.makeText(xulipdf.this, "Vui lòng chụp ảnh trước", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    // Thêm phương thức showExitConfirmation vào lớp xulipdf
    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Thoát ứng dụng")
                .setMessage("Bạn có muốn thoát ứng dụng không?")
                .setPositiveButton("Có", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Dọn dẹp tài nguyên trước khi thoát
                        if (camera != null) {
                            camera.release();
                            camera = null;
                        }
                        cleanupTempFiles();

                        finishAffinity();  // For API 16+
                    }
                })
                .setNegativeButton("Không", null)
                .show();
    }
    // Thêm phương thức mới để thiết lập khung hướng dẫn A4
    private void setupA4Guide() {
        if (a4GuideOverlay == null) return;

        // Đảm bảo khung hướng dẫn hiển thị đúng tỷ lệ A4
        a4GuideOverlay.post(new Runnable() {
            @Override
            public void run() {
                int parentWidth = ((View)a4GuideOverlay.getParent()).getWidth();
                int parentHeight = ((View)a4GuideOverlay.getParent()).getHeight();

                // Tính toán kích thước khung A4 dựa trên tỷ lệ 1:1.41
                int guideWidth, guideHeight;
                float a4Ratio = A4_RATIO; // Tỷ lệ A4 (width:height) đã định nghĩa

                // Tính toán dựa trên không gian có sẵn
                if (parentWidth * 1.41f <= parentHeight) {
                    // Nếu giới hạn bởi chiều rộng
                    guideWidth = (int)(parentWidth * 0.9f); // Để lại lề 5% mỗi bên
                    guideHeight = (int)(guideWidth / a4Ratio);
                } else {
                    // Nếu giới hạn bởi chiều cao
                    guideHeight = (int)(parentHeight * 0.9f); // Để lại lề 5% mỗi bên
                    guideWidth = (int)(guideHeight * a4Ratio);
                }

                // Cập nhật LayoutParams của khung hướng dẫn
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(guideWidth, guideHeight);
                params.gravity = android.view.Gravity.CENTER;
                a4GuideOverlay.setLayoutParams(params);

                // Đảm bảo khung hướng dẫn luôn hiển thị với độ trong suốt phù hợp
                a4GuideOverlay.setVisibility(View.VISIBLE);
                a4GuideOverlay.setAlpha(0.7f);  // Đặt độ trong suốt phù hợp
            }
        });
    }
    private void updateCapturedCount() {
        if (capturedCountTextView != null) {
            capturedCountTextView.setText("Đã chụp: " + photoFiles.size() + " ảnh");
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE for full storage access
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Vui lòng cấp quyền truy cập bộ nhớ để lưu ảnh vào thư mục Downloads", Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST);
                }
            }
        } else {
            // Android < 11 uses traditional permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, STORAGE_PERMISSION_REQUEST);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST) {
            // Handle storage permission result
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Đã cấp quyền truy cập bộ nhớ", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Quyền truy cập bộ nhớ bị từ chối", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == PHOTO_VIEWER_REQUEST && resultCode == RESULT_OK) {
            // Handle reordered photos from PhotoViewerActivity
            if (data != null && data.hasExtra("reordered_paths")) {
                ArrayList<String> reorderedPaths = data.getStringArrayListExtra("reordered_paths");
                // Update photoFiles list based on reordered paths
                photoFiles.clear();
                for (String path : reorderedPaths) {
                    photoFiles.add(new File(path));
                }
                updateCapturedCount();
                Toast.makeText(this, "Thứ tự ảnh đã được cập nhật", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera();
            } else {
                Toast.makeText(this, "Không có quyền truy cập camera", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Không có quyền truy cập bộ nhớ", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Sửa phương thức setupCamera để thêm xử lý tỷ lệ khung hình
    private void setupCamera() {
        try {
            camera = Camera.open();

            // Set camera orientation
            setCameraDisplayOrientation();

            // Optimize image quality
            Camera.Parameters parameters = camera.getParameters();

            // Set focus area to match A4 ratio in the preview
            List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();
            Camera.Size bestSize = getBestPictureSizeForA4(pictureSizes);
            parameters.setPictureSize(bestSize.width, bestSize.height);

            // Set autofocus mode
            if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            // Set highest image quality
            parameters.setJpegQuality(100);

            camera.setParameters(parameters);

            // Set up camera preview
            cameraPreview = new CameraPreview(this, camera);
            FrameLayout preview = findViewById(R.id.camera_preview);

            // Option 1: Only remove the camera preview, not the guide overlay
            for (int i = 0; i < preview.getChildCount(); i++) {
                View child = preview.getChildAt(i);
                if (child instanceof SurfaceView) {
                    preview.removeView(child);
                    i--; // Adjust index after removal
                }
            }

            // Add camera preview at index 0 so it's behind the guide overlay
            preview.addView(cameraPreview, 0);
            // Đảm bảo khung hướng dẫn luôn hiển thị
            if (a4GuideOverlay != null) {
                a4GuideOverlay.bringToFront();
                a4GuideOverlay.setAlpha(0.7f);  // Luôn hiển thị với độ trong suốt này
            }

        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Thay thế phương thức getBestPictureSize bằng phương thức này để lấy kích thước phù hợp với A4
    private Camera.Size getBestPictureSizeForA4(List<Camera.Size> sizes) {
        Camera.Size bestSize = sizes.get(0);
        float targetRatio = A4_RATIO; // tỷ lệ A4 (width:height)
        float bestMatchRatio = Float.MAX_VALUE;

        for (Camera.Size size : sizes) {
            float ratio = (float) size.width / size.height;
            float matchQuality = Math.abs(ratio - targetRatio);

            // Ưu tiên kích thước phù hợp với tỷ lệ A4 và có độ phân giải cao
            if (matchQuality < bestMatchRatio) {
                bestSize = size;
                bestMatchRatio = matchQuality;
            } else if (matchQuality == bestMatchRatio &&
                    (size.width * size.height > bestSize.width * bestSize.height)) {
                // Nếu tỷ lệ giống nhau, chọn kích thước lớn hơn
                bestSize = size;
            }
        }

        return bestSize;
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result = (info.orientation - degrees + 360) % 360;
        camera.setDisplayOrientation(result);
    }

    // Sửa lại phương thức capturePhoto để đảm bảo xử lý ảnh theo tỷ lệ A4
    private void capturePhoto() {
        if (camera != null) {
            try {
                // Autofocus before capturing
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        // Take photo after focusing
                        camera.takePicture(null, null, new Camera.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, Camera camera) {
                                try {
                                    // Process image orientation
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                    Bitmap rotatedBitmap = correctImageOrientation(bitmap);

                                    // Crop to match A4 guide area
                                    Bitmap croppedBitmap = cropToA4Ratio(rotatedBitmap);

                                    // Save to cache directory
                                    photoFile = createImageFileInCache();
                                    FileOutputStream fos = new FileOutputStream(photoFile);
                                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                    fos.close();

                                    // Add to photo list
                                    photoFiles.add(photoFile);
                                    hasPhoto = true;
                                    updateCapturedCount();

                                    Toast.makeText(xulipdf.this, "Đã chụp ảnh " + photoFiles.size(), Toast.LENGTH_SHORT).show();

                                    // Free bitmap memory
                                    if (bitmap != rotatedBitmap) {
                                        bitmap.recycle();
                                    }
                                    if (rotatedBitmap != croppedBitmap) {
                                        rotatedBitmap.recycle();
                                    }
                                    if (!croppedBitmap.isRecycled()) {
                                        croppedBitmap.recycle();
                                    }

                                    // Restart camera preview
                                    camera.startPreview();
                                } catch (IOException e) {
                                    Toast.makeText(xulipdf.this, "Lỗi lưu ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Toast.makeText(xulipdf.this, "Lỗi chụp ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Thêm phương thức xử lý ảnh để có tỷ lệ A4
    private Bitmap cropToA4Ratio(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        float currentRatio = (float) width / height;

        // A4 ratio là 1:1.41 (width:height ở chế độ portrait)
        float targetRatio = A4_RATIO;

        // Tính toán kích thước crop
        int cropWidth = width;
        int cropHeight = height;

        if (currentRatio > targetRatio) {
            // Ảnh quá rộng so với A4, cần crop theo chiều rộng
            cropWidth = (int) (height * targetRatio);
            int startX = (width - cropWidth) / 2;
            return Bitmap.createBitmap(original, startX, 0, cropWidth, height);
        } else if (currentRatio < targetRatio) {
            // Ảnh quá cao so với A4, cần crop theo chiều cao
            cropHeight = (int) (width / targetRatio);
            int startY = (height - cropHeight) / 2;
            return Bitmap.createBitmap(original, 0, startY, width, cropHeight);
        }

        // Nếu tỷ lệ đã phù hợp, không cần crop
        return original;
    }

    // Replace createImageFileInDownloads with this method
    private File createImageFileInCache() throws IOException {
        // Create filename from timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + ".jpg";

        // Use app's cache directory instead of Downloads
        File cacheDir = getCacheDir();
        File image = new File(cacheDir, imageFileName);
        return image;
    }

    // Add this method to delete temporary files
    private void cleanupTempFiles() {
        // We'll only delete the files that were successfully added to PDF
        ArrayList<File> filesToDelete = new ArrayList<>(photoFiles);
        photoFiles.clear(); // Clear the list but don't delete files yet

        // Now delete the actual files
        for (File file : filesToDelete) {
            if (file.exists() && file.getPath().startsWith(getCacheDir().getPath())) {
                file.delete();
            }
        }

        // Reset state
        hasPhoto = false;
        updateCapturedCount();
    }
    // Call this in onDestroy() or after PDF creation
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupTempFiles();
    }

    private Bitmap correctImageOrientation(Bitmap bitmap) {
        // Get camera orientation info
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);

        // Get device orientation
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        // Calculate rotation angle
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // Front camera needs to be mirrored
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }

        // Rotate image if needed
        if (result != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(result);
            try {
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                return rotatedBitmap;
            } catch (OutOfMemoryError e) {
                // If not enough memory, return original bitmap
                return bitmap;
            }
        }
        return bitmap;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            try {
                if (camera != null) {
                    Camera.Parameters parameters = camera.getParameters();
                    if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        camera.setParameters(parameters);
                        camera.autoFocus(null);
                        Toast.makeText(xulipdf.this, "Đang lấy nét...", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
            } catch (Exception e) {
                // Ignore focus errors
            }
        }
        return super.onTouchEvent(event);
    }

    private File createImageFileInDownloads() throws IOException {
        // Create filename from timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + ".jpg";

        // Use Downloads directory
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        File image = new File(storageDir, imageFileName);
        return image;
    }

    private void scanFile(File file) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(file);
        mediaScanIntent.setData(contentUri);
        sendBroadcast(mediaScanIntent);
    }

    private void saveToPdf() {
        if (photoFiles.isEmpty()) {
            Toast.makeText(this, "Chưa có ảnh nào để chuyển đổi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show dialog to enter file name
        final EditText input = new EditText(this);
        input.setHint("Nhập tên file PDF");

        new AlertDialog.Builder(this)
                .setTitle("Lưu PDF")
                .setView(input)
                .setPositiveButton("Lưu", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String filename = input.getText().toString().trim();
                        if (filename.isEmpty()) {
                            // If no name entered, use timestamp
                            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                            filename = "PDF_" + timeStamp;
                        }

                        // Ensure filename has .pdf extension
                        if (!filename.toLowerCase().endsWith(".pdf")) {
                            filename += ".pdf";
                        }

                        createMultiPagePdf(filename);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Sửa lại phương thức createMultiPagePdf để đảm bảo mỗi trang PDF có kích thước A4
    private void createMultiPagePdf(String filename) {
        // Show processing dialog before starting the operation
        showProcessingDialog();

        // To avoid blocking the UI thread, perform the PDF creation in a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Create PDF document
                    PdfDocument document = new PdfDocument();

                    // Add each image to a PDF page
                    for (int i = 0; i < photoFiles.size(); i++) {
                        File photoFile = photoFiles.get(i);
                        if (photoFile.exists()) {
                            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                            if (bitmap != null) {
                                // Định nghĩa kích thước trang A4 (595 x 842 points ở 72dpi)
                                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, i + 1).create();
                                PdfDocument.Page page = document.startPage(pageInfo);

                                Canvas canvas = page.getCanvas();

                                // Tính toán tỷ lệ scale để fit ảnh vào trang A4
                                float scale = Math.min(
                                        (float) pageInfo.getPageWidth() / bitmap.getWidth(),
                                        (float) pageInfo.getPageHeight() / bitmap.getHeight()
                                );

                                // Tính toán vị trí để căn giữa ảnh
                                float left = (pageInfo.getPageWidth() - (bitmap.getWidth() * scale)) / 2;
                                float top = (pageInfo.getPageHeight() - (bitmap.getHeight() * scale)) / 2;

                                // Thiết lập ma trận chuyển đổi để scale và căn giữa ảnh
                                Matrix matrix = new Matrix();
                                matrix.postScale(scale, scale);
                                matrix.postTranslate(left, top);

                                // Vẽ ảnh đã scale lên canvas
                                canvas.drawBitmap(bitmap, matrix, null);

                                document.finishPage(page);
                                bitmap.recycle();
                            }
                        }
                    }

                    // Save PDF to Downloads with chosen name
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs();
                    }

                    pdfFile = new File(downloadsDir, filename);
                    FileOutputStream fos = new FileOutputStream(pdfFile);
                    document.writeTo(fos);
                    fos.close();
                    document.close();

                    // Notify Media Scanner
                    scanFile(pdfFile);

                    // Clean up temporary image files
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Tắt dialog xử lý
                            dismissProcessingDialog();
                            Toast.makeText(xulipdf.this, "Đã lưu PDF vào Downloads: " + pdfFile.getName(), Toast.LENGTH_LONG).show();

                            // Hỏi người dùng có muốn xóa ảnh không
                            new AlertDialog.Builder(xulipdf.this)
                                    .setTitle("Xóa ảnh")
                                    .setMessage("Bạn có muốn xóa các ảnh đã chụp để chụp mới không?")
                                    .setPositiveButton("Có", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            cleanupTempFiles();
                                        }
                                    })
                                    .setNegativeButton("Không", null)
                                    .show();
                        }
                    });
                } catch (Exception e) {
                    final String errorMessage = e.getMessage();
                    // Update UI on the main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Dismiss processing dialog on error
                            dismissProcessingDialog();
                            Toast.makeText(xulipdf.this, "Lỗi tạo PDF: " + errorMessage, Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    });
                }
            }
        }).start();
    }

    private void saveToPdfAndShare() {
        if (photoFiles.isEmpty()) {
            Toast.makeText(this, "Chưa có ảnh nào để chuyển đổi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show dialog to enter file name
        final EditText input = new EditText(this);
        input.setHint("Nhập tên file PDF");

        new AlertDialog.Builder(this)
                .setTitle("Lưu và Chia sẻ PDF")
                .setView(input)
                .setPositiveButton("Tiếp tục", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String filename = input.getText().toString().trim();
                        if (filename.isEmpty()) {
                            // If no name entered, use timestamp
                            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                            filename = "PDF_" + timeStamp;
                        }

                        // Ensure filename has .pdf extension
                        if (!filename.toLowerCase().endsWith(".pdf")) {
                            filename += ".pdf";
                        }

                        createMultiPagePdfAndShare(filename);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Cập nhật createMultiPagePdfAndShare tương tự như createMultiPagePdf
    private void createMultiPagePdfAndShare(String filename) {
        // Show processing dialog
        showProcessingDialog();

        // Run PDF creation in background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Create PDF document
                    PdfDocument document = new PdfDocument();
                    for (int i = 0; i < photoFiles.size(); i++) {
                        File photoFile = photoFiles.get(i);
                        if (photoFile.exists()) {
                            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                            if (bitmap != null) {
                                // Định nghĩa kích thước trang A4 (595 x 842 points ở 72dpi)
                                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, i + 1).create();
                                PdfDocument.Page page = document.startPage(pageInfo);

                                Canvas canvas = page.getCanvas();

                                // Tính toán tỷ lệ scale để fit ảnh vào trang A4
                                float scale = Math.min(
                                        (float)pageInfo.getPageWidth() / bitmap.getWidth(),
                                        (float)pageInfo.getPageHeight() / bitmap.getHeight()
                                );

                                // Tính toán vị trí để căn giữa ảnh
                                float left = (pageInfo.getPageWidth() - (bitmap.getWidth() * scale)) / 2;
                                float top = (pageInfo.getPageHeight() - (bitmap.getHeight() * scale)) / 2;

                                // Thiết lập ma trận chuyển đổi để scale và căn giữa ảnh
                                Matrix matrix = new Matrix();
                                matrix.postScale(scale, scale);
                                matrix.postTranslate(left, top);

                                // Vẽ ảnh đã scale lên canvas
                                canvas.drawBitmap(bitmap, matrix, null);

                                document.finishPage(page);
                                bitmap.recycle();
                            }
                        }
                    }

                    // Save PDF to Downloads
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs();
                    }

                    pdfFile = new File(downloadsDir, filename);
                    FileOutputStream fos = new FileOutputStream(pdfFile);
                    document.writeTo(fos);
                    fos.close();
                    document.close();

                    // Notify Media Scanner
                    scanFile(pdfFile);

                    final File finalPdfFile = pdfFile;

                    // Update UI on main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Tắt dialog xử lý
                            dismissProcessingDialog();
                            Toast.makeText(xulipdf.this, "Đã lưu PDF vào Downloads: " + finalPdfFile.getName(), Toast.LENGTH_SHORT).show();

                            // Chia sẻ PDF
                            shareDocument(finalPdfFile);

                            // Hỏi người dùng có muốn xóa ảnh không
                            new AlertDialog.Builder(xulipdf.this)
                                    .setTitle("Xóa ảnh")
                                    .setMessage("Bạn có muốn xóa các ảnh đã chụp để chụp mới không?")
                                    .setPositiveButton("Có", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            cleanupTempFiles();
                                        }
                                    })
                                    .setNegativeButton("Không", null)
                                    .show();
                        }
                    });

                } catch (Exception e) {
                    final String errorMessage = e.getMessage();

                    // Update UI on main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Dismiss processing dialog on error
                            dismissProcessingDialog();
                            Toast.makeText(xulipdf.this, "Lỗi tạo PDF: " + errorMessage, Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    });
                }
            }
        }).start();
    }

    private void shareDocument(File file) {
        try {
            // Ensure your manifest has the proper FileProvider configuration
            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);

            // Set the right MIME type
            if (file.getName().endsWith(".pdf")) {
                shareIntent.setType("application/pdf");
            } else {
                shareIntent.setType("image/jpeg");
            }

            // Add these flags to grant temporary permissions
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Show explicit chooser with messaging apps
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ qua"));
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi chia sẻ tài liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera();
        }
    }

    // Phương thức tối ưu bitmap cho tỷ lệ A4
    private Bitmap createOptimizedBitmapForA4(Bitmap original) {
        // A4 có tỷ lệ 1:1.41 (width:height)
        float targetRatio = 1f / 1.41f;

        int width = original.getWidth();
        int height = original.getHeight();
        float currentRatio = (float) width / height;

        int newWidth, newHeight;

        // Xác định kích thước mới giữ tỷ lệ A4
        if (currentRatio > targetRatio) {
            // Ảnh rộng hơn A4 - sử dụng chiều cao làm tham chiếu
            newHeight = height;
            newWidth = Math.round(height * targetRatio);
        } else {
            // Ảnh cao hơn A4 - sử dụng chiều rộng làm tham chiếu
            newWidth = width;
            newHeight = Math.round(width / targetRatio);
        }

        // Tạo bitmap mới với tỷ lệ A4
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);

        // Nén bitmap để giảm kích thước file nhưng vẫn giữ chất lượng
        Bitmap optimizedBitmap = compressBitmap(scaledBitmap, 85); // 85% chất lượng

        // Nếu tạo bitmap mới trong quá trình scale, giải phóng nó
        if (scaledBitmap != optimizedBitmap && scaledBitmap != original) {
            scaledBitmap.recycle();
        }

        return optimizedBitmap;
    }

    // Nén bitmap để giảm kích thước
    private Bitmap compressBitmap(Bitmap original, int quality) {
        // Tạo ByteArrayOutputStream để lưu ảnh đã nén
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Nén bitmap sang định dạng JPEG với chất lượng được chỉ định
        original.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);

        // Chuyển đổi dữ liệu đã nén thành bitmap
        byte[] compressedData = outputStream.toByteArray();
        Bitmap compressedBitmap = BitmapFactory.decodeByteArray(compressedData, 0, compressedData.length);

        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return compressedBitmap != null ? compressedBitmap : original;
    }

    // Hiển thị dialog quá trình xử lý
// Add this to your existing code in xulipdf.java
// Replace or update the existing showProcessingDialog and dismissProcessingDialog methods

    private AlertDialog processingDialog;

    private void showProcessingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.processing_dialog, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        processingDialog = builder.create();

        // Apply a semi-transparent background
        if (processingDialog.getWindow() != null) {
            processingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        processingDialog.show();
    }

    private void dismissProcessingDialog() {
        if (processingDialog != null && processingDialog.isShowing()) {
            try {
                processingDialog.dismiss();
            } catch (Exception e) {
                // Handle any exception that might occur when dismissing
                e.printStackTrace();
            }
        }
    }

    // Cập nhật lại CameraPreview để sử dụng tỷ lệ A4 cho preview
    private class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder holder;
        private Camera camera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            this.camera = camera;

            // Set up SurfaceHolder
            holder = getHolder();
            holder.addCallback(this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                // Set up preview
                camera.setPreviewDisplay(holder);

                // Optimize preview size for A4 ratio
                Camera.Parameters parameters = camera.getParameters();
                List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
                Camera.Size optimalSize = getOptimalPreviewSizeForA4(sizes, getWidth(), getHeight());
                parameters.setPreviewSize(optimalSize.width, optimalSize.height);
                camera.setParameters(parameters);

                // Start preview
                camera.startPreview();
            } catch (IOException e) {
                Toast.makeText(getContext(), "Lỗi camera preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        // Tương tự các methods surfaceChanged và surfaceDestroyed...
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (holder.getSurface() == null) {
                return;
            }

            try {
                camera.stopPreview();
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            } catch (IOException e) {
                Toast.makeText(getContext(), "Lỗi camera preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Camera is released in onPause()
        }

        private Camera.Size getOptimalPreviewSizeForA4(List<Camera.Size> sizes, int w, int h) {
            final double ASPECT_TOLERANCE = 0.1;
            double targetRatio = A4_RATIO; // Sử dụng tỷ lệ A4

            if (sizes == null) return null;

            Camera.Size optimalSize = null;
            double minDiff = Double.MAX_VALUE;

            // Tìm kích thước preview phù hợp nhất với tỷ lệ A4
            for (Camera.Size size : sizes) {
                float ratio = (float) size.width / size.height;
                double diff = Math.abs(ratio - targetRatio);

                if (diff < minDiff) {
                    optimalSize = size;
                    minDiff = diff;
                }
            }

            return optimalSize;
        }
    }
}