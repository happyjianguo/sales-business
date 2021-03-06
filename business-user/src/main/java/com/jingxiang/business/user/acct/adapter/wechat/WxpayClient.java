package com.jingxiang.business.user.acct.adapter.wechat;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static com.jingxiang.business.user.acct.adapter.wechat.WxpayConsts.SignType;


/**
 * 微信支付客户端
 */
@Component
@Slf4j
public class WxpayClient {

    @Autowired
    private WxpayConfigProperties config;

    /**
     * 向 Map 中添加 appid、mch_id、nonce_str、sign_type、sign <br>
     * 该函数适用于商户适用于统一下单等接口，不适用于红包、代金券接口
     *
     * @param reqData 请求体
     * @return 请求体
     */
    public Map<String, String> fillRequestData(Map<String, String> reqData) {
        reqData.put("appid", config.getAppId());
        reqData.put("mch_id", config.getMchId());
        reqData.put("nonce_str", WxpayUtil.generateNonceStr());
        if (SignType.MD5.equals(config.getSignType())) {
            reqData.put("sign_type", WxpayConsts.MD5);
        } else if (SignType.HMACSHA256.equals(config.getSignType())) {
            reqData.put("sign_type", WxpayConsts.HMACSHA256);
        }
        reqData.put("sign", WxpayUtil.generateSignature(reqData, config.getAppKey(), config.getSignType()));
        return reqData;
    }

    /**
     * 判断xml数据的sign是否有效，必须包含sign字段，否则返回false。
     *
     * @param reqData 向wxpay post的请求数据
     * @return 签名是否有效
     */
    public boolean isResponseSignatureValid(Map<String, String> reqData) {
        // 返回数据的签名方式和请求中给定的签名方式是一致的
        return WxpayUtil.isSignatureValid(reqData, config.getAppKey(), config.getSignType());
    }

    /**
     * 判断支付结果通知中的sign是否有效
     *
     * @param reqData 向wxpay post的请求数据
     * @return 签名是否有效
     */
    public boolean isPayResultNotifySignatureValid(Map<String, String> reqData) {
        String signTypeInData = reqData.get(WxpayConsts.FIELD_SIGN_TYPE);
        SignType signType;
        if (StringUtils.isBlank(signTypeInData)) {
            signType = SignType.MD5;
        } else {
            signTypeInData = signTypeInData.trim();
            if (StringUtils.isBlank(signTypeInData) || signTypeInData.equals(WxpayConsts.MD5)) {
                signType = SignType.MD5;
            } else if (WxpayConsts.HMACSHA256.equals(signTypeInData)) {
                signType = SignType.HMACSHA256;
            } else {
                throw new IllegalArgumentException(String.format("Unsupported sign type: %s", signTypeInData));
            }
        }
        return WxpayUtil.isSignatureValid(reqData, config.getAppKey(), signType);
    }


    /**
     * 不需要证书的请求
     *
     * @param strUrl           String
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 超时时间，单位是毫秒
     * @param readTimeoutMs    超时时间，单位是毫秒
     * @return API返回数据
     */
    public String requestWithoutCert(String strUrl, Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection httpConnection;
        try {
            URL httpUrl = new URL(strUrl);
            httpConnection = (HttpURLConnection) httpUrl.openConnection();
            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod("POST");
            httpConnection.setConnectTimeout(connectTimeoutMs);
            httpConnection.setReadTimeout(readTimeoutMs);
            httpConnection.connect();
        } catch (Exception e) {
            log.error("Request connect error {} {}", strUrl, reqData, e);
            return "";
        }
        String reqBody = WxpayUtil.mapToXml(reqData);
        try (OutputStream outputStream = httpConnection.getOutputStream();
             InputStream inputStream = httpConnection.getInputStream();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            outputStream.write(reqBody.getBytes("UTF-8"));
            //获取内容
            final StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Request error {} {}", strUrl, reqData, e);
        }

        return "";
    }

