package com.example.pdf;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

public class PhotoViewerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private Button btnSave, btnCancel;
    private ImageView fullImageView;

    private TextView tvPhotoNumber;
    private FrameLayout zoomViewLayout;
    private ArrayList<String> photoPaths;
    private PhotoAdapter adapter;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private float scaleFactor = 1.0f;
    private Matrix matrix = new Matrix();

    // Số cột trong grid
    private static final int GRID_COLUMNS = 3;

    // Thread pool để xử lý ảnh
    private ExecutorService executor;
    private Handler mainHandler;

    // Cache bitmap để tránh load lại nhiều lần
    private Map<String, Bitmap> bitmapCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);

        // Khởi tạo thread pool và handler
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mainHandler = new Handler(Looper.getMainLooper());
        bitmapCache = new HashMap<>();

        // Get photo paths from intent
        photoPaths = getIntent().getStringArrayListExtra("photo_paths");
        if (photoPaths == null) {
            photoPaths = new ArrayList<>();
        }

        // Initialize views
        recyclerView = findViewById(R.id.recyclerViewPhotos);
        btnSave = findViewById(R.id.btnSaveOrder);
        btnCancel = findViewById(R.id.btnCancel);
        fullImageView = findViewById(R.id.fullImageView);
        zoomViewLayout = findViewById(R.id.zoomViewLayout);
        tvPhotoNumber = findViewById(R.id.tvPhotoNumber);

        // Setup recycler view với GridLayoutManager
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, GRID_COLUMNS);
        recyclerView.setLayoutManager(gridLayoutManager);

        // Cài đặt caching cho RecyclerView để cải thiện hiệu suất khi scroll
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        // Tạo adapter
        adapter = new PhotoAdapter(photoPaths);
        recyclerView.setAdapter(adapter);

        // Setup drag and drop reordering
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new PhotoItemTouchHelperCallback(adapter));
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // Setup buttons
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Chỉ lưu những ảnh đã được chọn theo thứ tự người dùng đã chọn
                ArrayList<String> selectedPhotosPaths = adapter.getSelectedPhotosPaths();
                if (selectedPhotosPaths.isEmpty()) {
                    Toast.makeText(PhotoViewerActivity.this, "Vui lòng chọn ít nhất một ảnh", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent resultIntent = new Intent();
                resultIntent.putStringArrayListExtra("reordered_paths", selectedPhotosPaths);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        // Setup zoom gestures
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
        gestureDetector = new GestureDetector(this, new GestureListener());

        // Update photo count
        updatePhotoCount();

        // Tải trước hình ảnh
        preloadBitmaps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dọn dẹp tài nguyên khi activity bị hủy
        if (executor != null) {
            executor.shutdown();
        }

        // Xóa cache bitmap
        clearBitmapCache();
    }

    // Xóa cache bitmap để giải phóng bộ nhớ
    private void clearBitmapCache() {
        if (bitmapCache != null) {
            for (Bitmap bitmap : bitmapCache.values()) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            bitmapCache.clear();
        }
    }

    // Tải trước bitmap trong background thread
    private void preloadBitmaps() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (String path : photoPaths) {
                    if (!bitmapCache.containsKey(path)) {
                        Bitmap bitmap = decodeSampledBitmapFromPath(path, 120, 160);
                        bitmapCache.put(path, bitmap);
                    }
                }
            }
        });
    }

    // Tải bitmap với độ phân giải thích hợp để tiết kiệm bộ nhớ
    private Bitmap decodeSampledBitmapFromPath(String path, int reqWidth, int reqHeight) {
        // Đầu tiên decode với inJustDecodeBounds=true để kiểm tra kích thước
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // Tính toán inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap với inSampleSize được thiết lập
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565; // Sử dụng cấu hình bitmap tiết kiệm bộ nhớ
        return BitmapFactory.decodeFile(path, options);
    }

    // Tính toán tỉ lệ thu nhỏ phù hợp
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Chiều cao và rộng gốc của hình ảnh
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Tính toán tỉ lệ lớn nhất mà vẫn lớn hơn kích thước yêu cầu
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private void updatePhotoCount() {
        int selectedCount = adapter != null ? adapter.getSelectedCount() : 0;
        tvPhotoNumber.setText("Đã chọn: " + selectedCount + "/" + photoPaths.size() + " ảnh");
    }

    // Show full image for zooming
    private void showFullImage(final String path) {
        zoomViewLayout.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        btnCancel.setVisibility(View.GONE);

        // Reset zoom
        scaleFactor = 1.0f;
        matrix.reset();
        fullImageView.setScaleType(ImageView.ScaleType.MATRIX);

        // Load image in background thread
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = BitmapFactory.decodeFile(path);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        fullImageView.setImageBitmap(bitmap);
                    }
                });
            }
        });

        // Add back button
        ImageButton btnBack = findViewById(R.id.btnBackFromZoom);
        btnBack.setVisibility(View.VISIBLE);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zoomViewLayout.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                btnSave.setVisibility(View.VISIBLE);
                btnCancel.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (zoomViewLayout.getVisibility() == View.VISIBLE) {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true;
        }
        return super.onTouchEvent(event);
    }

    // Scale listener for pinch zoom
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 5.0f));

            matrix.reset();
            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            fullImageView.setImageMatrix(matrix);
            return true;
        }
    }

    // Gesture listener for pan
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            matrix.postTranslate(-distanceX, -distanceY);
            fullImageView.setImageMatrix(matrix);
            return true;
        }
    }

    // Adapter for the photo recycler view
    private class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
        private ArrayList<String> photoPaths;
        private ArrayList<Boolean> selectedItems; // Mảng lưu trạng thái đã chọn của các ảnh
        private Map<Integer, Integer> selectionOrder; // Map lưu thứ tự chọn (position -> order number)
        private int nextOrderNumber = 1; // Số thứ tự tiếp theo sẽ được gán

        public PhotoAdapter(ArrayList<String> photoPaths) {
            this.photoPaths = photoPaths;
            this.selectedItems = new ArrayList<>();
            this.selectionOrder = new HashMap<>();

            // Khởi tạo trạng thái ban đầu: TẤT CẢ ảnh đều BỎ CHỌN
            for (int i = 0; i < photoPaths.size(); i++) {
                selectedItems.add(false);
            }
        }

        // Lấy số lượng ảnh đã chọn
        public int getSelectedCount() {
            return selectionOrder.size();
        }

        // Lấy danh sách đường dẫn của các ảnh đã chọn theo đúng thứ tự người dùng đã chọn
        public ArrayList<String> getSelectedPhotosPaths() {
            ArrayList<String> selectedPaths = new ArrayList<>();

            // Tạo mảng tạm lưu cặp giá trị (vị trí, thứ tự)
            ArrayList<Map.Entry<Integer, Integer>> entries = new ArrayList<>(selectionOrder.entrySet());

            // Sắp xếp theo thứ tự tăng dần (1, 2, 3,...)
            entries.sort((e1, e2) -> e1.getValue().compareTo(e2.getValue()));

            // Thêm đường dẫn theo thứ tự đã sắp xếp
            for (Map.Entry<Integer, Integer> entry : entries) {
                selectedPaths.add(photoPaths.get(entry.getKey()));
            }

            return selectedPaths;
        }

        @Override
        public PhotoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_photo, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final PhotoViewHolder holder, int position) {
            final String path = photoPaths.get(position);

            // Sử dụng bitmap từ cache nếu có sẵn
            if (bitmapCache.containsKey(path)) {
                holder.imageView.setImageBitmap(bitmapCache.get(path));
            } else {
                // Nếu chưa có trong cache, load trong background
                holder.imageView.setImageBitmap(null); // Clear image view

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap bitmap = decodeSampledBitmapFromPath(path, 120, 160);

                        // Lưu vào cache
                        bitmapCache.put(path, bitmap);

                        // Update UI trên main thread
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // Kiểm tra xem ViewHolder có còn được sử dụng cho position này không
                                if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                                    holder.imageView.setImageBitmap(bitmap);
                                }
                            }
                        });
                    }
                });
            }

            // Hiển thị số thứ tự nếu ảnh đã được chọn
            final int adapterPosition = position;
            if (selectedItems.get(adapterPosition)) {
                Integer orderNumber = selectionOrder.get(adapterPosition);
                if (orderNumber != null) {
                    holder.tvPhotoNumber.setText(String.valueOf(orderNumber));
                    holder.tvPhotoNumber.setVisibility(View.VISIBLE);
                }
            } else {
                holder.tvPhotoNumber.setVisibility(View.INVISIBLE);
            }

            // Long press để phóng to ảnh
            holder.imageView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showFullImage(path);
                    return true;
                }
            });

            // Cập nhật trạng thái checkbox
            holder.checkBox.setOnCheckedChangeListener(null); // Xóa listener cũ
            holder.checkBox.setChecked(selectedItems.get(adapterPosition));

            holder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    int adapterPosition = holder.getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        if (isChecked) {
                            // Khi chọn ảnh: thêm vào danh sách và gán số thứ tự mới
                            selectedItems.set(adapterPosition, true);
                            selectionOrder.put(adapterPosition, nextOrderNumber++);
                            holder.tvPhotoNumber.setText(String.valueOf(selectionOrder.get(adapterPosition)));
                            holder.tvPhotoNumber.setVisibility(View.VISIBLE);
                        } else {
                            // Khi bỏ chọn: xóa khỏi danh sách và cập nhật lại thứ tự
                            selectedItems.set(adapterPosition, false);
                            Integer removedOrder = selectionOrder.remove(adapterPosition);
                            holder.tvPhotoNumber.setVisibility(View.INVISIBLE);

                            // Cập nhật lại thứ tự cho tất cả các ảnh có số thứ tự lớn hơn số vừa xóa
                            if (removedOrder != null) {
                                boolean needFullUpdate = false;
                                for (Map.Entry<Integer, Integer> entry : selectionOrder.entrySet()) {
                                    if (entry.getValue() > removedOrder) {
                                        selectionOrder.put(entry.getKey(), entry.getValue() - 1);
                                        needFullUpdate = true;
                                    }
                                }
                                // Cập nhật lại số thứ tự tiếp theo
                                nextOrderNumber = selectionOrder.isEmpty() ? 1 : getMaxOrderNumber() + 1;

                                // Chỉ cập nhật toàn bộ nếu cần thiết
                                if (needFullUpdate) {
                                    notifyDataSetChanged();
                                }
                            }
                        }

                        // Cập nhật số lượng ảnh đã chọn
                        updatePhotoCount();
                    }
                }
            });
        }

        // Lấy số thứ tự lớn nhất hiện tại
        private int getMaxOrderNumber() {
            int max = 0;
            for (Integer order : selectionOrder.values()) {
                if (order > max) {
                    max = order;
                }
            }
            return max;
        }

        @Override
        public int getItemCount() {
            return photoPaths.size();
        }

        // Di chuyển item trong danh sách (kéo thả)
        public void moveItem(int fromPosition, int toPosition) {
            // Kiểm tra cả hai vị trí đều đã được chọn
            if (selectedItems.get(fromPosition) && selectedItems.get(toPosition)) {
                // Lấy thứ tự của hai ảnh
                Integer fromOrder = selectionOrder.get(fromPosition);
                Integer toOrder = selectionOrder.get(toPosition);

                if (fromOrder != null && toOrder != null) {
                    // Hoán đổi thứ tự
                    selectionOrder.put(fromPosition, toOrder);
                    selectionOrder.put(toPosition, fromOrder);

                    notifyItemChanged(fromPosition);
                    notifyItemChanged(toPosition);
                }
            } else {
                // Hiển thị thông báo
                Toast.makeText(PhotoViewerActivity.this,
                        "Chỉ có thể sắp xếp giữa các ảnh đã chọn", Toast.LENGTH_SHORT).show();
            }
        }

        // Kiểm tra xem item có được chọn không
        public boolean isItemSelected(int position) {
            return position >= 0 && position < selectedItems.size() && selectedItems.get(position);
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            CheckBox checkBox;
            TextView tvPhotoNumber;

            public PhotoViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageViewPhoto);
                checkBox = itemView.findViewById(R.id.checkBoxSelect);
                tvPhotoNumber = itemView.findViewById(R.id.tvItemNumber);
            }
        }
    }

    // Item touch helper for drag and drop functionality
    private class PhotoItemTouchHelperCallback extends ItemTouchHelper.Callback {
        private final PhotoAdapter adapter;

        public PhotoItemTouchHelperCallback(PhotoAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getAdapterPosition();
            // Chỉ cho phép kéo thả nếu item đã được chọn
            if (position != RecyclerView.NO_POSITION && adapter.isItemSelected(position)) {
                // Hỗ trợ kéo thả theo cả ngang và dọc cho grid layout
                int dragFlags = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT | ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                return makeMovementFlags(dragFlags, 0);
            } else {
                // Không cho phép kéo thả nếu item chưa được chọn
                return makeMovementFlags(0, 0);
            }
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source,
                              RecyclerView.ViewHolder target) {
            int fromPos = source.getAdapterPosition();
            int toPos = target.getAdapterPosition();

            if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                return false;
            }

            // Chỉ di chuyển nếu cả hai vị trí đều đã được chọn
            if (adapter.isItemSelected(fromPos) && adapter.isItemSelected(toPos)) {
                adapter.moveItem(fromPos, toPos);
                return true;
            } else {
                if (!adapter.isItemSelected(toPos)) {
                    Toast.makeText(PhotoViewerActivity.this,
                            "Chỉ có thể sắp xếp giữa các ảnh đã chọn", Toast.LENGTH_SHORT).show();
                }
                return false;
            }
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            // Không sử dụng
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            // Kiểm tra khi bắt đầu kéo
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                int position = viewHolder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && !adapter.isItemSelected(position)) {
                    Toast.makeText(PhotoViewerActivity.this,
                            "Vui lòng chọn ảnh trước khi sắp xếp", Toast.LENGTH_SHORT).show();
                }
            }
            super.onSelectedChanged(viewHolder, actionState);
        }
    }
}