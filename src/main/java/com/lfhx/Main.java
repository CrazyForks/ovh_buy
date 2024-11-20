package com.lfhx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 配置文件中以抢购 ks-le-c来验证锁单可行性
 *
 *
 * 实际操作中:
 * 若抢购的ks-le-b, 配置为
 * plancode:25skleb01
 * options:bandwidth-300-25skle ram-32g-ecc-2400-25skle softraid-2x450nvme-25skle
 *
 * 如果要抢ks-,配置为
 * plancode: 24ska01
 * options: bandwidth-100-24sk ram-64g-noecc-2133-24ska01 softraid-1x480ssd-24ska01
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String CONFIG_FILE = "src/main/resources/application.properties";

    private static final Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration file", e);
        }
    }

    private static final String APP_KEY = props.getProperty("APP_KEY");
    private static final String APP_SECRET = props.getProperty("APP_SECRET");
    private static final String CONSUMER_KEY = props.getProperty("CONSUMER_KEY");
    private static final String END_POINT = props.getProperty("END_POINT");
//    private static final String TG_TOKEN = props.getProperty("TG_TOKEN");
//    private static final String TG_CHAT_ID = props.getProperty("TG_CHAT_ID");
    private static final String IAM = props.getProperty("IAM");
    private static final String ZONE = props.getProperty("ZONE");
    private static final String PLAN_CODE = props.getProperty("PLAN_CODE");
    private static final String OPTIONS = props.getProperty("OPTIONS");
    private static final Boolean AUTO_PAY = Boolean.parseBoolean(props.getProperty("AUTO_PAY"));
    //Bark 通知方式
    private static final String BARK_TOKEN_URL = props.getProperty("BARK_TOKEN_URL");

    public static void main(String[] args) {
        if (APP_KEY == null || APP_SECRET == null||CONSUMER_KEY ==null) {
            throw new IllegalArgumentException("APP_KEY ;APP_SECRET ;CONSUMER_KEY must be set");
        }
        while (true) {
            runTask();
            try {
                Thread.sleep(10000); // Sleep for 10 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Task interrupted: " + e.getMessage());
            }
        }
    }

    public static void runTask() {
        log.info("runTask: {}","++++++++++++++++++++ task start +++++++++++++++++++++++");

//        System.out.println("Task");
        try {
            OvhClient client = new OvhClient(END_POINT, APP_KEY, APP_SECRET, CONSUMER_KEY);
            String response = client.getPlan("/dedicated/server/datacenter/availabilities",PLAN_CODE);
            log.info("response:{}", response);
            JSONArray result = JSON.parseArray(response);

            boolean foundAvailable = false;
            String fqn = null, planCode = null, datacenter = null;

            for (Object obj : result) {
                JSONObject item = (JSONObject) obj;
                if (PLAN_CODE.equals(item.getString("planCode"))) {
                    fqn = item.getString("fqn");
                    planCode = item.getString("planCode");
                    JSONArray datacenters = item.getJSONArray("datacenters");

                    for (Object dcObj : datacenters) {
                        JSONObject dc = (JSONObject) dcObj;
                        String availability = dc.getString("availability");
                        datacenter = dc.getString("datacenter");

                        log.info("FQN:{}", fqn);
                        log.info("Availability:{} " , availability);
                        log.info("Datacenter: {}" , datacenter);
                        log.info("------------------------");

                        if (!"unavailable".equals(availability)) {
                            foundAvailable = true;
                            break;
                        }
                    }

                    if (foundAvailable) {
                        log.info("Proceeding to next step with FQN: " + fqn + " Datacenter: " + datacenter);
                        break;
                    }
                }
            }

            if (!foundAvailable) {
                log.info("No record to buy");
                return;
            }

            String msg = IAM + ": found "+PLAN_CODE+ " available at " + datacenter;
            sendBarkMsg(msg);

            log.info("Create cart");
            Map<String, String> cartPayload = new HashMap<>();
            cartPayload.put("ovhSubsidiary", ZONE);
            JSONObject cartResult = client.post("/order/cart", JSON.toJSONString(cartPayload));

            String cartID = cartResult.getString("cartId");
            log.info("Cart ID: {}" , cartID);

            log.info("Assign cart");
            client.post("/order/cart/" + cartID + "/assign", null);

            log.info("Put item into cart");
            Map<String, Object> itemPayload = new HashMap<>();
            itemPayload.put("planCode", planCode);
            itemPayload.put("pricingMode", "default");
            itemPayload.put("duration", "P1M");
            itemPayload.put("quantity", 1);
            JSONObject itemResult = client.post("/order/cart/" + cartID + "/eco", JSON.toJSONString(itemPayload));

            String itemID = itemResult.getString("itemId");
            log.info("Item ID: {}" , itemID);

            log.info("Checking required configuration");
            String configResponse = client.get("/order/cart/" + cartID + "/item/" + itemID + "/requiredConfiguration");
            JSONArray requiredConfig = JSON.parseArray(configResponse);

            String dedicatedOs = "none_64.en";
            String regionValue = null;
            for (Object configObj : requiredConfig) {
                JSONObject config = (JSONObject) configObj;
                if ("region".equals(config.getString("label"))) {
                    List<String> allowedValues = config.getJSONArray("allowedValues").toJavaList(String.class);
                    if (!allowedValues.isEmpty()) {
                        regionValue = allowedValues.get(0);
                    }
                }
            }

            Map<String, String> configurations = new HashMap<>();
            configurations.put("dedicated_datacenter", datacenter);
            configurations.put("dedicated_os", dedicatedOs);
            configurations.put("region", regionValue);

            for (Map.Entry<String, String> entry : configurations.entrySet()) {
                log.info("Configure " + entry.getKey());
                Map<String, String> configPayload = new HashMap<>();
                configPayload.put("label", entry.getKey());
                configPayload.put("value", entry.getValue());
                client.post("/order/cart/" + cartID + "/item/" + itemID + "/configuration", JSON.toJSONString(configPayload));
            }

            log.info("---------------------------------Add options--------------------------------------");
            String[] options = OPTIONS.split(" ");
            log.info("buy_options:{}",JSONArray.toJSONString(options));
//
//           {
//                    "bandwidth-300-25skle",
//                    "ram-32g-ecc-2400-25skle",
//                    "softraid-2x450nvme-25skle"
//            };

            for (String option : options) {
                Map<String, Object> optionPayload = new HashMap<>();
                optionPayload.put("duration", "P1M");
                optionPayload.put("itemId", Integer.parseInt(itemID));
                optionPayload.put("planCode", option);
                optionPayload.put("pricingMode", "default");
                optionPayload.put("quantity", 1);
                client.post("/order/cart/" + cartID + "/eco/options", JSON.toJSONString(optionPayload));
                log.info("buy_post:{},buy_body:{}","/order/cart/" + cartID + "/eco/options",JSON.toJSONString(optionPayload));
            }

            Map<String, Object> checkoutPayload = new HashMap<>();
            checkoutPayload.put("autoPayWithPreferredPaymentMethod", AUTO_PAY);
            checkoutPayload.put("waiveRetractationPeriod", true);
            JSONObject pay_result = client.post("/order/cart/" + cartID + "/checkout", JSON.toJSONString(checkoutPayload));
            log.info("pay_result:{}",JSON.toJSONString(pay_result));
            msg = IAM +"_plan_code="+planCode+" 锁单成功;"+"订单url:"+pay_result.getString("url");
            log.info(msg);
            sendBarkMsg(msg);
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendBarkMsg(String message) throws Exception {
        if (BARK_TOKEN_URL == null || "".equals(BARK_TOKEN_URL)){
            return;
        }
        // URL encode the message to avoid illegal characters
        String encodedMessage = encodeMessage(message);

        // Create payload as a map
        Map<String, String> payload = new HashMap<>();
        payload.put("text", message);

        // Convert payload to JSON string
        String jsonData = JSON.toJSONString(payload);

        // Construct the URL with the encoded message
        String url = BARK_TOKEN_URL + "message:"+ encodedMessage;

        // Create HttpClient instance
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Create POST request
            HttpPost httpPost = new HttpPost(url);

            // Set headers
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"); // Simulate a browser request

            // Set the request body
            StringEntity entity = new StringEntity(jsonData);
            httpPost.setEntity(entity);

            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // Get the response status code
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new RuntimeException("Failed : HTTP error code : " + statusCode);
                }

                // Optionally read the response
                HttpEntity responseEntity = response.getEntity();
                String responseString = EntityUtils.toString(responseEntity);
                System.out.println("Response: " + responseString);
            }
        }
    }

    // Helper method to encode the message
    private static String encodeMessage(String message) throws UnsupportedEncodingException {
        return URLEncoder.encode(message, "UTF-8");
    }


}