    /**
     * 需要证书的请求
     *
     * @param strUrl           String
     * @param reqData          向wxpay post的请求数据  Map
     * @param connectTimeoutMs 超时时间，单位是毫秒
     * @param readTimeoutMs    超时时间，单位是毫秒
     * @return API返回数据
     */
    public String requestWithCert(String strUrl, Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection httpConnection;
        try {
            URL httpUrl = new URL(strUrl);
            char[] password = config.getMchId().toCharArray();
            InputStream certStream = config.getCertStream();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(certStream, password);

            // 实例化密钥库 & 初始化密钥工厂
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);

            // 创建SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            httpConnection = (HttpURLConnection) httpUrl.openConnection();
            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod("POST");
            httpConnection.setConnectTimeout(connectTimeoutMs);
            httpConnection.setReadTimeout(readTimeoutMs);
            httpConnection.connect();
        } catch (Exception e) {
            log.error("Request with cert error {} {}", strUrl, reqData, e);
            return "";
        }

        try (OutputStream outputStream = httpConnection.getOutputStream();
             InputStream inputStream = httpConnection.getInputStream();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String reqBody = WxpayUtil.mapToXml(reqData);
            outputStream.write(reqBody.getBytes("UTF-8"));
            String line;
            final StringBuilder sb = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Request with cert error {} {}", strUrl, reqData, e);
            return "";
        }
    }

    /**
     * 处理 HTTPS API返回数据，转换成Map对象。return_code为SUCCESS时，验证签名。
     *
     * @param xmlStr API返回的XML格式数据
     * @return Map类型数据
     */
    public Map<String, String> processResponseXml(String xmlStr) {
        String RETURN_CODE = "return_code";
        String return_code;
        Map<String, String> respData = WxpayUtil.xmlToMap(xmlStr);
        if (respData.containsKey(RETURN_CODE)) {
            return_code = respData.get(RETURN_CODE);
        } else {
            throw new IllegalStateException(String.format("No `return_code` in XML: %s", xmlStr));
        }

        switch (return_code) {
            case WxpayConsts.FAIL:
                return respData;
            case WxpayConsts.SUCCESS:
                if (isResponseSignatureValid(respData)) {
                    return respData;
                } else {
                    throw new IllegalStateException(String.format("Invalid sign value in XML: %s", xmlStr));
                }
            default:
                throw new IllegalStateException(String.format("Return_code value %s is invalid in XML: %s", return_code, xmlStr));
        }
    }

    /**
     * 作用：提交刷卡支付<br>
     * 场景：刷卡支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> microPay(Map<String, String> reqData) {
        return microPay(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：提交刷卡支付<br>
     * 场景：刷卡支付
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> microPay(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_MICROPAY_URL;
        } else {
            url = WxpayConsts.MICROPAY_URL;
        }
        String respXml = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }


    /**
     * 作用：统一下单<br>
     * 场景：公共号支付、扫码支付、APP支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> unifiedOrder(Map<String, String> reqData) {
        return unifiedOrder(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：统一下单<br>
     * 场景：公共号支付、扫码支付、APP支付
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> unifiedOrder(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_UNIFIEDORDER_URL;
        } else {
            url = WxpayConsts.UNIFIEDORDER_URL;
        }
        String respXml = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }


    /**
     * 作用：查询订单<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> orderQuery(Map<String, String> reqData) {
        return orderQuery(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：查询订单<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付
     *
     * @param reqData          向wxpay post的请求数据 int
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> orderQuery(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_ORDERQUERY_URL;
        } else {
            url = WxpayConsts.ORDERQUERY_URL;
        }
        String respXml = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }


    /**
     * 作用：撤销订单<br>
     * 场景：刷卡支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> reverse(Map<String, String> reqData) {
        return reverse(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：撤销订单<br>
     * 场景：刷卡支付<br>
     * 其他：需要证书
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> reverse(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_REVERSE_URL;
        } else {
            url = WxpayConsts.REVERSE_URL;
        }
        String respXml = requestWithCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }


    /**
     * 作用：关闭订单<br>
     * 场景：公共号支付、扫码支付、APP支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> closeOrder(Map<String, String> reqData) {
        return closeOrder(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：关闭订单<br>
     * 场景：公共号支付、扫码支付、APP支付
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> closeOrder(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_CLOSEORDER_URL;
        } else {
            url = WxpayConsts.CLOSEORDER_URL;
        }
        String respXml = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }


    /**
     * 作用：申请退款<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> refund(Map<String, String> reqData) {
        return refund(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：申请退款<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付<br>
     * 其他：需要证书
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> refund(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_REFUND_URL;
        } else {
            url = WxpayConsts.REFUND_URL;
        }
        String respXml = requestWithCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }


    /**
     * 作用：退款查询<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> refundQuery(Map<String, String> reqData) {
        return refundQuery(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：退款查询<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> refundQuery(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_REFUNDQUERY_URL;
        } else {
            url = WxpayConsts.REFUNDQUERY_URL;
        }
        String respXml = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }


    /**
     * 作用：对账单下载（成功时返回对账单数据，失败时返回XML格式数据）<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> downloadBill(Map<String, String> reqData) {
        return downloadBill(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：对账单下载<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付<br>
     * 其他：无论是否成功都返回Map。若成功，返回的Map中含有return_code、return_msg、data，
     * 其中return_code为`SUCCESS`，data为对账单数据。
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return 经过封装的API返回数据
     */
    public Map<String, String> downloadBill(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_DOWNLOADBILL_URL;
        } else {
            url = WxpayConsts.DOWNLOADBILL_URL;
        }
        String respStr = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs).trim();
        Map<String, String> ret;
        // 出现错误，返回XML数据
        if (respStr.indexOf("<") == 0) {
            ret = WxpayUtil.xmlToMap(respStr);
        } else {
            // 正常返回csv数据
            ret = new HashMap<>();
            ret.put("return_code", WxpayConsts.SUCCESS);
            ret.put("return_msg", "ok");
            ret.put("data", respStr);
        }
        return ret;
    }


    /**
     * 作用：交易保障<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> report(Map<String, String> reqData) {
        return report(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：交易保障<br>
     * 场景：刷卡支付、公共号支付、扫码支付、APP支付
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> report(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_REPORT_URL;
        } else {
            url = WxpayConsts.REPORT_URL;
        }
        String respXml = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return WxpayUtil.xmlToMap(respXml);
    }


    /**
     * 作用：转换短链接<br>
     * 场景：刷卡支付、扫码支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> shortUrl(Map<String, String> reqData) {
        return shortUrl(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：转换短链接<br>
     * 场景：刷卡支付、扫码支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> shortUrl(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_SHORTURL_URL;
        } else {
            url = WxpayConsts.SHORTURL_URL;
        }
        String respXml = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }


    /**
     * 作用：授权码查询OPENID接口<br>
     * 场景：刷卡支付
     *
     * @param reqData 向wxpay post的请求数据
     * @return API返回数据
     */
    public Map<String, String> authCodeToOpenid(Map<String, String> reqData) {
        return authCodeToOpenid(reqData, config.getConnectTimeoutInMills(), config.getReadTimeoutInMills());
    }


    /**
     * 作用：授权码查询OPENID接口<br>
     * 场景：刷卡支付
     *
     * @param reqData          向wxpay post的请求数据
     * @param connectTimeoutMs 连接超时时间，单位是毫秒
     * @param readTimeoutMs    读超时时间，单位是毫秒
     * @return API返回数据
     */
    public Map<String, String> authCodeToOpenid(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs) {
        String url;
        if (config.isUseSandbox()) {
            url = WxpayConsts.SANDBOX_AUTHCODETOOPENID_URL;
        } else {
            url = WxpayConsts.AUTHCODETOOPENID_URL;
        }
        String respXml = requestWithoutCert(url, fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
        return processResponseXml(respXml);
    }
}
