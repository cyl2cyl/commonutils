package com.example.demo.utils;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * @author cyl
 * @description 微信流量主爬取
 * @date 2019/03/27 19:45
 */
@Component
public class WechatGDTSpiderUtils {

    /**
     * 登录链接
     */
    private final static String LOGIN_URL = "https://mp.weixin.qq.com/cgi-bin/bizlogin?action=startlogin";
    /**
     * 登录询问
     */
    private final static String LOGIN_ASK_URL = "https://mp.weixin.qq.com/cgi-bin/loginqrcode?action=ask&token=&lang=zh_CN&f=json&ajax=1";

    /**
     * 业务登录
     */
    private final static String BIZ_LOGIN = "https://mp.weixin.qq.com/cgi-bin/bizlogin?action=login&lang=zh_CN";
    /**
     * 主页
     */
    private final static String HOME_URL = "https://mp.weixin.qq.com";

    private final static String QR_CODE_URL = "https://mp.weixin.qq.com/cgi-bin/loginqrcode?action=getqrcode&param=4300";

    private final static String LOGIN_AUTH_URL = "https://mp.weixin.qq.com/cgi-bin/loginauth?action=ask&token=&lang=zh_CN&f=json&ajax=1";

    private static final List<HttpCookie> COOKIES = new ArrayList<>();

    /**
     * 处理下cookies
     *
     * @param cooks
     */
    private static void handleCookies(List<String> cooks) {
        cooks.stream().map((c) -> HttpCookie.parse(c)).forEachOrdered((cook) -> {
            cook.forEach((a) -> {
                if (!a.getValue().contains("EXPIRED")) {
                    COOKIES.add(a);
                } else {
                    HttpCookie cookieExists = COOKIES.stream().filter(x -> a.getName().equals(x.getName())).findAny().orElse(null);
                    if (cookieExists != null) {
                        COOKIES.remove(cookieExists);
                    }
                }
            });
        });
    }

    /**
     * 获得下token
     *
     * @param query
     * @return
     * @throws RuntimeException
     */
    private static String getToken(String query) throws RuntimeException {
        String[] params = query.split("&");
        for (String param : params) {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            if ("token".equals(name)) {
                return value;
            }
        }
        throw new RuntimeException("获得token 为空");
    }

    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders httpHeaders = getHeader();
        ResponseEntity<String> exchange1 = restTemplate.exchange(HOME_URL, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
        HttpHeaders headers = exchange1.getHeaders();
        List<String> cooks = headers.get(HttpHeaders.SET_COOKIE);
        handleCookies(cooks);
        //构造body
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("username", "账号");
        map.add("pwd", "密码md5之后的值");
        map.add("imgcode", "");
        map.add("f", "json");
        HttpHeaders formHttpHeader = getHeader();
        formHttpHeader.add("Accept-Encoding", "gzip,deflate");
        formHttpHeader.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<Map> requestEntity = new HttpEntity<>(map, formHttpHeader);
        ResponseEntity<Map> exchange = restTemplate.exchange(LOGIN_URL, HttpMethod.POST, requestEntity, Map.class);
        Map response = exchange.getBody();
        if (response != null) {
            if (response.get("redirect_url") != null) {
                List<String> cookies = exchange.getHeaders().get(HttpHeaders.SET_COOKIE);
                handleCookies(cookies);
                HttpHeaders httpHeaders1 = getHeader();
                httpHeaders1.setContentType(MediaType.IMAGE_JPEG);
                //这里获得二维码
                String redirectUrl = QR_CODE_URL;
                HttpEntity<Map> requestEntity1 = new HttpEntity<>(httpHeaders1);
                ResponseEntity<byte[]> forEntity = restTemplate.exchange(redirectUrl, HttpMethod.GET, requestEntity1, byte[].class);
                String s = Base64.getEncoder().encodeToString(forEntity.getBody());
                System.out.println("图片的base64结果为:" + s);
                // 这里进行轮询问
                SpiderThreadFactory.getSimpleExecutorInstance().execute(() -> {
                    HttpHeaders askHeader = getHeader();
                    String url = LOGIN_ASK_URL;
                    while (true) {
                        try {
                            ResponseEntity<Map> askExchange = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(askHeader), Map.class);
                            Map askBody = askExchange.getBody();
                            Integer status = (Integer) askBody.get("status");
                            if (status == 1) {
                                if (askBody.get("user_category") != null && (Integer) askBody.get("user_category") == 1) {
                                    url = LOGIN_AUTH_URL;
                                } else {
                                    System.out.println("登录成功");
                                    break;
                                }
                            } else if (status == 2) {
                                System.out.println("管理员拒绝");
                                break;
                            } else if (status == 3) {
                                System.out.println("登录超时");
                                break;
                            } else if (status == 4) {
                                System.out.println("已经扫码");
                            } else {
                                if (url == LOGIN_ASK_URL) {
                                    System.out.println("等待扫码");
                                } else {
                                    System.out.println("等待确认");
                                }
                            }
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            System.out.println("出错了" + e);
                        }
                    }
                    System.out.println("开始验证");
                    MultiValueMap<String, String> bizBodyMap = new LinkedMultiValueMap<>();
                    bizBodyMap.add("lang", "zh_CN");
                    bizBodyMap.add("ajax", "1");
                    bizBodyMap.add("f", "json");
                    HttpHeaders bizHttpHeader = getHeader();
                    bizHttpHeader.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                    HttpEntity<Map> bizMapHttpEntity = new HttpEntity<>(bizBodyMap, bizHttpHeader);
                    ResponseEntity<Map> biExchange = restTemplate.exchange(BIZ_LOGIN, HttpMethod.POST, bizMapHttpEntity, Map.class);
                    String tokenRedirectUrl = (String) biExchange.getBody().get("redirect_url");
                    String token = getToken(tokenRedirectUrl);
                    System.out.println("token：" + token);
                    List<String> bizCookies = biExchange.getHeaders().get(HttpHeaders.SET_COOKIE);
                    handleCookies(bizCookies);
                    String dataUrl = String.format("https://mp.weixin.qq.com/wxopen/weapp_publisher_stat?action=stat&page=1&page_size=10&start=1554048000&end=1554048000&pos_type=5&token=%s", token);
                    HttpHeaders dataHeader = getHeader();
                    System.out.println("cookies为" + dataHeader.get(HttpHeaders.COOKIE));
                    dataHeader.setContentType(MediaType.APPLICATION_JSON_UTF8);
                    HttpEntity<Map> dataHttpEntity = new HttpEntity<>(dataHeader);
                    ResponseEntity<String> dataMap = restTemplate.exchange(dataUrl, HttpMethod.GET, dataHttpEntity, String.class);
                    System.out.println(dataMap);
                });

            }
        }
    }

    /**
     * 处理下请求头
     *
     * @return
     */
    private static HttpHeaders getHeader() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Referer", "https://mp.weixin.qq.com/");
        httpHeaders.add("Host", "mp.weixin.qq.com");
        httpHeaders.setOrigin("https://mp.weixin.qq.com");
        httpHeaders.add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36");
        if (COOKIES.size() > 0) {
            List<String> cookies = new ArrayList<>();
            for (HttpCookie cookie : COOKIES) {
                String str = cookie.getName() + "=" + cookie.getValue();
                cookies.add(str);
            }
            httpHeaders.put(HttpHeaders.COOKIE, cookies);
        }
        return httpHeaders;
    }
}