package com.lfhx;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class OvhClient {

    private final String endpoint;
    private final String appKey;
    private final String appSecret;
    private final String consumerKey;

    public OvhClient(String endpoint, String appKey, String appSecret, String consumerKey) {
        this.endpoint = endpoint;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.consumerKey = consumerKey;
    }

    // GET 请求
    public String get(String path) throws IOException {
        // 构建完整的 URL
        String url = endpoint + path;
        System.out.println("url=" + url);

        // 创建 HttpClient 实例
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // 创建 GET 请求
            HttpGet request = new HttpGet(url);

            // 设置请求头
            request.setHeader("X-Ovh-Application", appKey);
            request.setHeader("X-Ovh-Consumer", consumerKey);

            // 执行请求
            HttpResponse response = client.execute(request);

            // 获取响应实体并转换为字符串
            String responseString = EntityUtils.toString(response.getEntity());

            // 返回响应字符串
            return responseString;
        }
    }

    // POST 请求
    public JSONObject post(String path, String body) throws IOException {
        // 构建完整的 URL
        String url = endpoint + path;

        // 创建 HttpClient 实例
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // 创建 POST 请求
            HttpPost request = new HttpPost(url);
            String signature = calculateOvhSignature(appSecret, consumerKey, "POST", url, body == null ? "" : body, System.currentTimeMillis() / 1000);
            // 设置请求头
            request.setHeader("Content-Type", "application/json");
            request.setHeader("X-Ovh-Application", appKey);
            request.setHeader("X-Ovh-Consumer", consumerKey);
            request.setHeader("X-Ovh-Timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            request.setHeader("X-Ovh-Signature", signature);

            // 设置请求体
            if (body != null && !body.isEmpty()) {
                request.setEntity(new org.apache.http.entity.StringEntity(body));
            }

            // 执行请求
            HttpResponse response = client.execute(request);

            // 获取响应实体并转换为字符串
            String responseString = EntityUtils.toString(response.getEntity());

            // 解析 JSON 响应
            return JSON.parseObject(responseString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getPlan(String path, String planCode) {
        // 构建完整的 URL
        String url = endpoint + path +"?planCode="+planCode;
        System.out.println("url=" + url +"?planCode="+planCode);

        // 创建 HttpClient 实例
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // 创建 GET 请求
            HttpGet request = new HttpGet(url);

//            // 设置请求头
//            request.setHeader("X-Ovh-Application", appKey);
//            request.setHeader("X-Ovh-Consumer", consumerKey);

            // 执行请求
            HttpResponse response = client.execute(request);

            // 获取响应实体并转换为字符串
            String responseString = EntityUtils.toString(response.getEntity());

            // 返回响应字符串
            return responseString;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public String calculateOvhSignature(String appSecret, String consumerKey, String method, String url, String body, long timestamp) throws Exception {
        String toSign = appSecret + "+" + consumerKey + "+" + method + "+" + url + "+" + body + "+" + timestamp;
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hash = sha1.digest(toSign.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder("$1$");
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
