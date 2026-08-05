package com.webtools.optimizer.fragment;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.webtools.optimizer.databinding.FragmentWebBinding;
import com.webtools.optimizer.util.PrefsManager;

public class WebFragment extends Fragment {

    private static final String DEFAULT_URL = "https://www.google.com";

    private FragmentWebBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentWebBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WebView webView = binding.webview;
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                    } catch (ActivityNotFoundException ignored) {
                        // Tidak ada aplikasi yang menangani skema ini, abaikan.
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                binding.swipeRefresh.setRefreshing(false);
                binding.addressBar.setText(url);
                updateNavButtons();
                PrefsManager.saveLastUrl(requireContext(), url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    binding.swipeRefresh.setRefreshing(false);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                binding.progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                binding.progressBar.setProgress(newProgress);
            }
        });

        binding.swipeRefresh.setOnRefreshListener(webView::reload);

        binding.btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        binding.btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        binding.btnRefresh.setOnClickListener(v -> webView.reload());

        binding.addressBar.setOnEditorActionListener((v, actionId, event) -> {
            boolean isGo = actionId == EditorInfo.IME_ACTION_GO;
            boolean isEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (isGo || isEnter) {
                loadFromAddressBar();
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            webView.loadUrl(PrefsManager.getLastUrl(requireContext(), DEFAULT_URL));
        }
    }

    private void loadFromAddressBar() {
        String input = binding.addressBar.getText().toString().trim();
        if (TextUtils.isEmpty(input)) return;
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = "https://" + input;
        }
        binding.webview.loadUrl(input);
        hideKeyboard();
    }

    private void updateNavButtons() {
        boolean canBack = binding.webview.canGoBack();
        boolean canForward = binding.webview.canGoForward();
        binding.btnBack.setEnabled(canBack);
        binding.btnForward.setEnabled(canForward);
        binding.btnBack.setAlpha(canBack ? 1f : 0.4f);
        binding.btnForward.setAlpha(canForward ? 1f : 0.4f);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.addressBar.getWindowToken(), 0);
    }

    /** @return true jika WebView menangani tombol back (masih ada histori navigasi). */
    public boolean handleBackPressed() {
        if (binding != null && binding.webview.canGoBack()) {
            binding.webview.goBack();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
