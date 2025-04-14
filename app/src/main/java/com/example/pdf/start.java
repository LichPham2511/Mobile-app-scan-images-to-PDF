package com.example.pdf;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class start extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dn);

        // PHƯƠNG PHÁP 1: Sử dụng WebView để hiển thị GIF
        WebView webView = findViewById(R.id.webViewBackground);
        webView.setBackgroundColor(0); // Làm cho nền trong suốt

        // Thiết lập để hỗ trợ co dãn hình ảnh
        WebSettings webSettings = webView.getSettings();
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        // Load GIF với CSS để điều chỉnh kích thước
        String htmlData = "<html><head>" +
                "<style type='text/css'>" +
                "body {margin: 0; padding: 0;}" +
                "img {width: 100%; height: 100%; object-fit: cover; position: fixed;}" +
                "</style></head>" +
                "<body><img src='file:///android_asset/background.gif' /></body></html>";

        webView.loadDataWithBaseURL("file:///android_asset/", htmlData, "text/html", "UTF-8", null);

        // Thêm xử lý sự kiện cho nút Start
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Chuyển đến giao diện xuli.xml thông qua Activity xulipdf
                Intent intent = new Intent(start.this, xulipdf.class);
                startActivity(intent);
            }
        });
    }
}