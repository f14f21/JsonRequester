package com.alirezaafkar.json.requester.requesters;

import android.support.annotation.NonNull;
import android.widget.Toast;

import com.alirezaafkar.json.requester.R;
import com.alirezaafkar.json.requester.Requester;
import com.alirezaafkar.json.requester.interfaces.Methods;
import com.alirezaafkar.json.requester.interfaces.Response;
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import static android.text.TextUtils.isEmpty;
import static com.alirezaafkar.json.requester.CommonUtils.appendToUrl;
import static com.alirezaafkar.json.requester.CommonUtils.isClientError;

/**
 * Created by Alireza Afkar on 12/11/15 AD.
 */
public class JsonArrayRequester implements com.android.volley.Response.Listener<String>, com.android.volley.Response.ErrorListener {
    private RequestQueue mQueue;
    private RequestBuilder mBuilder;
    private Response.ArrayResponse mCallBack;

    protected JsonArrayRequester() {
    }

    protected JsonArrayRequester(RequestQueue queue, RequestBuilder builder, Response.ArrayResponse callBack) {
        mQueue = queue;
        mBuilder = builder;
        mCallBack = callBack;
    }

    /**
     * Send request using {@link Methods Methods.GET}
     */
    @SuppressWarnings("unused")
    public void request(@NonNull String url) {
        request(Methods.GET, url);
    }

    @SuppressWarnings("unused")
    public void request(@Methods.Method int method, @NonNull String url, @NonNull String body) {
        mBuilder.body = body.getBytes();
        request(method, url);
    }

    @SuppressWarnings("unused")
    public void request(@Methods.Method int method, @NonNull String url) {
        if (mCallBack != null)
            mCallBack.onRequestStart(mBuilder.requestCode);

        if (!mBuilder.ignoreBaseUrl &&
                !isEmpty(Requester.getBaseUrl())) {
            url = Requester.getBaseUrl() + url;
        }

        String param = Requester.getGeneralParam();
        if (param != null) {
            String s = url.contains("?") ? "&" : "?";
            url = appendToUrl(url, param);
        }

        StringRequest request = new StringRequest(method, url, JsonArrayRequester.this, JsonArrayRequester.this) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                if (mBuilder.header != null)
                    return mBuilder.header;
                return super.getHeaders();
            }

            @Override
            public String getBodyContentType() {
                return mBuilder.contentType;
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return mBuilder.body != null &&
                        mBuilder.body.length != 0
                        ? mBuilder.body
                        : super.getBody();
            }

            @Override
            protected String getParamsEncoding() {
                return mBuilder.encoding == null
                        ? super.getParamsEncoding()
                        : mBuilder.encoding;
            }

            @Override
            public Priority getPriority() {
                return mBuilder.priority;
            }

            @Override
            protected com.android.volley.Response<String> parseNetworkResponse(NetworkResponse response) {
                try {
                    if (!(response != null && response.data != null))
                        return com.android.volley.Response.error(null);

                    String string = new String(response.data, mBuilder.encoding);
                    return com.android.volley.Response.success(string, HttpHeaderParser
                            .parseCacheHeaders(response));
                } catch (UnsupportedEncodingException e) {
                    return com.android.volley.Response.error(new ParseError(e));
                }
            }
        };

        if (mBuilder.retry == null)
            mBuilder.retry = DefaultRetryPolicy.DEFAULT_MAX_RETRIES;

        if (mBuilder.timeOut == null)
            mBuilder.timeOut = DefaultRetryPolicy.DEFAULT_TIMEOUT_MS;

        request.setShouldCache(mBuilder.shouldCache);
        request.setRetryPolicy(new DefaultRetryPolicy(mBuilder.timeOut,
                mBuilder.retry, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        request.setTag(mBuilder.tag);
        mQueue.add(request);
    }

    @Override
    public void onResponse(String response) {
        if (mBuilder.allowNullResponse &&
                response.length() == 0 ||
                response.equalsIgnoreCase("null") ||
                response.equalsIgnoreCase("[]")) {
            sendResponse(null);
            return;
        }
        try {
            JSONArray jsonArray = new JSONArray(response);
            if (jsonArray.length() > 0) {
                sendResponse(jsonArray);
            } else {
                sendFinish(R.string.parsing_error, new ParseError());
            }
        } catch (JSONException e) {
            sendFinish(R.string.network_error, new ParseError(e));
            if (mBuilder.shouldCache && mQueue.getCache() != null)
                mQueue.getCache().clear();
        }
    }

    @Override
    public void onErrorResponse(VolleyError volleyError) {
        if (volleyError instanceof NoConnectionError) {
            sendFinish(R.string.no_connection_error, volleyError);
        } else if (volleyError instanceof NetworkError) {
            sendFinish(R.string.network_error, volleyError);
        } else if (volleyError instanceof TimeoutError) {
            sendFinish(R.string.timeout_error, volleyError);
        } else if (volleyError instanceof ServerError) {
            if (isClientError(volleyError)) {
                sendError(volleyError);
            } else {
                sendFinish(R.string.server_error, volleyError);
            }
        } else if (volleyError instanceof ParseError) {
            sendFinish(R.string.parsing_error, volleyError);
        } else {
            sendError(volleyError);
        }
    }

    @SuppressWarnings("unused")
    public void setCallback(Response.ArrayResponse callback) {
        mCallBack = callback;
    }

    private void sendResponse(JSONArray jsonArray) {
        if (mCallBack != null) {
            mCallBack.onRequestFinish(mBuilder.requestCode);
            mCallBack.onResponse(mBuilder.requestCode, jsonArray);
        }
    }

    private void sendError(VolleyError volleyError) {
        if (mCallBack == null) return;

        if (volleyError.networkResponse == null || volleyError.networkResponse.data == null) {
            sendFinish(R.string.network_error, new NetworkError());
            return;
        }

        String error = new String(volleyError.networkResponse.data);
        mCallBack.onErrorResponse(mBuilder.requestCode, volleyError, error);
        mCallBack.onRequestFinish(mBuilder.requestCode);
    }

    private void sendFinish(int message, VolleyError volleyError) {
        if (mCallBack == null) return;

        mCallBack.onFinishResponse(mBuilder.requestCode, volleyError,
                mBuilder.context.getString(message));
        mCallBack.onRequestFinish(mBuilder.requestCode);

        if (mBuilder.showError)
            Toast.makeText(mBuilder.context,
                    message, Toast.LENGTH_SHORT).show();
    }
}
